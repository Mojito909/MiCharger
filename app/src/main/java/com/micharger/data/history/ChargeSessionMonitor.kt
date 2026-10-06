package com.micharger.data.history

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 独立于守护采样的充电会话监测。
 *
 * 守护服务的采样按分钟级间隔写入，短时插拔或采样间隔较长时不会记录到
 * charging=true 的样本，导致"今日充电次数"恒为 0。本监测器监听系统电池
 * 广播，仅在「未充电 → 充电中」跳变时写入一条样本，保证跳变即时入库。
 *
 * 覆盖范围：App 进程存活期间（前台使用或守护服务运行时）。
 */
object ChargeSessionMonitor {

    fun start(context: Context, scope: CoroutineScope, history: HistoryRepository) {
        val appContext = context.applicationContext
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)

        // 先读 sticky 广播初始化基准状态，不写库（启动时已在充电属于历史会话，
        // 是否计数由已入库的样本决定，避免与守护采样重复计数）
        var lastCharging: Boolean = readSticky(appContext, filter)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                val pct = if (level >= 0 && scale > 0) level * 100 / scale else -1

                val wasCharging = lastCharging
                lastCharging = charging

                // 仅在「未充电 → 充电中」跳变时写库
                if (charging && !wasCharging) {
                    scope.launch(Dispatchers.IO) {
                        history.insert(
                            BatterySample(
                                timestamp = System.currentTimeMillis(),
                                level = pct.coerceAtLeast(0),
                                charging = true,
                            ),
                        )
                        history.trim()
                    }
                }
            }
        }
        appContext.registerReceiver(receiver, filter)
    }

    private fun readSticky(context: Context, filter: IntentFilter): Boolean {
        val sticky = context.registerReceiver(null, filter) ?: return false
        val status = sticky.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
    }
}
