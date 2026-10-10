package com.micharger.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.micharger.R
import com.micharger.app
import com.micharger.data.history.BatterySample
import com.micharger.util.Formatters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ChargingGuardService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val roundMutex = Mutex()
    private var guardJob: Job? = null
    private var lastShownInBar: Boolean? = null
    private var lastNotifyText: String? = null
    private var chargingPaused: Boolean? = null
    private var socPaused = false
    private var tempPaused = false
    private var lastSampleAt = 0L
    private var lastRoundAt = 0L

    /** 电量/温度变化广播：温度跨阈值时秒级响应，无需等待轮询周期 */
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val now = System.currentTimeMillis()
            if (now - lastRoundAt < EVENT_MIN_INTERVAL_MS) return
            scope.launch { runCatching { guardRound() } }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createChannels()
        lastShownInBar = true
        startForegroundCompat(buildNotification(CHANNEL_ID, "充电守护运行中"))
        ContextCompat.registerReceiver(
            this,
            batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 重复启动时取消旧循环，保证守护循环全局唯一
        guardJob?.cancel()
        guardJob = scope.launch {
            while (currentCoroutineContext().isActive) {
                runCatching { guardRound() }
                delay(CONTROL_INTERVAL_MS)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        runCatching { unregisterReceiver(batteryReceiver) }
        isRunning = false
        statusFlow.value = null
        chargingPaused = null
        socPaused = false
        tempPaused = false
        lastNotifyText = null
        super.onDestroy()
    }

    /** 单轮决策：轮询循环与电池广播共用，互斥串行避免并发写节点 */
    private suspend fun guardRound() {
        roundMutex.withLock {
            lastRoundAt = System.currentTimeMillis()
            val app = app
            val history = app.historyRepository
            val settings = app.settingsRepository.settingsOnce()
            if (!settings.guardEnabled && !settings.tempStopEnabled) {
                stopSelf()
            } else {
                val controller = app.chargingController
                // 节点检测失败（无可写节点）时每轮重试，直到成功
                if (!controller.canControl) controller.initialize()

                val info = app.batteryRepository.snapshot()

                // 电量锁存：仅守护开启时参与决策
                if (settings.guardEnabled) {
                    if (info.levelPct >= settings.targetSoc) socPaused = true
                    else if (info.levelPct <= settings.resumeSoc) socPaused = false
                }
                // 温度锁存：仅温控开启时参与决策；温度未知时保持原状态
                if (settings.tempStopEnabled && info.tempTenths != null) {
                    val tempC = info.tempTenths / 10.0
                    if (tempC >= settings.tempStopC) tempPaused = true
                    else if (tempC <= settings.tempResumeC) tempPaused = false
                } else if (!settings.tempStopEnabled) {
                    tempPaused = false
                }

                val wantPause = socPaused || tempPaused
                var status = when {
                    wantPause && tempPaused ->
                        "温控暂停充电（${Formatters.temp(info.tempTenths)} ≥ ${settings.tempStopC}℃）"
                    wantPause -> "已暂停充电（目标 ${settings.targetSoc}%）"
                    settings.guardEnabled && info.levelPct <= settings.resumeSoc ->
                        "已恢复充电（恢复阈值 ${settings.resumeSoc}%）"
                    settings.guardEnabled && settings.tempStopEnabled ->
                        "守护中 · 目标 ${settings.targetSoc}% · ${Formatters.temp(info.tempTenths)}"
                    settings.tempStopEnabled ->
                        "温控保护中 · ${Formatters.temp(info.tempTenths)}"
                    else -> "守护中 · 目标 ${settings.targetSoc}%"
                }

                // 只在状态需要变化时写节点；恢复仅撤销本服务发起的暂停，不覆盖手动暂停
                val success = if (wantPause) {
                    if (chargingPaused != true) {
                        val ok = controller.pause()
                        if (ok) chargingPaused = true
                        ok
                    } else true
                } else {
                    if (chargingPaused == true) {
                        val ok = controller.resume()
                        if (ok) chargingPaused = false
                        ok
                    } else true
                }
                if (!success) {
                    status = if (wantPause) "暂停失败：无法写入充电节点" else "恢复失败：无法写入充电节点"
                }

                updateNotification("电量 ${info.levelPct}% · $status", settings.showNotification)
                statusFlow.value = status

                // 历史采样按独立间隔记录（广播触发的时间门控自动去重）
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
        }
    }

    private fun createChannels() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "充电守护",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                setShowBadge(true)
            },
        )
        // 静默通道：最低重要性，不显示状态栏图标，满足前台服务的系统通知要求
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SILENT_ID,
                "充电守护（静默）",
                NotificationManager.IMPORTANCE_MIN,
            ),
        )
    }

    private fun buildNotification(channelId: String, text: String): Notification =
        Notification.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher)
            .setBadgeIconType(Notification.BADGE_ICON_SMALL)
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

    /**
     * 更新通知；关闭状态栏显示时降级到静默通道。
     * 通道切换必须重新 startForeground 才能生效，同通道内直接 notify 即可。
     * 文本与通道均未变化时跳过，避免事件路径高频刷新状态栏。
     */
    private fun updateNotification(text: String, showInBar: Boolean) {
        if (text == lastNotifyText && lastShownInBar == showInBar) return
        lastNotifyText = text
        val notification = buildNotification(
            if (showInBar) CHANNEL_ID else CHANNEL_SILENT_ID,
            if (showInBar) text else "充电守护运行中",
        )
        if (lastShownInBar != showInBar) {
            startForegroundCompat(notification)
            lastShownInBar = showInBar
        } else {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val CHANNEL_ID = "charging_guard"
        private const val CHANNEL_SILENT_ID = "charging_guard_silent"
        private const val NOTIFICATION_ID = 1

        /** 服务是否存活，供 UI 判断开关状态与服务是否一致 */
        @Volatile
        var isRunning: Boolean = false
            private set

        /** 首页展示的实时守护动作；null 表示服务未运行 */
        val statusFlow = MutableStateFlow<String?>(null)

        /** 控制周期：与采样间隔解耦，保证阈值动作及时执行 */
        private const val CONTROL_INTERVAL_MS = 30_000L

        /** 广播触发单轮决策的最小间隔，防止充电时广播风暴 */
        private const val EVENT_MIN_INTERVAL_MS = 3_000L

        fun start(context: Context) {
            context.startForegroundService(Intent(context, ChargingGuardService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ChargingGuardService::class.java))
        }
    }
}
