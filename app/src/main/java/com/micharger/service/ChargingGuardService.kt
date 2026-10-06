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
import com.micharger.data.history.BatterySample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ChargingGuardService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var guardJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForegroundCompat(buildNotification("充电守护运行中"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 重复启动时取消旧循环，保证守护循环全局唯一
        guardJob?.cancel()
        guardJob = scope.launch { guardLoop() }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun guardLoop() {
        val app = app
        val history = app.historyRepository
        var lastSampleAt = 0L

        while (currentCoroutineContext().isActive) {
            // 单轮异常不终结守护
            runCatching {
                val settings = app.settingsRepository.settingsOnce()
                if (!settings.guardEnabled) {
                    stopSelf()
                    return
                }

                val controller = app.chargingController
                // 节点检测失败（无可写节点）时每轮重试，直到成功
                if (!controller.canControl) controller.initialize()

                val info = app.batteryRepository.snapshot()

                // 直接按电量写节点：写是幂等的，不依赖读状态是否可解析
                val status = when {
                    info.levelPct >= settings.targetSoc ->
                        if (controller.pause()) "已暂停充电（目标 ${settings.targetSoc}%）"
                        else "暂停失败：无法写入充电节点"

                    info.levelPct <= settings.resumeSoc ->
                        if (controller.resume()) "已恢复充电（恢复阈值 ${settings.resumeSoc}%）"
                        else "恢复失败：无法写入充电节点"

                    else -> "守护中 · 目标 ${settings.targetSoc}%"
                }
                updateNotification("电量 ${info.levelPct}% · $status")

                // 历史采样按独立间隔记录
                val now = System.currentTimeMillis()
                if (now - lastSampleAt >= settings.sampleMinutes * 60_000L) {
                    lastSampleAt = now
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
                }
            }
            delay(CONTROL_INTERVAL_MS)
        }
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

        /** 控制周期：与采样间隔解耦，保证阈值动作及时执行 */
        private const val CONTROL_INTERVAL_MS = 30_000L

        fun start(context: Context) {
            context.startForegroundService(Intent(context, ChargingGuardService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ChargingGuardService::class.java))
        }
    }
}
