package dev.frost819.newbv.app.data.anlibili

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * [AnlibiliBlocklist] 的单元测试。
 *
 * 屏蔽表是内容策略，改动会影响整个列表，因此这里锁定三类行为：
 * 账号命中、标题/标签关键词命中、无关内容不误伤。
 */
class AnlibiliBlocklistTest {
    private fun video(
        up: String = "普通UP主",
        title: String = "Rust 异步运行时解析",
        tags: List<String> = emptyList(),
    ) = AnlibiliVideo(bv = "BV1", title = title, up = up, tags = tags)

    @Test
    fun `blocks banned author regardless of case and spaces`() {
        assertThat(AnlibiliBlocklist.blocks(video(up = "NathanRich火锅大王"))).isTrue()
        assertThat(AnlibiliBlocklist.blocks(video(up = "nathan rich 火锅大王"))).isTrue()
        assertThat(AnlibiliBlocklist.blocks(video(up = "观视频工作室"))).isTrue()
        assertThat(AnlibiliBlocklist.blocks(video(up = "观察者网"))).isTrue()
    }

    @Test
    fun `blocks propaganda keywords in title`() {
        assertThat(AnlibiliBlocklist.blocks(video(title = "【牢A】美国混血后代排行"))).isTrue()
        assertThat(AnlibiliBlocklist.blocks(video(title = "厉害了我的国 主题曲"))).isTrue()
        assertThat(AnlibiliBlocklist.blocks(video(title = "外交部最新回应"))).isTrue()
    }

    @Test
    fun `blocks keywords carried by tags`() {
        assertThat(AnlibiliBlocklist.blocks(video(tags = listOf("战狼")))).isTrue()
    }

    @Test
    fun `keeps ordinary technology and culture videos`() {
        assertThat(AnlibiliBlocklist.blocks(video())).isFalse()
        assertThat(AnlibiliBlocklist.blocks(video(title = "央视纪录片《丝绸之路》全14集", up = "落雪幻梦"))).isFalse()
        assertThat(AnlibiliBlocklist.blocks(video(title = "香港 2019 VLOG 时间胶囊", up = "SEVEN七叔摄影数码"))).isFalse()
    }
}
