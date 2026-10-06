package com.micharger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.micharger.data.settings.AppSettings
import com.micharger.ui.AppRoot
import com.micharger.ui.theme.MiChargerTheme
import com.micharger.ui.theme.parseThemeMode

class MainActivity : ComponentActivity() {

    private val app: MiChargerApp get() = application as MiChargerApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by app.settingsRepository.settingsFlow.collectAsState(initial = AppSettings())
            MiChargerTheme(themeMode = parseThemeMode(settings.themeMode)) {
                AppRoot()
            }
        }
    }
}
