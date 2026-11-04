package dev.frost819.newbv.app.ui.screen.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dev.frost819.newbv.app.ui.component.rememberDoublePressExit
import dev.frost819.newbv.app.ui.component.rememberFocusSaver
import dev.frost819.newbv.app.ui.navigation.HomeRoute
import dev.frost819.newbv.app.ui.navigation.LoginRoute
import dev.frost819.newbv.app.ui.navigation.SettingsRoute
import dev.frost819.newbv.app.ui.screen.anlibili.AnlibiliContent
import dev.frost819.newbv.app.ui.screen.personal.PersonalContent
import dev.frost819.newbv.app.ui.screen.search.SearchContent
import dev.frost819.newbv.app.viewmodel.user.UserViewModel

/** 主页面路由注册。 */
fun NavGraphBuilder.mainScreen(navController: NavController) {
    composable<HomeRoute> {
        MainScreen(navController = navController)
    }
}

/**
 * 主页面：左侧导航栏 + anlibili 视频列表。
 *
 * 双击返回退出：首次返回显示 Toast，3 秒内再次返回退出 App。
 *
 * @param navController 导航控制器（跳转设置/登录/播放器）。
 * @param userViewModel 账号状态（导航栏头像与登录入口）。
 */
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    navController: NavController,
    userViewModel: UserViewModel = hiltViewModel(),
) {
    val userUiState by userViewModel.uiState.collectAsState()
    val context = LocalContext.current
    var focusInitialized by rememberSaveable { mutableStateOf(false) }
    var section by rememberSaveable { mutableStateOf(MainSection.Anlibili) }

    val contentFocusRequester = remember { FocusRequester() }
    val focusSaver = rememberFocusSaver()

    // 仅"返回 MainScreen"时恢复焦点；首次进入不恢复，
    // 否则启动阶段系统自动聚焦左侧栏头像产生的 key 会把焦点抢回头像。
    if (focusInitialized) {
        focusSaver.RestoreFocus()
    }

    val handleBack =
        rememberDoublePressExit(
            onExit = { (context as? android.app.Activity)?.finish() },
            message = "再按一次退出",
        )

    // 内容区首项由 AnlibiliContent 自行聚焦；这里交回焦点系统做右向搜索，
    // 避免 FocusRequester 指向不可聚焦容器导致"请求成功但焦点没动"。
    val onFocusToContent: () -> Boolean = { false }

    LaunchedEffect(Unit) {
        if (!focusInitialized) {
            focusInitialized = true
            focusSaver.clearFocusedKey()
        }
    }

    BackHandler { handleBack() }

    Row(modifier = modifier.fillMaxSize()) {
        LeftNaviContent(
            isLogin = userUiState.isLogin,
            avatar = userUiState.avatar,
            section = section,
            onSectionChanged = { section = it },
            onOpenLogin = { navController.navigate(LoginRoute) },
            onOpenSettings = { navController.navigate(SettingsRoute) },
            onFocusToContent = onFocusToContent,
            focusSaver = focusSaver,
        )
        Box(modifier = Modifier.weight(1f)) {
            when (section) {
                MainSection.Anlibili ->
                    AnlibiliContent(
                        navFocusRequester = contentFocusRequester,
                        navController = navController,
                        focusSaver = focusSaver,
                    )
                MainSection.Search ->
                    SearchContent(
                        navController = navController,
                        focusSaver = focusSaver,
                    )
                MainSection.Personal ->
                    PersonalContent(
                        navFocusRequester = contentFocusRequester,
                        navController = navController,
                        focusSaver = focusSaver,
                    )
            }
        }
    }
}
