package dev.frost819.newbv.app.ui.screen.anlibili

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import dev.frost819.newbv.app.data.anlibili.AnlibiliVideo
import dev.frost819.newbv.app.ui.component.FocusSaver
import dev.frost819.newbv.app.ui.component.ListFooterTip
import dev.frost819.newbv.app.ui.component.TvLazyVerticalGrid
import dev.frost819.newbv.app.ui.component.focusSaverItem
import dev.frost819.newbv.app.ui.component.videocard.SmallVideoCard
import dev.frost819.newbv.app.ui.component.videocard.VideoCardData
import dev.frost819.newbv.app.util.formatHourMinSec
import dev.frost819.newbv.app.util.toWanString
import dev.frost819.newbv.biliapi.http.util.toSmartDate

/** anlibili 页的焦点 key 前缀。 */
const val ANLIBILI_FOCUS_PREFIX = "anlibili"

/** 搜索页的焦点 key 前缀，与 anlibili 页区分，避免焦点恢复时串页。 */
const val SEARCH_FOCUS_PREFIX = "search"

/** 站点条目的焦点 key，Anlibili 页与搜索页共用。 */
fun anlibiliFocusKey(
    prefix: String,
    video: AnlibiliVideo,
): String = "${prefix}_${video.bv}"

/**
 * 站点条目网格（4 列，整表一次展示，站点无分页）。
 *
 * anlibili 页与搜索页共用；aid 未知（站点只给 BV 号），点击时才由 ViewModel 解析。
 *
 * @param items 要展示的视频。
 * @param loading 是否正在拉取站点数据。
 * @param error 拉取是否失败。
 * @param focusSaver 焦点恢复器。
 * @param keyPrefix 焦点 key 前缀，见 [ANLIBILI_FOCUS_PREFIX] / [SEARCH_FOCUS_PREFIX]。
 * @param onPlay 点击卡片回调。
 * @param firstItemFocusRequester 可选：绑定到第一张卡片，供页面把焦点从输入框移进网格。
 */
@Composable
fun AnlibiliVideoGrid(
    items: List<AnlibiliVideo>,
    loading: Boolean,
    error: Boolean,
    focusSaver: FocusSaver,
    keyPrefix: String,
    onPlay: (AnlibiliVideo) -> Unit,
    modifier: Modifier = Modifier,
    firstItemFocusRequester: FocusRequester? = null,
) {
    val firstBv = items.firstOrNull()?.bv
    TvLazyVerticalGrid(
        modifier = modifier,
        state = rememberLazyGridState(),
        columns = GridCells.Fixed(4),
        contentPadding = PaddingValues(24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(
            items = items,
            key = { it.bv },
        ) { video ->
            val cardData =
                remember(video) {
                    VideoCardData(
                        avid = 0L,
                        bvid = video.bv,
                        title = video.title,
                        cover = video.pic,
                        playString = video.view.toWanString(),
                        danmakuString = video.danmaku.toWanString(),
                        timeString = video.duration.formatHourMinSec(),
                        upName = video.up,
                        upMid = video.mid.takeIf { it > 0 },
                        pubTime = video.pub.toSmartDate(),
                    )
                }
            SmallVideoCard(
                modifier =
                    Modifier
                        .focusSaverItem(focusSaver, anlibiliFocusKey(keyPrefix, video))
                        .then(
                            if (firstItemFocusRequester != null && video.bv == firstBv) {
                                Modifier.focusRequester(firstItemFocusRequester)
                            } else {
                                Modifier
                            },
                        ),
                data = cardData,
                onClick = { onPlay(video) },
            )
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            ListFooterTip(
                isLoading = loading,
                isError = error,
                hasMore = false,
                itemsIsEmpty = items.isEmpty(),
            )
        }
    }
}
