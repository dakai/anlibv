package dev.frost819.newbv.app.ui.screen.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import dev.frost819.newbv.app.ui.component.FocusSaver
import dev.frost819.newbv.app.ui.component.focusSaverItem
import dev.frost819.newbv.app.ui.component.videocard.VideoCardData
import dev.frost819.newbv.app.ui.navigation.navigateFromVideoCard
import dev.frost819.newbv.app.ui.screen.anlibili.AnlibiliVideoGrid
import dev.frost819.newbv.app.ui.screen.anlibili.SEARCH_FOCUS_PREFIX
import dev.frost819.newbv.app.util.ToastUtils
import dev.frost819.newbv.app.viewmodel.search.SearchUiEffect
import dev.frost819.newbv.app.viewmodel.search.SearchViewModel
import androidx.compose.material3.Icon as Material3Icon

/**
 * 搜索页：输入关键词，按标题/UP 名在已拉取的站点条目里检索。
 *
 * 联想只做标题补全，选中后立即提交；按 IME 搜索键或遥控器「确定」也提交。
 * 搜索在内存里完成，不发送搜索请求（见 [SearchViewModel]）。
 *
 * @param navController 导航控制器（进播放器）。
 * @param focusSaver 焦点恢复器（由 MainScreen 共享传入）。
 * @param viewModel 搜索页 ViewModel。
 */
@Composable
fun SearchContent(
    modifier: Modifier = Modifier,
    navController: NavController,
    focusSaver: FocusSaver,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var fieldFocused by remember { mutableStateOf(false) }
    // 输入框会吞掉方向键，下移焦点必须显式请求（联想首项或结果首项共用这一个请求器）
    val downRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { viewModel.loadIfNeeded() }

    // 提交后联想项消失，若不显式移交焦点会回落到左侧栏头像（点一下就进登录页）
    LaunchedEffect(state.submitted) {
        if (state.submitted) runCatching { downRequester.requestFocus() }
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is SearchUiEffect.Play ->
                    navController.navigateFromVideoCard(
                        VideoCardData(
                            avid = effect.aid,
                            bvid = effect.bvid,
                            title = effect.title,
                            cover = effect.cover,
                            upName = effect.upName,
                        ),
                    )
                is SearchUiEffect.ShowToast -> ToastUtils.show(context, effect.message)
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        SearchField(
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 12.dp),
            query = state.query,
            focused = fieldFocused,
            onFocusChanged = { focused ->
                fieldFocused = focused
                if (focused) keyboardController?.show()
            },
            onQueryChange = viewModel::onQueryChange,
            onMoveDown = { runCatching { downRequester.requestFocus() }.isSuccess },
            onSubmit = {
                viewModel.onSubmit()
                keyboardController?.hide()
            },
        )

        val showSuggestions = !state.submitted && state.suggestions.isNotEmpty()
        if (showSuggestions) {
            SuggestionList(
                suggestions = state.suggestions,
                focusSaver = focusSaver,
                firstItemFocusRequester = downRequester,
                onSelect = { suggestion ->
                    viewModel.onSubmit(suggestion)
                    keyboardController?.hide()
                },
            )
        } else if (state.query.isBlank()) {
            SearchHint("输入关键词，搜索站点的视频标题或 UP 主")
        } else {
            val hasResults = state.results.isNotEmpty()
            if (state.submitted && !hasResults && !state.loading && !state.error) {
                SearchHint("没有找到「${state.query}」")
            } else {
                AnlibiliVideoGrid(
                    modifier = Modifier.weight(1f),
                    items = state.results,
                    loading = state.loading,
                    error = state.error,
                    focusSaver = focusSaver,
                    keyPrefix = SEARCH_FOCUS_PREFIX,
                    onPlay = viewModel::play,
                    firstItemFocusRequester = downRequester,
                )
            }
        }
    }
}

/**
 * 搜索输入框。
 *
 * 遥控器「确定」在输入框上同时做两件事：唤起软键盘、提交当前关键词，
 * 因此不选联想项也能直接看完整结果。
 *
 * @param query 当前输入。
 * @param focused 是否持有焦点（决定边框颜色）。
 * @param onFocusChanged 焦点变化回调。
 * @param onQueryChange 输入变化回调。
 * @param onMoveDown 把焦点移出输入框（到联想首项或结果首项），返回是否成功。
 * @param onSubmit 提交回调。
 */
@Composable
private fun SearchField(
    modifier: Modifier = Modifier,
    query: String,
    focused: Boolean,
    onFocusChanged: (Boolean) -> Unit,
    onQueryChange: (String) -> Unit,
    onMoveDown: () -> Boolean,
    onSubmit: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val colors = MaterialTheme.colorScheme

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = SurfaceDefaults.colors(containerColor = colors.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Material3Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = if (focused) colors.primary else colors.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
            Box(
                modifier =
                    Modifier
                        .padding(start = 16.dp)
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .onFocusChanged { onFocusChanged(it.hasFocus) }
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (event.key) {
                                // BasicTextField 会消费上下方向键，这里显式把焦点交给联想/结果
                                Key.DirectionDown -> onMoveDown()
                                Key.DirectionCenter, Key.Enter -> {
                                    keyboardController?.show()
                                    onSubmit()
                                    true
                                }
                                else -> false
                            }
                        }.background(
                            color = colors.surface,
                            shape = MaterialTheme.shapes.small,
                        ),
            ) {
                BasicTextField(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    cursorBrush = SolidColor(colors.primary),
                    textStyle =
                        TextStyle(
                            color = colors.onSurface,
                            fontSize = 22.sp,
                        ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                    decorationBox = { innerTextField ->
                        Box {
                            if (query.isEmpty()) {
                                Text(
                                    text = "搜索标题 / UP 主",
                                    color = colors.onSurfaceVariant,
                                    fontSize = 22.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            innerTextField()
                        }
                    },
                )
            }
        }
    }
}

/**
 * 标题联想的可聚焦列表（最多 [dev.frost819.newbv.app.data.anlibili.SUGGESTION_LIMIT] 条）。
 *
 * @param suggestions 联想标题。
 * @param focusSaver 焦点恢复器。
 * @param firstItemFocusRequester 绑定到首条联想，供输入框下移焦点。
 * @param onSelect 选中回调。
 */
@Composable
private fun SuggestionList(
    suggestions: List<String>,
    focusSaver: FocusSaver,
    firstItemFocusRequester: FocusRequester,
    onSelect: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "联想",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
        )
        suggestions.forEachIndexed { index, suggestion ->
            SuggestionRow(
                modifier =
                    Modifier
                        .focusSaverItem(focusSaver, "search_suggestion_$index")
                        .then(
                            if (index == 0) {
                                Modifier.focusRequester(firstItemFocusRequester)
                            } else {
                                Modifier
                            },
                        ),
                text = suggestion,
                onClick = { onSelect(suggestion) },
            )
        }
    }
}

/** 单条联想项。 */
@Composable
private fun SuggestionRow(
    modifier: Modifier = Modifier,
    text: String,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = MaterialTheme.shapes.small),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = colors.surfaceVariant.copy(alpha = 0.6f),
                focusedContainerColor = colors.inverseSurface.copy(alpha = 0.5f),
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Material3Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Text(
                modifier = Modifier.padding(start = 12.dp),
                text = text,
                color = colors.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 居中提示文案。 */
@Composable
private fun SearchHint(text: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
