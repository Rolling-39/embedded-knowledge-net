package com.rolling.learningguider.ui

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

    Scaffold(
        bottomBar = {
            NavigationBar {
                TopDestination.entries.forEach { dest ->
                    NavigationBarItem(
                        selected = dest == current,
                        onClick = {
                            navController.navigate(dest.name) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label) },
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
                fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 8 }
            },
            exitTransition = { fadeOut(tween(160)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = {
                fadeOut(tween(160)) + slideOutHorizontally(tween(160)) { it / 8 }
            },
        ) {
            composable(TopDestination.NET.name) { NetScreen(vm) }
            composable(TopDestination.LIBRARY.name) { LibraryScreen(vm) }
            composable(TopDestination.PROGRESS.name) { ProgressScreen(vm) }
            composable(TopDestination.ME.name) { MeScreen(vm) }
        }
    }
}
