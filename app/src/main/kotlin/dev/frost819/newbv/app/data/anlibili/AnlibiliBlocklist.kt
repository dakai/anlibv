package dev.frost819.newbv.app.data.anlibili

import java.util.Locale

/**
 * anlibili 内容过滤表。
 *
 * 目的：站点数据来自知乎/小红书网友推荐，其中混有政治宣传类账号与时政内容，
 * 这里在**拉取数据后、写缓存前**统一剔除，`videos()` 返回的列表不再包含它们。
 *
 * 两类规则：
 * - [AUTHORS]：整号屏蔽（账号名归一化后做包含匹配）。
 * - [KEYWORDS]：标题或标签命中即屏蔽，用于定位宣传话术与特定内容（如「牢A」）。
 *
 * 归一化：转小写并去掉所有空白，因此 `Nathan Rich` / `nathanrich` 都能命中。
 * 新增条目只需往两个列表里加字符串，重启 App 后生效；命中条数会打印在日志里
 * （`Filtered anlibili videos: ...`）。
 *
 * 注意：关键词越短越容易误杀（例如只写「央视」会连带屏蔽纪录片）。当前列表按
 * 「政治宣传源 + 宣传话术」而非「泛政治话题」挑选，宁缺勿滥。
 */
object AnlibiliBlocklist {
    /** 整号屏蔽的政治宣传类账号。 */
    private val AUTHORS =
        listOf(
            "nathanrich", // Nathan Rich 火锅大王
            "火锅大王",
            "观视频工作室",
            "观察者网",
            "陈平眉山论剑",
            "帝都南城人", // 「牢A」系列
        )

    /** 标题/标签命中即屏蔽的关键词（已归一化，这里写原样即可）。 */
    private val KEYWORDS =
        listOf(
            "牢A",
            "火锅大王",
            "观视频",
            "观察者网",
            "张维为",
            "金灿荣",
            "沈逸",
            "司马南",
            "胡锡进",
            "中国共产党",
            "中共",
            "战狼",
            "外交部",
            "环球时报",
            "厉害了我的国",
            "犯我中华",
            "爱国言论",
            "清零",
        )

    private val normalizedAuthors = AUTHORS.map(::normalize)
    private val normalizedKeywords = KEYWORDS.map(::normalize)

    /**
     * 判断一条站点数据是否应被剔除。
     *
     * @param video 站点条目。
     * @return true 表示命中屏蔽表，界面与播放列表都不再展示。
     */
    fun blocks(video: AnlibiliVideo): Boolean {
        val author = normalize(video.up)
        if (normalizedAuthors.any { it.isNotEmpty() && author.contains(it) }) return true

        val text = normalize(video.title)
        if (normalizedKeywords.any { it.isNotEmpty() && text.contains(it) }) return true

        return video.tags.any { tag ->
            val normalizedTag = normalize(tag)
            normalizedKeywords.any { it.isNotEmpty() && normalizedTag.contains(it) }
        }
    }

    private fun normalize(value: String): String = value.lowercase(Locale.ROOT).filterNot { it.isWhitespace() }
}
