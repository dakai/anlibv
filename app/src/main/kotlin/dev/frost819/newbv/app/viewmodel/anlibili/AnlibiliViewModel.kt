package dev.frost819.newbv.app.viewmodel.anlibili

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.frost819.newbv.app.data.anlibili.AnlibiliChannel
import dev.frost819.newbv.app.data.anlibili.AnlibiliRepository
import dev.frost819.newbv.app.data.anlibili.AnlibiliVideo
import dev.frost819.newbv.app.data.anlibili.inChannel
import dev.frost819.newbv.biliapi.http.BiliHttpApi
import dev.frost819.newbv.core.log.Loggers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * anlibili 页状态。
 *
 * @property channel 当前频道。
 * @property items 当前频道的视频（整表过滤后，无分页）。
 * @property loading 是否正在拉取数据。
 * @property error 最近一次拉取是否失败。
 * @property playPending 是否正在解析点击视频的 aid（防重复点击）。
 */
data class AnlibiliUiState(
    val channel: AnlibiliChannel = AnlibiliChannel.Recommend,
    val items: List<AnlibiliVideo> = emptyList(),
    val loading: Boolean = false,
    val error: Boolean = false,
    val playPending: Boolean = false,
)

/** anlibili 页一次性事件。 */
sealed interface AnlibiliEffect {
    /**
     * 已解析出可播放的视频。
     *
     * @property aid 视频 AV 号。
     * @property bvid 视频 BV 号。
     * @property title 视频标题。
     * @property cover 封面地址。
     * @property upName UP 主名称。
     */
    data class Play(
        val aid: Long,
        val bvid: String,
        val title: String,
        val cover: String,
        val upName: String,
    ) : AnlibiliEffect

    /** 提示文案。 */
    data class ShowToast(
        val message: String,
    ) : AnlibiliEffect
}

/**
 * anlibili 页 ViewModel。
 *
 * 站点数据整表一次拉取，频道切换只在内存里过滤，不产生新请求。
 * 卡片只有 BV 号，点击时才用 `/x/web-interface/view` 解析 aid 后交给播放器。
 *
 * @param anlibiliRepository 站点数据仓库。
 * @param biliHttpApi B 站 Web API（用于 BV → aid 解析）。
 */
@HiltViewModel
class AnlibiliViewModel
    @Inject
    constructor(
        private val anlibiliRepository: AnlibiliRepository,
        private val biliHttpApi: BiliHttpApi,
    ) : ViewModel() {
        private val logger = Loggers.get("AnlibiliViewModel")

        private val _uiState = MutableStateFlow(AnlibiliUiState())
        val uiState = _uiState.asStateFlow()

        private val _effect = MutableSharedFlow<AnlibiliEffect>()
        val effect = _effect.asSharedFlow()

        private var allVideos: List<AnlibiliVideo> = emptyList()

        /**
         * 首次进入页面时加载。
         *
         * 当天已拉取过则直接用本地缓存（[AnlibiliRepository] 内部判断），不产生网络请求。
         */
        fun loadIfNeeded() {
            if (allVideos.isNotEmpty()) return
            load(force = false)
        }

        /**
         * 强制重新拉取站点数据（菜单键「刷新」）。
         */
        fun reload() {
            load(force = true)
        }

        private fun load(force: Boolean) {
            if (_uiState.value.loading) return
            viewModelScope.launch {
                _uiState.update { it.copy(loading = true, error = false) }
                runCatching { anlibiliRepository.videos(force = force) }
                    .onSuccess { videos ->
                        allVideos = videos
                        _uiState.update { it.copy(loading = false, items = videos.inChannel(it.channel)) }
                    }.onFailure { error ->
                        if (error is CancellationException) throw error
                        logger.error(error) { "Failed to load anlibili videos" }
                        _uiState.update { it.copy(loading = false, error = true) }
                    }
            }
        }

        /**
         * 切换频道。
         *
         * @param channel 目标频道。
         */
        fun selectChannel(channel: AnlibiliChannel) {
            _uiState.update { it.copy(channel = channel, items = allVideos.inChannel(channel)) }
        }

        /**
         * 解析并播放卡片对应的视频。
         *
         * aid 只能由 B 站接口给出，cid 传 0 让播放器按历史恢复分 P。
         *
         * @param video 被点击的站点条目。
         */
        fun play(video: AnlibiliVideo) {
            if (_uiState.value.playPending) return
            viewModelScope.launch {
                _uiState.update { it.copy(playPending = true) }
                runCatching { biliHttpApi.getVideoInfo(bv = video.bv).getResponseData() }
                    .onSuccess { info ->
                        _effect.emit(
                            AnlibiliEffect.Play(
                                aid = info.aid,
                                bvid = info.bvid.ifEmpty { video.bv },
                                title = info.title.ifEmpty { video.title },
                                cover = info.pic.ifEmpty { video.pic },
                                upName = info.owner.name.ifEmpty { video.up },
                            ),
                        )
                    }.onFailure { error ->
                        if (error is CancellationException) throw error
                        logger.error(error) { "Failed to resolve ${video.bv}" }
                        _effect.emit(AnlibiliEffect.ShowToast("打开失败: ${error.message ?: "未知错误"}"))
                    }
                _uiState.update { it.copy(playPending = false) }
            }
        }
    }
