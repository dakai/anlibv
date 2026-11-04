package dev.frost819.newbv.app.viewmodel.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.frost819.newbv.app.data.anlibili.AnlibiliRepository
import dev.frost819.newbv.app.data.anlibili.AnlibiliVideo
import dev.frost819.newbv.app.data.anlibili.searchVideos
import dev.frost819.newbv.app.data.anlibili.suggestTitles
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
 * 搜索页状态。
 *
 * @property query 输入框内容。
 * @property suggestions 标题联想（最多 [dev.frost819.newbv.app.data.anlibili.SUGGESTION_LIMIT] 条）。
 * @property results 命中标题或 UP 名的视频。
 * @property submitted 是否已提交搜索（提交后隐藏联想、展示结果网格）。
 * @property loading 是否正在拉取站点数据。
 * @property error 站点数据是否加载失败。
 * @property playPending 是否正在解析点击视频的 aid（防重复点击）。
 */
data class SearchUiState(
    val query: String = "",
    val suggestions: List<String> = emptyList(),
    val results: List<AnlibiliVideo> = emptyList(),
    val submitted: Boolean = false,
    val loading: Boolean = false,
    val error: Boolean = false,
    val playPending: Boolean = false,
)

/** 搜索页一次性事件。 */
sealed interface SearchUiEffect {
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
    ) : SearchUiEffect

    /** 提示文案。 */
    data class ShowToast(
        val message: String,
    ) : SearchUiEffect
}

/**
 * 搜索页 ViewModel。
 *
 * 搜索完全在内存里做：站点数据整表拉取（当天命中缓存则不产生请求），
 * 输入变化时实时计算联想与结果，不发送搜索请求。
 *
 * @param anlibiliRepository 站点数据仓库。
 * @param biliHttpApi B 站 Web API（用于 BV → aid 解析）。
 */
@HiltViewModel
class SearchViewModel
    @Inject
    constructor(
        private val anlibiliRepository: AnlibiliRepository,
        private val biliHttpApi: BiliHttpApi,
    ) : ViewModel() {
        private val logger = Loggers.get("SearchViewModel")

        private val _uiState = MutableStateFlow(SearchUiState())
        val uiState = _uiState.asStateFlow()

        private val _effect = MutableSharedFlow<SearchUiEffect>()
        val effect = _effect.asSharedFlow()

        private var allVideos: List<AnlibiliVideo> = emptyList()

        /** 首次进入搜索页时加载站点数据（命中当天缓存则只读磁盘）。 */
        fun loadIfNeeded() {
            if (allVideos.isNotEmpty() || _uiState.value.loading) return
            viewModelScope.launch {
                _uiState.update { it.copy(loading = true, error = false) }
                runCatching { anlibiliRepository.videos() }
                    .onSuccess { videos ->
                        allVideos = videos
                        // 数据可能晚于输入到达，补齐当前关键词的联想与结果
                        _uiState.update { it.withQuery(it.query, submitted = it.submitted) }
                    }.onFailure { error ->
                        if (error is CancellationException) throw error
                        logger.error(error) { "Failed to load anlibili videos for search" }
                        _uiState.update { it.copy(error = true) }
                    }
                _uiState.update { it.copy(loading = false) }
            }
        }

        /**
         * 输入变化：实时计算联想与结果，并退出已提交状态。
         *
         * @param query 输入框内容。
         */
        fun onQueryChange(query: String) {
            _uiState.update { it.withQuery(query, submitted = false) }
        }

        /**
         * 提交搜索（IME 搜索键 / 选中联想项）：隐藏联想，展示结果网格。
         *
         * @param query 可选的完整关键词（点击联想项时使用）。
         */
        fun onSubmit(query: String = _uiState.value.query) {
            _uiState.update { it.withQuery(query, submitted = true) }
        }

        /** 清空关键词。 */
        fun clearQuery() = onQueryChange("")

        /**
         * 解析并播放命中的视频。
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
                            SearchUiEffect.Play(
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
                        _effect.emit(SearchUiEffect.ShowToast("打开失败: ${error.message ?: "未知错误"}"))
                    }
                _uiState.update { it.copy(playPending = false) }
            }
        }

        private fun SearchUiState.withQuery(
            query: String,
            submitted: Boolean,
        ): SearchUiState {
            val results = allVideos.searchVideos(query)
            return copy(
                query = query,
                suggestions = allVideos.suggestTitles(query),
                results = results,
                submitted = submitted && results.isNotEmpty(),
            )
        }
    }
