package com.rolling.learningguider.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rolling.learningguider.ui.icons.AppIcons

/** 四个顶层目的地（顺序即 Tab 顺序） */
enum class TopDestination(val label: String, val icon: ImageVector) {
    NET("知识网", AppIcons.Net),
    LIBRARY("知识库", AppIcons.Library),
    PROGRESS("进度", AppIcons.Progress),
    ME("我的", AppIcons.Me),
}

@Composable
fun AppRoot() {
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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            enterTransition = {
                fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 8 }
            },
            exitTransition = { fadeOut(tween(160)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = {
                fadeOut(tween(160)) + slideOutHorizontally(tween(160)) { it / 8 }
            },
        ) {
            composable(TopDestination.NET.name) { PlaceholderScreen(TopDestination.NET.label, "全屏知识网画布（M3）") }
            composable(TopDestination.LIBRARY.name) { PlaceholderScreen(TopDestination.LIBRARY.label, "技能/岗位/路径/资源（M2）") }
            composable(TopDestination.PROGRESS.name) { PlaceholderScreen(TopDestination.PROGRESS.label, "总进度与岗位掌握度（M4）") }
            composable(TopDestination.ME.name) { PlaceholderScreen(TopDestination.ME.label, "主题/导入导出/设置（M6）") }
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String, hint: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(
            hint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
