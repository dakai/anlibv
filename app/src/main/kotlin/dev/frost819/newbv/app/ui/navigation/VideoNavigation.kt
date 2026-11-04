package dev.frost819.newbv.app.ui.navigation

import androidx.navigation.NavController
import dev.frost819.newbv.app.ui.component.videocard.VideoCardData
import dev.frost819.newbv.core.log.Loggers

private val logger = Loggers.get("VideoNavigation")

/**
 * 视频卡统一导航入口：直接进入播放器。
 *
 * 卡片只携带 aid/cid/bvid/title/cover；cid 为空时播放器会按历史恢复分 P。
 *
 * @param data 卡片数据（含 aid/cid/bvid/epid/title/cover）
 */
fun NavController.navigateFromVideoCard(data: VideoCardData) {
    logger.info {
        "[CARD] click aid=${data.avid}, cid=${data.cid ?: 0L}, bvid=${data.bvid}, title=${data.title.take(80)}"
    }
    navigate(
        VideoPlayerRoute(
            aid = data.avid,
            cid = data.cid ?: 0L,
            bvid = data.bvid,
            epid = data.epid?.takeIf { it != 0 }?.toLong(),
            title = data.title,
            cover = data.cover,
        ),
    ) {
        popUpTo<VideoPlayerRoute> { inclusive = true }
        launchSingleTop = true
    }
}
