package dev.frost819.newbv.app.data.anlibili

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * [AnlibiliSearch] 的单元测试。
 *
 * 搜索与联想决定用户能看到什么，因此锁定三类行为：
 * 排序（标题前缀 > 标题包含 > UP 名包含）、归一化（大小写/空白）、联想去重与上限。
 */
class AnlibiliSearchTest {
    private fun video(
        bv: String,
        title: String,
        up: String = "某UP",
    ) = AnlibiliVideo(bv = bv, title = title, up = up)

    private val videos =
        listOf(
            video("BV1", "Python 入门教程", "黑马程序员"),
            video("BV2", "零基础学 Python 爬虫", "张三"),
            video("BV3", "Rust 语言入门", "Rust 官方"),
            video("BV4", "漫谈 Python 性能", "python侠"),
        )

    @Test
    fun `empty query returns nothing`() {
        assertThat(videos.searchVideos("")).isEmpty()
        assertThat(videos.searchVideos("   ")).isEmpty()
        assertThat(videos.suggestTitles("")).isEmpty()
    }

    @Test
    fun `title prefix ranks before title contains`() {
        val result = videos.searchVideos("python")

        assertThat(result.map { it.bv }).containsExactly("BV1", "BV2", "BV4").inOrder()
    }

    @Test
    fun `up name match is included after title matches`() {
        val result = videos.searchVideos("黑马")

        assertThat(result.map { it.bv }).containsExactly("BV1")
    }

    @Test
    fun `matching ignores case and whitespace`() {
        assertThat(videos.searchVideos("PY THON").map { it.bv })
            .containsExactly("BV1", "BV2", "BV4")
    }

    @Test
    fun `suggestions are deduplicated and capped`() {
        val many = (1..10).map { video("BV$it", "同一个标题 $it") } + video("BV11", "同一个标题 1")

        val suggestions = many.suggestTitles("同一个标题")

        assertThat(suggestions).hasSize(SUGGESTION_LIMIT)
        assertThat(suggestions.toSet()).hasSize(SUGGESTION_LIMIT)
    }

    @Test
    fun `suggestions put earlier highlight first and skip exact query`() {
        val list =
            listOf(
                video("BV1", "零基础学 Python"),
                video("BV2", "Python 入门"),
                video("BV3", "Python"),
            )

        val suggestions = list.suggestTitles("python")

        assertThat(suggestions).containsExactly("Python 入门", "零基础学 Python").inOrder()
    }
}
