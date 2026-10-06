package com.micharger.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.ThemeController

@Composable
fun MiChargerTheme(themeMode: ColorSchemeMode, content: @Composable () -> Unit) {
    // ThemeController 属性只读，模式变化时重建实例
    val controller = remember(themeMode) { ThemeController(colorSchemeMode = themeMode) }
    MiuixTheme(controller = controller, content = content)
}

fun parseThemeMode(raw: String?): ColorSchemeMode =
    runCatching { ColorSchemeMode.valueOf(raw ?: "System") }.getOrDefault(ColorSchemeMode.System)
