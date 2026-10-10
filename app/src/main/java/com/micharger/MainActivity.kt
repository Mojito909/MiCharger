package com.micharger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.micharger.data.settings.AppSettings
import com.micharger.service.ChargingGuardService
import com.micharger.ui.AppRoot
import com.micharger.ui.theme.MiChargerTheme
import com.micharger.ui.theme.parseThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val app: MiChargerApp get() = application as MiChargerApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 打开应用时校正守护服务：守护/温控开关为开但服务未运行（如重启后未自启）则自动拉起
        lifecycleScope.launch(Dispatchers.IO) {
            val settings = app.settingsRepository.settingsOnce()
            if ((settings.guardEnabled || settings.tempStopEnabled) && !ChargingGuardService.isRunning) {
                ChargingGuardService.start(this@MainActivity)
            }
        }
        setContent {
            val settings by app.settingsRepository.settingsFlow.collectAsState(initial = AppSettings())
            MiChargerTheme(themeMode = parseThemeMode(settings.themeMode)) {
                AppRoot()
            }
        }
    }
}
