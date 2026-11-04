package dev.frost819.newbv.app.ui.screen.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import coil3.compose.AsyncImage
import dev.frost819.newbv.app.ui.component.FocusSaver
import dev.frost819.newbv.app.ui.component.focusSaverItem
import dev.frost819.newbv.core.focus.isDpadRight
import dev.frost819.newbv.core.focus.isKeyDown

/** 主界面内容区。 */
enum class MainSection {
    /** anlibili 视频列表。 */
    Anlibili,

    /** 个人页（稍后再看 / 历史 / 收藏）。 */
    Personal,
}

/**
 * 左侧导航栏。
 *
 * 入口：账号（登录按钮）、精选（anlibili 列表）、我的（稍后再看/历史/收藏）、设置。
 *
 * D-Pad 右键触发 [onFocusToContent] 把焦点移入内容区；入口不可聚焦时回退到
 * 焦点系统的默认右向搜索，避免焦点卡在导航栏。
 *
 * @param isLogin 是否已登录。
 * @param avatar 头像 URL（已登录时显示）。
 * @param section 当前内容区。
 * @param onSectionChanged 切换内容区回调。
 * @param onOpenLogin 打开登录页回调。
 * @param onOpenSettings 打开设置页回调。
 * @param onFocusToContent 聚焦内容区回调，返回是否成功。
 * @param focusSaver 焦点恢复器（由 MainScreen 共享传入）。
 */
@Composable
fun LeftNaviContent(
    modifier: Modifier = Modifier,
    isLogin: Boolean = false,
    avatar: String = "",
    section: MainSection = MainSection.Anlibili,
    onSectionChanged: (MainSection) -> Unit = {},
    onOpenLogin: () -> Unit,
    onOpenSettings: () -> Unit,
    onFocusToContent: () -> Boolean,
    focusSaver: FocusSaver,
) {
    val focusManager = LocalFocusManager.current
    NavigationRail(
        modifier =
            modifier
                .fillMaxHeight()
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.isDpadRight() && keyEvent.isKeyDown()) {
                        if (onFocusToContent() || focusManager.moveFocus(FocusDirection.Right)) {
                            return@onPreviewKeyEvent true
                        }
                    }
                    false
                },
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        var userIsFocused by remember { mutableStateOf(false) }
        NavigationRailItem(
            modifier =
                Modifier
                    .focusSaverItem(focusSaver, "user")
                    .onFocusChanged { userIsFocused = it.hasFocus },
            onClick = onOpenLogin,
            selected = userIsFocused,
            icon = {
                if (isLogin) {
                    Surface(
                        modifier =
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape),
                        colors =
                            SurfaceDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                    ) {
                        AsyncImage(
                            modifier =
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape),
                            model = avatar,
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                    )
                }
            },
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            var listIsFocused by remember { mutableStateOf(false) }
            NavigationRailItem(
                modifier =
                    Modifier
                        .onFocusChanged { listIsFocused = it.hasFocus }
                        .selectionIndicator(
                            if (section ==
                                MainSection.Anlibili
                            ) {
                                MaterialTheme.colorScheme.border
                            } else {
                                Color.Transparent
                            },
                        ),
                onClick = { onSectionChanged(MainSection.Anlibili) },
                selected = listIsFocused,
                icon = {
                    Icon(
                        imageVector = Icons.Default.PlaylistPlay,
                        contentDescription = "精选",
                    )
                },
            )

            var personalIsFocused by remember { mutableStateOf(false) }
            NavigationRailItem(
                modifier =
                    Modifier
                        .onFocusChanged { personalIsFocused = it.hasFocus }
                        .selectionIndicator(
                            if (section ==
                                MainSection.Personal
                            ) {
                                MaterialTheme.colorScheme.border
                            } else {
                                Color.Transparent
                            },
                        ),
                onClick = { onSectionChanged(MainSection.Personal) },
                selected = personalIsFocused,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "我的",
                    )
                },
            )
        }

        var settingsIsFocused by remember { mutableStateOf(false) }
        NavigationRailItem(
            modifier =
                Modifier
                    .focusSaverItem(focusSaver, "settings")
                    .onFocusChanged { settingsIsFocused = it.hasFocus },
            onClick = onOpenSettings,
            selected = settingsIsFocused,
            icon = {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "设置",
                )
            },
        )
    }
}

/** 绘制左侧选中指示条。 */
private fun Modifier.selectionIndicator(color: Color): Modifier =
    this.drawBehind {
        val strokeWidth = 4.dp.toPx()
        drawRect(
            color = color,
            topLeft = Offset(-strokeWidth, 0f),
            size = Size(strokeWidth, size.height),
        )
    }
