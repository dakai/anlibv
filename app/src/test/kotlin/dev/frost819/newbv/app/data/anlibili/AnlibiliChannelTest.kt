package dev.frost819.newbv.app.data.anlibili

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * anlibili 分类规则测试。
 *
 * 规则必须与站点 app.js 的 prepare() / listFor() / recommendFeed() 一致，
 * 否则页面分类与站点对不上。
 */
class AnlibiliChannelTest {
    private fun video(
        bv: String,
        zone: String = "知识",
        tags: List<String> = emptyList(),
        fresh: Boolean = false,
    ) = AnlibiliVideo(bv = bv, title = bv, zone = zone, tags = tags, fresh = fresh)

    @Test
    fun `visible keeps whitelisted zones and tags only`() {
        val list =
            listOf(
                video("a", zone = "知识"),
                video("b", zone = "娱乐"),
                video("c", zone = "美食", tags = listOf("AI")),
                video("d", zone = "时尚", tags = listOf("穿搭")),
            )

        assertThat(list.filter(AnlibiliVideo::isVisible).map { it.bv }).containsExactly("a", "c").inOrder()
    }

    @Test
    fun `zone channels match by zone name`() {
        val list = listOf(video("a", zone = "游戏"), video("b", zone = "知识"))

        assertThat(list.inChannel(AnlibiliChannel.Game).map { it.bv }).containsExactly("a")
    }

    @Test
    fun `tag channels match by tag`() {
        val list = listOf(video("a", tags = listOf("AI")), video("b", tags = listOf("编程", "AI")), video("c"))

        assertThat(list.inChannel(AnlibiliChannel.AI).map { it.bv }).containsExactly("a", "b").inOrder()
        assertThat(list.inChannel(AnlibiliChannel.Programming).map { it.bv }).containsExactly("b")
    }

    @Test
    fun `fresh channel keeps only fresh entries`() {
        val list = listOf(video("a"), video("b", fresh = true))

        assertThat(list.inChannel(AnlibiliChannel.Fresh).map { it.bv }).containsExactly("b")
    }

    @Test
    fun `recommend interleaves one fresh entry every six community items`() {
        val community = (1..13).map { video("c$it") }
        val fresh = (1..2).map { video("f$it", fresh = true) }
        // 站点实现：社区条目按原序，第 6、12 个之后各插入一条新近好评
        val expected =
            listOf("c1", "c2", "c3", "c4", "c5", "f1", "c6", "c7", "c8", "c9", "c10", "f2", "c11", "c12", "c13")

        assertThat(community.inChannel(AnlibiliChannel.Recommend)).isEqualTo(community)
        assertThat((community + fresh).inChannel(AnlibiliChannel.Recommend).map { it.bv }).isEqualTo(expected)
    }

    @Test
    fun `recommend appends leftover fresh entries at the end`() {
        val list = listOf(video("c1"), video("f1", fresh = true), video("f2", fresh = true))

        assertThat(list.inChannel(AnlibiliChannel.Recommend).map { it.bv }).isEqualTo(listOf("c1", "f1", "f2"))
    }
}
