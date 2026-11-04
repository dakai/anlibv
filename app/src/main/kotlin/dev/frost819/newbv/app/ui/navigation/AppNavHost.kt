package dev.frost819.newbv.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import dev.frost819.newbv.app.ui.screen.login.loginScreen
import dev.frost819.newbv.app.ui.screen.main.mainScreen
import dev.frost819.newbv.app.ui.screen.player.videoPlayerScreen
import dev.frost819.newbv.app.ui.screen.settings.settingsScreen
import dev.frost819.newbv.core.log.Loggers

/**
 * 应用 Navigation 宿主。
 *
 * 只保留三个入口：anlibili 视频列表（[HomeRoute]）、视频播放器（[VideoPlayerRoute]）
 * 与设置（[SettingsRoute]，含编解码/日志子页）、登录（[LoginRoute]）。
 *
 * @param navController 导航控制器，默认使用 [rememberNavController]
 * @param startDestination 起始路由，默认 [HomeRoute]
 */
@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
    startDestination: Any = HomeRoute,
) {
    val logger = Loggers.get("AppNavHost")

    LaunchedEffect(navController) {
        var previousRoute: String? = null
        navController.currentBackStackEntryFlow.collect { entry ->
            val route = entry.destination.route?.substringAfterLast('.') ?: "unknown"
            if (previousRoute != null && previousRoute != route) {
                logger.info { "[NAV] from=$previousRoute to=$route" }
            }
            previousRoute = route
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        mainScreen(navController)
        videoPlayerScreen(navController)
        settingsScreen(navController)
        loginScreen(navController)
    }
}
