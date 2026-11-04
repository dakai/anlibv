package dev.frost819.newbv.app.ui.screen.anlibili

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import dev.frost819.newbv.app.data.anlibili.AnlibiliChannel
import dev.frost819.newbv.app.data.anlibili.AnlibiliVideo
import dev.frost819.newbv.app.ui.component.FocusSaver
import dev.frost819.newbv.app.ui.component.ListFooterTip
import dev.frost819.newbv.app.ui.component.TopNav
import dev.frost819.newbv.app.ui.component.TopNavItem
import dev.frost819.newbv.app.ui.component.TvLazyVerticalGrid
import dev.frost819.newbv.app.ui.component.focusSaverItem
import dev.frost819.newbv.app.ui.component.videocard.SmallVideoCard
import dev.frost819.newbv.app.ui.component.videocard.VideoCardData
import dev.frost819.newbv.app.ui.navigation.navigateFromVideoCard
import dev.frost819.newbv.app.util.ToastUtils
import dev.frost819.newbv.app.util.formatHourMinSec
import dev.frost819.newbv.app.util.toWanString
import dev.frost819.newbv.app.viewmodel.anlibili.AnlibiliEffect
import dev.frost819.newbv.app.viewmodel.anlibili.AnlibiliViewModel
import dev.frost819.newbv.biliapi.http.util.toSmartDate
import androidx.compose.material3.Scaffold as Material3Scaffold

/**
 * anlibili 频道 Tab，显示名即站点频道名。
 *
 * @property channel 对应站点频道。
 */
private data class AnlibiliTabItem(
    val channel: AnlibiliChannel,
) : TopNavItem {
    override val displayName: String = channel.label
}

/** 顶部频道的固定顺序。 */
private val TAB_ITEMS: List<AnlibiliTabItem> = AnlibiliChannel.entries.map(::AnlibiliTabItem)

/**
 * anlibili 内容页。
 *
 * 视频列表与分类来自 anlibili 站点（一次性整表拉取，见
 * [dev.frost819.newbv.app.data.anlibili.AnlibiliRepository]），
 * 播放仍走新 BV 播放器：点击卡片先解析 aid，再按卡片导航规则进播放器。
 *
 * @param navFocusRequester 顶部 Tab 的焦点请求器（由 MainScreen 传入）。
 * @param navController 导航控制器。
 * @param focusSaver 焦点恢复器（由 MainScreen 共享传入）。
 * @param viewModel anlibili 页 ViewModel。
 */
@Composable
fun AnlibiliContent(
    navFocusRequester: FocusRequester,
    navController: NavController,
    focusSaver: FocusSaver,
    viewModel: AnlibiliViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var selectedTab by rememberSaveable { mutableStateOf(AnlibiliChannel.Recommend) }
    var focusOnContent by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadIfNeeded() }

    // 首次进入（没有保存过焦点）时把焦点放到第一个视频卡片，避免 TV 上"无焦点"状态
    val firstItemKey = state.items.firstOrNull()?.let { "anlibili_${it.bv}" }
    LaunchedEffect(firstItemKey) {
        val key = firstItemKey ?: return@LaunchedEffect
        if (focusSaver.savedKeyValue().isNotEmpty()) return@LaunchedEffect
        kotlinx.coroutines.delay(50)
        runCatching { focusSaver.focusRequesterFor(key).requestFocus() }
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is AnlibiliEffect.Play ->
                    navController.navigateFromVideoCard(
                        VideoCardData(
                            avid = effect.aid,
                            bvid = effect.bvid,
                            title = effect.title,
                            cover = effect.cover,
                            upName = effect.upName,
                        ),
                    )
                is AnlibiliEffect.ShowToast -> ToastUtils.show(context, effect.message)
            }
        }
    }

    Material3Scaffold(
        topBar = {
            TopNav(
                modifier = Modifier.focusRequester(navFocusRequester),
                items = TAB_ITEMS,
                selectedIndex = TAB_ITEMS.indexOfFirst { it.channel == selectedTab },
                isLargePadding = !focusOnContent,
                onSelectedChanged = { item ->
                    val channel = (item as AnlibiliTabItem).channel
                    if (channel != selectedTab) {
                        selectedTab = channel
                        viewModel.selectChannel(channel)
                    }
                },
                onClick = { viewModel.reload() },
            )
        },
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .padding(innerPadding)
                    .onFocusChanged { focusOnContent = it.hasFocus }
                    .onPreviewKeyEvent { event ->
                        if (event.key == Key.Menu && event.type == KeyEventType.KeyUp) {
                            viewModel.reload()
                            navFocusRequester.requestFocus()
                            return@onPreviewKeyEvent true
                        }
                        false
                    },
        ) {
            AnlibiliGrid(
                items = state.items,
                loading = state.loading,
                error = state.error,
                focusSaver = focusSaver,
                onPlay = viewModel::play,
            )
        }
    }
}

/**
 * anlibili 视频网格（4 列，整表一次展示，站点无分页）。
 *
 * @param items 当前频道的视频。
 * @param loading 是否正在加载。
 * @param error 是否加载失败。
 * @param focusSaver 焦点恢复器。
 * @param onPlay 点击卡片回调。
 */
@Composable
private fun AnlibiliGrid(
    items: List<AnlibiliVideo>,
    loading: Boolean,
    error: Boolean,
    focusSaver: FocusSaver,
    onPlay: (AnlibiliVideo) -> Unit,
) {
    val gridState = rememberLazyGridState()

    TvLazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(4),
        contentPadding = PaddingValues(24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(
            items = items,
            key = { it.bv },
        ) { video ->
            // aid 未知：站点只给 BV 号，点击时才解析，因此卡片上不提供「稍后再看」
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
                modifier = Modifier.focusSaverItem(focusSaver, "anlibili_${video.bv}"),
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
