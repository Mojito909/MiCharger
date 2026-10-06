package com.micharger

import android.app.Application
import com.micharger.data.battery.BatteryRepository

class MiChargerApp : Application() {

    lateinit var batteryRepository: BatteryRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        batteryRepository = BatteryRepository(this)
    }

    companion object {
        lateinit var instance: MiChargerApp
            private set
    }
}

val android.content.Context.app: MiChargerApp
    get() = applicationContext as MiChargerApp
