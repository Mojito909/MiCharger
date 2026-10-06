package com.micharger

import android.app.Application
import com.micharger.data.battery.BatteryRepository
import com.micharger.data.battery.ChargingController
import com.micharger.data.battery.ChargingNodeDetector
import com.micharger.data.battery.SysFsReader

class MiChargerApp : Application() {

    lateinit var batteryRepository: BatteryRepository
        private set

    lateinit var chargingController: ChargingController
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        batteryRepository = BatteryRepository(this)
        chargingController = ChargingController(SysFsReader(), ChargingNodeDetector(SysFsReader()))
    }

    companion object {
        lateinit var instance: MiChargerApp
            private set
    }
}

val android.content.Context.app: MiChargerApp
    get() = applicationContext as MiChargerApp
