package com.micharger

import android.app.Application
import com.micharger.data.battery.BatteryRepository
import com.micharger.data.battery.ChargingController
import com.micharger.data.battery.ChargingNodeDetector
import com.micharger.data.battery.SysFsReader
import com.micharger.data.history.ChargeSessionMonitor
import com.micharger.data.history.HistoryRepository
import com.micharger.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class MiChargerApp : Application() {

    lateinit var batteryRepository: BatteryRepository
        private set

    lateinit var chargingController: ChargingController
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var historyRepository: HistoryRepository
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        batteryRepository = BatteryRepository(this)
        chargingController = ChargingController(SysFsReader(), ChargingNodeDetector(SysFsReader()))
        settingsRepository = SettingsRepository(this)
        historyRepository = HistoryRepository(this)
        ChargeSessionMonitor.start(this, appScope, historyRepository)
    }

    companion object {
        lateinit var instance: MiChargerApp
            private set
    }
}

val android.content.Context.app: MiChargerApp
    get() = applicationContext as MiChargerApp
