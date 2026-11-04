package dev.frost819.newbv.app.ui.navigation

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

/**
 * [Routes] 路由定义的单元测试。
 *
 * 验证剩余 `@Serializable` 路由的序列化/反序列化正确性，
 * 确保 Navigation-Compose 类型安全路由可正常工作。
 */
class RoutesTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `HomeRoute serializes and deserializes`() {
        val encoded = json.encodeToString(HomeRoute)
        assertThat(json.decodeFromString<HomeRoute>(encoded)).isEqualTo(HomeRoute)
    }

    @Test
    fun `SettingsRoute serializes and deserializes`() {
        val encoded = json.encodeToString(SettingsRoute)
        assertThat(json.decodeFromString<SettingsRoute>(encoded)).isEqualTo(SettingsRoute)
    }

    @Test
    fun `MediaCodecRoute serializes and deserializes`() {
        val encoded = json.encodeToString(MediaCodecRoute)
        assertThat(json.decodeFromString<MediaCodecRoute>(encoded)).isEqualTo(MediaCodecRoute)
    }

    @Test
    fun `LogViewerRoute serializes and deserializes`() {
        val encoded = json.encodeToString(LogViewerRoute)
        assertThat(json.decodeFromString<LogViewerRoute>(encoded)).isEqualTo(LogViewerRoute)
    }

    @Test
    fun `LoginRoute serializes and deserializes`() {
        val encoded = json.encodeToString(LoginRoute)
        assertThat(json.decodeFromString<LoginRoute>(encoded)).isEqualTo(LoginRoute)
    }

    @Test
    fun `VideoPlayerRoute serializes with required fields`() {
        val route = VideoPlayerRoute(aid = 12345L, cid = 67890L, bvid = "BV1xx411c7mD", title = "t", cover = "c")

        val decoded = json.decodeFromString<VideoPlayerRoute>(json.encodeToString(route))

        assertThat(decoded.aid).isEqualTo(12345L)
        assertThat(decoded.cid).isEqualTo(67890L)
        assertThat(decoded.bvid).isEqualTo("BV1xx411c7mD")
        assertThat(decoded.epid).isNull()
    }
}
