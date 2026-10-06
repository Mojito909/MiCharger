package com.micharger.data.battery

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlin.math.abs

data class BatteryInfo(
    val levelPct: Int,
    val charging: Boolean,
    val voltageMv: Int?,
    val currentMa: Double?,
    val powerW: Double?,
    val tempTenths: Int?,
    val chargeCounterMah: Int?,
    val timestamp: Long = System.currentTimeMillis(),
)

class BatteryRepository(private val context: Context) {

    private val batteryManager by lazy {
        context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    }

    fun snapshot(): BatteryInfo {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val voltageMv = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)?.takeIf { it > 0 }
        val temp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            ?.takeIf { it != Int.MIN_VALUE }

        val currentUa = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        var currentMa = if (currentUa != Int.MIN_VALUE && currentUa != 0) currentUa / 1000.0 else null
        if (currentMa == null) {
            // sysfs 兜底（单位 µA）
            val sysCurrent = SysFsReader().read("/sys/class/power_supply/battery/current_now")
            currentMa = sysCurrent?.toDoubleOrNull()?.div(1000.0)
        }

        val counterUah = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val counterMah = if (counterUah != Int.MIN_VALUE) counterUah / 1000 else null

        val powerW = if (voltageMv != null && currentMa != null) {
            abs(voltageMv / 1000.0 * currentMa) / 1000.0
        } else null

        val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else 0
        return BatteryInfo(
            levelPct = pct,
            charging = charging,
            voltageMv = voltageMv,
            currentMa = currentMa,
            powerW = powerW,
            tempTenths = temp,
            chargeCounterMah = counterMah,
        )
    }
}
