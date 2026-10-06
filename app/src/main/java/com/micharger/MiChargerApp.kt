package com.micharger

import android.app.Application

class MiChargerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: MiChargerApp
            private set
    }
}

// 各处快捷访问：context.app.xxx
val android.content.Context.app: MiChargerApp
    get() = applicationContext as MiChargerApp
