package com.micharger.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.micharger.R
import com.micharger.app
import com.micharger.data.battery.BatterySample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ChargingGuardService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForegroundCompat(buildNotification("充电守护运行中"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch { guardLoop() }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun guardLoop() {
        val app = app
        val history = app.historyRepository
        while (isActive) {
            val settings = app.settingsRepository.settingsOnce()
            if (!settings.guardEnabled) break

            val info = app.batteryRepository.snapshot()
            val controller = app.chargingController

            // 首次循环若未初始化节点则尝试初始化
            if (controller.nodes == null) controller.initialize()

            val isSuspended = controller.isSuspended()
            when {
                isSuspended == false && info.levelPct >= settings.targetSoc ->
                    controller.pause()
                isSuspended == true && info.levelPct <= settings.resumeSoc ->
                    controller.resume()
            }

            // 历史采样
            runCatching {
                history.insert(
                    BatterySample(
                        timestamp = info.timestamp,
                        level = info.levelPct,
                        charging = info.charging,
                    ),
                )
                history.trim()
            }

            updateNotification("电量 ${info.levelPct}% · " + if (info.charging) "充电中" else "未充电")
            delay(settings.sampleMinutes * 60_000L)
        }
        stopSelf()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "充电守护",
            NotificationManager.IMPORTANCE_LOW,
        )
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("充电管家")
            .setContentText(text)
            .setOngoing(true)
            .build()

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    companion object {
        private const val CHANNEL_ID = "charging_guard"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            context.startForegroundService(Intent(context, ChargingGuardService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ChargingGuardService::class.java))
        }
    }
}
