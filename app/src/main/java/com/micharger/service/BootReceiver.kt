package com.micharger.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.micharger.app
import kotlinx.coroutines.runBlocking

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val settings = runBlocking { context.app.settingsRepository.settingsOnce() }
        if (settings.bootStart && settings.guardEnabled) {
            ChargingGuardService.start(context)
        }
    }
}
