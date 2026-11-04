package dev.frost819.newbv.app.data.anlibili

import kotlinx.serialization.Serializable

/**
 * anlibili 站点 `videos.json` 的单条视频。
 *
 * 只声明本站用到的字段，站点新增字段由 [AnlibiliRepository] 的
 * `ignoreUnknownKeys` 忽略，因此不必跟随站点 schema 变更。
 *
 * @property bv 视频 BV 号（播放与详情解析的唯一键）。
 * @property title 视频标题。
 * @property up UP 主名称。
 * @property mid UP 主 MID，0 表示站点未提供。
 * @property pic 封面地址。
 * @property duration 时长（秒）。
 * @property pub 发布时间（秒级时间戳）。
 * @property view 播放数。
 * @property danmaku 弹幕数。
 * @property zone B 站分区名。
 * @property tags 标签（AI / 编程 等）。
 * @property people 推荐人数，0 表示「新近好评」条目。
 * @property sources 推荐来源平台（知乎 / 小红书）。
 * @property fresh 是否属于「新近好评」，true 时无社区推荐。
 */
@Serializable
data class AnlibiliVideo(
    val bv: String,
    val title: String,
    val up: String = "",
    val mid: Long = 0,
    val pic: String = "",
    val duration: Int = 0,
    val pub: Long = 0,
    val view: Int = 0,
    val danmaku: Int = 0,
    val zone: String = "",
    val tags: List<String> = emptyList(),
    val people: Int = 0,
    val sources: List<String> = emptyList(),
    val fresh: Boolean = false,
)
