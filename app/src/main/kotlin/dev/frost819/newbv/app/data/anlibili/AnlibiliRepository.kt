package dev.frost819.newbv.app.data.anlibili

import dev.frost819.newbv.core.log.Loggers
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * anlibili 数据源（带「每天一次」本地缓存）。
 *
 * 站点把全部数据放在根路径的静态 `videos.json`（当前约 6.8 MB、1900 余条），
 * 无分页、无鉴权、无游标，因此一次性拉取整表，再在内存里做分类过滤。
 * 过滤与排序规则与站点 `app.js` 的 `prepare()` / `listFor()` 一致。
 *
 * 缓存策略：拉取成功后把**过滤后的列表**（展示范围 + 内容屏蔽表）写入 `cacheDir`，并按文件修改日期
 * 判断「今天是否已拉取」。App 启动时预热一次，当天内的页面切换与冷启动
 * 都只读本地文件，不产生网络请求；跨天后自动重新拉取。
 *
 * @param cacheDir 本地缓存目录（`context.cacheDir`）。
 * @param httpClient 共享 Ktor 客户端（超时已配置）。
 */
class AnlibiliRepository(
    private val cacheDir: File,
    private val httpClient: HttpClient,
) {
    private val logger = Loggers.get("AnlibiliRepository")

    private var cache: List<AnlibiliVideo>? = null

    /**
     * 获取全部可见视频。
     *
     * @param force true 时忽略当天缓存，强制重新拉取。
     * @return 已按站点规则过滤（7 个分区 + AI/编程 标签）的视频列表。
     */
    suspend fun videos(force: Boolean = false): List<AnlibiliVideo> {
        if (!force) cache?.let { return it }

        return withContext(Dispatchers.IO) {
            val file = File(cacheDir, CACHE_FILE)
            if (!force && file.isFresh()) {
                runCatching { json.decodeFromString(serializer, file.readText()) }
                    .onSuccess { cached ->
                        logger.info { "Loaded ${cached.size} anlibili videos from cache" }
                        cache = cached
                        return@withContext cached
                    }.onFailure { error ->
                        logger.error(error) { "Cached anlibili data unreadable, refetching" }
                        file.delete()
                    }
            }

            val body = httpClient.get(VIDEOS_URL).bodyAsText()
            val parsed = json.decodeFromString<List<AnlibiliVideo>>(body)
            val visible = parsed.filter(AnlibiliVideo::isVisible)
            val all = visible.filterNot(AnlibiliBlocklist::blocks)
            logger.info {
                "Filtered anlibili videos: ${visible.size} visible, ${visible.size - all.size} blocked"
            }
            runCatching { file.writeText(json.encodeToString(serializer, all)) }
                .onFailure { logger.error(it) { "Failed to cache anlibili data" } }
            cache = all
            all
        }
    }

    /** 文件是否是今天写入的。 */
    private fun File.isFresh(): Boolean {
        if (!exists()) return false
        val writtenOn =
            Instant
                .ofEpochMilli(lastModified())
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
        return writtenOn == LocalDate.now()
    }

    private companion object {
        const val VIDEOS_URL = "https://anlibili-site-bxlxh4ai.teamily.run/videos.json"

        /** 缓存文件名带版本号：改动过滤/屏蔽规则后 +1，避免读到按旧规则生成的缓存。 */
        const val CACHE_FILE = "anlibili-videos-v2.json"

        val json = Json { ignoreUnknownKeys = true }
        val serializer = ListSerializer(AnlibiliVideo.serializer())
    }
}

/** 站点只展示这 7 个分区加 2 个标签分类，其余条目在源头丢弃。 */
private val VISIBLE_ZONES = setOf("知识", "科技", "影视", "运动", "生活", "游戏", "动画")
private val VISIBLE_TAGS = setOf("AI", "编程")

/**
 * 条目是否属于站点展示范围。
 *
 * @return 分区命中 [VISIBLE_ZONES] 或标签命中 [VISIBLE_TAGS]。
 */
fun AnlibiliVideo.isVisible(): Boolean = zone in VISIBLE_ZONES || tags.any { it in VISIBLE_TAGS }

/**
 * 分类频道，顺序与站点导航一致。
 *
 * @property label 频道名，同时是站点 JSON 里的分区名或标签名。
 */
enum class AnlibiliChannel(
    val label: String,
) {
    Recommend("推荐"),
    Fresh("新近好评"),
    AI("AI"),
    Knowledge("知识"),
    Programming("编程"),
    Tech("科技"),
    Cinephile("影视"),
    Sports("运动"),
    Life("生活"),
    Game("游戏"),
    Anime("动画"),
}

/**
 * 按频道筛选，规则对应站点 `listFor()`。
 *
 * @param channel 目标频道。
 * @return 该频道的视频列表。
 */
fun List<AnlibiliVideo>.inChannel(channel: AnlibiliChannel): List<AnlibiliVideo> =
    when (channel) {
        AnlibiliChannel.Recommend -> recommendOrder()
        AnlibiliChannel.Fresh -> filter { it.fresh }
        AnlibiliChannel.AI -> filter { "AI" in it.tags }
        AnlibiliChannel.Programming -> filter { "编程" in it.tags }
        else -> filter { it.zone == channel.label }
    }

/**
 * 推荐流顺序：社区推荐保持 JSON 原序（站点已按推荐分排好），
 * 每 6 条混入 1 条「新近好评」，与站点 `recommendFeed()` 相同。
 */
private fun List<AnlibiliVideo>.recommendOrder(): List<AnlibiliVideo> {
    val fresh = filter { it.fresh }
    val out = ArrayList<AnlibiliVideo>(size)
    var nextFresh = 0
    forEach { video ->
        if (video.fresh) return@forEach
        out += video
        if (out.size % 6 == 5 && nextFresh < fresh.size) out += fresh[nextFresh++]
    }
    while (nextFresh < fresh.size) out += fresh[nextFresh++]
    return out
}
