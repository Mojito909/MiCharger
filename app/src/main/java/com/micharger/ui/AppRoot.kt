package com.micharger.ui

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
import com.micharger.ui.home.HomeScreen
import com.micharger.ui.icons.AppIcons
import com.micharger.ui.info.InfoScreen
import com.micharger.ui.settings.SettingsScreen
import com.micharger.ui.usage.UsageScreen

private data class Tab(val label: String, val icon: ImageVector)

@Composable
fun AppRoot() {
    var tab by rememberSaveable { mutableStateOf(0) }
    val tabs = listOf(
        Tab("充电", AppIcons.Home),
        Tab("耗电", AppIcons.Bolt),
        Tab("信息", AppIcons.Chart),
        Tab("设置", AppIcons.Tune),
    )
    Scaffold(
        bottomBar = {
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
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
            when (tab) {
                0 -> HomeScreen()
                1 -> UsageScreen()
                2 -> InfoScreen()
                else -> SettingsScreen()
            }
        }
    }
}
