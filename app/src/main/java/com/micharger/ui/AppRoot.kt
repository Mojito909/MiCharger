package com.micharger.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import com.micharger.ui.about.AboutScreen
import com.micharger.ui.home.HomeScreen
import com.micharger.ui.icons.AppIcons
import com.micharger.ui.info.InfoScreen
import com.micharger.ui.settings.SettingsScreen

private data class Tab(val label: String, val icon: ImageVector)

@Composable
fun AppRoot() {
    var tab by rememberSaveable { mutableStateOf(0) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    val tabs = listOf(
        Tab("充电", AppIcons.Home),
        Tab("信息", AppIcons.Chart),
        Tab("设置", AppIcons.Tune),
    )
    BackHandler(enabled = showAbout) { showAbout = false }
    Scaffold(
        bottomBar = {
            if (!showAbout) {
                NavigationBar {
                    tabs.forEachIndexed { index, item ->
                        NavigationBarItem(
                            selected = tab == index,
                            onClick = { tab = index },
                            icon = item.icon,
                            label = item.label,
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = padding.calculateBottomPadding()),
        ) {
            if (showAbout) {
                AboutScreen(onBack = { showAbout = false })
            } else {
                when (tab) {
                    0 -> HomeScreen()
                    1 -> InfoScreen()
                    else -> SettingsScreen(onOpenAbout = { showAbout = true })
                }
            }
        }
    }
}
