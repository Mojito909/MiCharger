package com.micharger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.micharger.ui.AppRoot
import com.micharger.ui.theme.MiChargerTheme
import top.yukonga.miuix.kmp.theme.ColorSchemeMode

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MiChargerTheme(themeMode = ColorSchemeMode.System) {
                AppRoot()
            }
        }
    }
}
