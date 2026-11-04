package dev.frost819.newbv.app.ui.screen.settings.content

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ArrowDropUp
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.frost819.newbv.app.ui.component.settings.OptionDialog
import dev.frost819.newbv.app.ui.component.settings.SettingListItem
import dev.frost819.newbv.app.ui.component.settings.SettingSwitchListItem
import dev.frost819.newbv.app.ui.component.settings.displayName
import dev.frost819.newbv.app.ui.screen.settings.SettingsMenuNavItem
import dev.frost819.newbv.data.datastore.Prefs
import dev.frost819.newbv.data.datastore.ThemeMode
import kotlin.math.roundToInt

/**
 * 界面设置页。
 *
 * 常显进度条/界面缩放/主题模式。
 */
@Composable
fun UISetting(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    var showDensityDialog by remember { mutableStateOf(false) }
    var dialogDensity by remember { mutableFloatStateOf(Prefs.density) }
    var showThemeDialog by remember { mutableStateOf(false) }

    var showPersistentSeek by remember { mutableStateOf(Prefs.showPersistentSeek) }
    var selectedThemeMode by remember { mutableStateOf(Prefs.themeMode) }
    var density by remember { mutableFloatStateOf(Prefs.density) }

    Box(modifier = modifier) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = SettingsMenuNavItem.UI.displayName,
                style = MaterialTheme.typography.displaySmall,
            )
            Spacer(modifier = Modifier.height(12.dp))
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    SettingListItem(
                        title = "主题模式",
                        supportText = "当前：${selectedThemeMode.displayName}",
                        onClick = { showThemeDialog = true },
                    )
                }
                item {
                    SettingSwitchListItem(
                        title = "常显进度条",
                        supportText = "播放器底部常驻迷你进度条",
                        checked = showPersistentSeek,
                        onCheckedChange = {
                            showPersistentSeek = it
                            Prefs.showPersistentSeek = it
                        },
                    )
                }
                item {
                    SettingListItem(
                        title = "界面缩放",
                        supportText = "当前：$density",
                        onClick = {
                            dialogDensity = density
                            showDensityDialog = true
                        },
                    )
                }
            }
        }
    }

    UIDensityDialog(
        show = showDensityDialog,
        onHideDialog = {
            showDensityDialog = false
            if (dialogDensity != density) {
                density = dialogDensity
                Prefs.density = dialogDensity
            }
        },
        density = dialogDensity,
        onDensityChange = { dialogDensity = it },
    )

    if (showThemeDialog) {
        OptionDialog(
            options = ThemeMode.entries.toTypedArray(),
            selectedOption = selectedThemeMode,
            onDismiss = { showThemeDialog = false },
            onSelect = {
                Prefs.themeMode = it
                selectedThemeMode = it
            },
            getDisplayName = { it.displayName },
        )
    }
}

/**
 * Density 调节弹窗。
 *
 * D-Pad Up/Down 调整 density 值，范围 0.5 ~ 5.0，步进 0.1。
 * 弹窗打开期间仅更新本地 [dialogDensity]，关闭时才写入 Prefs，
 * 避免全局 density 变化导致 AlertDialog 窗口重建闪烁。
 */
@Composable
private fun UIDensityDialog(
    modifier: Modifier = Modifier,
    show: Boolean,
    onHideDialog: () -> Unit,
    density: Float,
    onDensityChange: (Float) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(show) {
        if (show) {
            runCatching { focusRequester.requestFocus() }
        }
    }

    if (show) {
        AlertDialog(
            modifier = modifier,
            onDismissRequest = { onHideDialog() },
            title = { Text(text = "界面缩放") },
            text = {
                Column(
                    modifier =
                        Modifier
                            .focusRequester(focusRequester)
                            .focusable()
                            .fillMaxWidth()
                            .onPreviewKeyEvent {
                                if ((it.key == Key.DirectionUp || it.key == Key.DirectionDown) &&
                                    it.type == KeyEventType.KeyDown
                                ) {
                                    var newDensity =
                                        if (it.key == Key.DirectionUp) {
                                            density + 0.1f
                                        } else {
                                            density - 0.1f
                                        }
                                    newDensity = (newDensity * 10).roundToInt() / 10f
                                    onDensityChange(newDensity.coerceIn(0.5f, 5f))
                                    true
                                } else {
                                    false
                                }
                            },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier =
                            Modifier.clickable {
                                var newDensity = density + 0.1f
                                newDensity = (newDensity * 10).roundToInt() / 10f
                                onDensityChange(newDensity.coerceIn(0.5f, 5f))
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(imageVector = Icons.Rounded.ArrowDropUp, contentDescription = "增加")
                    }
                    Text(text = "$density")
                    Box(
                        modifier =
                            Modifier.clickable {
                                var newDensity = density - 0.1f
                                newDensity = (newDensity * 10).roundToInt() / 10f
                                onDensityChange(newDensity.coerceIn(0.5f, 5f))
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(imageVector = Icons.Rounded.ArrowDropDown, contentDescription = "减少")
                    }
                }
            },
            confirmButton = {},
        )
    }
}
