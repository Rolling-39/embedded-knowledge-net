package com.rolling.learningguider.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rolling.learningguider.LearningGuiderApp
import com.rolling.learningguider.ui.icons.AppIcons
import com.rolling.learningguider.ui.library.LibraryScreen
import com.rolling.learningguider.ui.me.MeScreen
import com.rolling.learningguider.ui.net.NetScreen
import com.rolling.learningguider.ui.progress.ProgressScreen
import com.rolling.learningguider.ui.theme.LearningGuiderTheme

/** 四个顶层目的地（顺序即 Tab 顺序） */
enum class TopDestination(val label: String, val icon: ImageVector) {
    NET("知识网", AppIcons.Net),
    LIBRARY("知识库", AppIcons.Library),
    PROGRESS("进度", AppIcons.Progress),
    ME("我的", AppIcons.Me),
}

@Composable
fun AppRoot() {
    val app = LocalContext.current.applicationContext as LearningGuiderApp
    val vm: AppViewModel = viewModel { AppViewModel(app.container.repository) }
    val ui by vm.ui.collectAsState()

    val darkTheme = when (ui.themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    LearningGuiderTheme(darkTheme = darkTheme) {
        AppRootContent(vm)
    }
}

@Composable
private fun AppRootContent(vm: AppViewModel) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val current = TopDestination.entries.firstOrNull {
        it.name == backStackEntry?.destination?.route
    } ?: TopDestination.NET

    // 页面切换：按 Tab 顺序决定进出方向（右进左出 / 左进右出），弹簧驱动
    val enter = spring<androidx.compose.ui.unit.IntOffset>(dampingRatio = 0.88f, stiffness = 420f)
    val fade = tween<Float>(200, easing = FastOutSlowInEasing)

    Scaffold(
        bottomBar = {
            NavigationBar {
                TopDestination.entries.forEach { dest ->
                    val selected = dest == current
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(dest.name) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = {
                            Text(
                                dest.label,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = TopDestination.NET.name,
            modifier = Modifier.padding(padding),
            enterTransition = {
                val from = TopDestination.entries.indexOfFirst { it.name == initialState.destination.route }
                val to = TopDestination.entries.indexOfFirst { it.name == targetState.destination.route }
                val dir = if (to >= from) 1 else -1
                fadeIn(fade) + slideInHorizontally(enter) { full -> dir * (full / 7) }
            },
            exitTransition = {
                val from = TopDestination.entries.indexOfFirst { it.name == initialState.destination.route }
                val to = TopDestination.entries.indexOfFirst { it.name == targetState.destination.route }
                val dir = if (to >= from) 1 else -1
                fadeOut(tween(160)) + slideOutHorizontally(tween(260, easing = FastOutSlowInEasing)) { full -> -dir * (full / 14) }
            },
            popEnterTransition = { fadeIn(fade) },
            popExitTransition = { fadeOut(tween(160)) },
        ) {
            composable(TopDestination.NET.name) { NetScreen(vm) }
            composable(TopDestination.LIBRARY.name) { LibraryScreen(vm) }
            composable(TopDestination.PROGRESS.name) { ProgressScreen(vm) }
            composable(TopDestination.ME.name) { MeScreen(vm) }
        }
    }
}
