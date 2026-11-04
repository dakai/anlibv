package dev.frost819.newbv.app.data.anlibili

import java.util.Locale

/** 标题联想最多返回的条数。 */
const val SUGGESTION_LIMIT = 6

/** 归一化口径与屏蔽表一致：小写 + 去空白（含全角空格）。 */
private fun normalize(value: String): String = value.lowercase(Locale.ROOT).filterNot { it.isWhitespace() }

/**
 * 在已拉取的站点条目里搜索。
 *
 * 匹配标题或 UP 名；排序为「标题前缀 > 标题包含 > UP 名包含」，同档保持站点原有顺序。
 * 空关键词返回空列表（不做"全部"语义，避免调用方误用）。
 *
 * @param query 用户输入的关键词。
 */
fun List<AnlibiliVideo>.searchVideos(query: String): List<AnlibiliVideo> {
    val keyword = normalize(query)
    if (keyword.isEmpty()) return emptyList()
    return mapNotNull { video ->
        val title = normalize(video.title)
        val rank =
            when {
                title.startsWith(keyword) -> 0
                title.contains(keyword) -> 1
                normalize(video.up).contains(keyword) -> 2
                else -> return@mapNotNull null
            }
        rank to video
    }.sortedBy { it.first }
        .map { it.second }
}

/**
 * 标题联想：包含关键词的标题去重后按「命中位置靠前」排序。
 *
 * 前缀命中的标题排在中间命中之前；与关键词完全相同的标题不返回（没有补全价值）。
 *
 * @param query 用户输入的关键词。
 * @param limit 最多返回条数。
 */
fun List<AnlibiliVideo>.suggestTitles(
    query: String,
    limit: Int = SUGGESTION_LIMIT,
): List<String> {
    val keyword = normalize(query)
    if (keyword.isEmpty()) return emptyList()
    return map { it.title }
        .filter { normalize(it) != keyword && normalize(it).contains(keyword) }
        .distinct()
        .sortedBy { normalize(it).indexOf(keyword) }
        .take(limit)
}
