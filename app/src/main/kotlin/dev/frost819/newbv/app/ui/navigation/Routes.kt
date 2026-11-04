package dev.frost819.newbv.app.ui.navigation

import kotlinx.serialization.Serializable

/*
 * Navigation 路由定义。
 *
 * 所有路由使用 `@Serializable` data class，通过 Navigation-Compose 类型安全路由导航。
 * 禁止使用字符串拼接 URL。
 */

// ── 主流程 ────────────────────────────────────────────────────────────

/** 主页面（anlibili 视频列表）。 */
@Serializable
object HomeRoute

// ── 播放器 ────────────────────────────────────────────────────────────

/** 视频播放器页面。 */
@Serializable
data class VideoPlayerRoute(
    val aid: Long,
    val cid: Long,
    val bvid: String = "",
    val epid: Long? = null,
    val title: String = "",
    val cover: String = "",
)

// ── 设置 ──────────────────────────────────────────────────────────────

/** 设置主页。 */
@Serializable
object SettingsRoute

/** 编解码信息页。 */
@Serializable
object MediaCodecRoute

/** 日志查看页。 */
@Serializable
object LogViewerRoute

// ── 登录 ──────────────────────────────────────────────────────────────

/** 登录页面。 */
@Serializable
object LoginRoute
