package com.micharger.data.battery

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import java.util.Locale
import kotlin.math.abs

data class BatteryInfo(
    val levelPct: Int,
    val charging: Boolean,
    val voltageMv: Int?,
    val currentMa: Double?,
    val powerW: Double?,
    val tempTenths: Int?,
    val chargeCounterMah: Int?,
    val chargeType: String?, // 快充 / 慢充，未充电或无法判断为 null
    val protocol: String?,   // 充电协议（如 QC3.0 / USB PD），未充电或无法识别为 null
    val timestamp: Long = System.currentTimeMillis(),
)

class BatteryRepository(private val context: Context) {

    private val batteryManager by lazy {
        context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    }
    private val sysReader = SysFsReader()

    /** 充电协议节点读取较重（走 root shell），缓存 30 秒 */
    private var chargerReads: ChargerReads? = null
    private var chargerReadAt = 0L
    private var lastCharging: Boolean? = null

    private data class ChargerReads(
        val chargeType: String?, // battery/charge_type 原始值（Fast/Slow/...）
        val realType: String?,   // usb/real_type 原始值（QC3/PD/DCP/...）
    )

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
            val sysCurrent = sysReader.read("/sys/class/power_supply/battery/current_now")
            currentMa = sysCurrent?.toDoubleOrNull()?.div(1000.0)
        }

        val counterUah = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val counterMah = if (counterUah != Int.MIN_VALUE) counterUah / 1000 else null

        val powerW = if (voltageMv != null && currentMa != null) {
            abs(voltageMv / 1000.0 * currentMa) / 1000.0
        } else null

        val charger = chargerInfo(charging)
        val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else 0
        return BatteryInfo(
            levelPct = pct,
            charging = charging,
            voltageMv = voltageMv,
            currentMa = currentMa,
            powerW = powerW,
            tempTenths = temp,
            chargeCounterMah = counterMah,
            chargeType = classifyChargeType(charging, charger.chargeType, powerW),
            protocol = if (charging) charger.realType?.let(::mapProtocol) else null,
        )
    }

    private fun chargerInfo(charging: Boolean): ChargerReads {
        val now = System.currentTimeMillis()
        val stale = now - chargerReadAt > CHARGER_REFRESH_MS
        val stateChanged = lastCharging != charging
        val cached = chargerReads
        if (cached == null || stale || stateChanged) {
            chargerReads = readChargerNodes()
            chargerReadAt = now
            lastCharging = charging
        }
        return chargerReads ?: ChargerReads(null, null)
    }

    private fun readChargerNodes(): ChargerReads {
        val base = "/sys/class/power_supply"
        val chargeType = runCatching { sysReader.read("$base/battery/charge_type") }.getOrNull()
        val realType = runCatching { sysReader.read("$base/usb/real_type") }.getOrNull()
            ?: runCatching { sysReader.read("$base/usb/type") }.getOrNull()
        return ChargerReads(chargeType, realType)
    }

    /** 快充/慢充判定：优先内核 charge_type，缺失时按功率阈值（≥18W 为快充） */
    private fun classifyChargeType(charging: Boolean, chargeType: String?, powerW: Double?): String? {
        if (!charging) return null
        when (chargeType?.trim()?.lowercase(Locale.US)) {
            "fast" -> return "快充"
            "slow", "trickle", "taper" -> return "慢充"
        }
        return powerW?.let { if (it >= FAST_CHARGE_WATTS) "快充" else "慢充" }
    }

    /** 把内核 real_type/type 映射为友好协议名 */
    private fun mapProtocol(raw: String): String? {
        val t = raw.trim().uppercase(Locale.US)
        if (t.isEmpty() || t == "UNKNOWN" || t == "NONE" || t == "N/A") return null
        return when {
            t.contains("PPS") -> "PD（PPS）"
            t == "PD" || t.startsWith("PD_") -> "USB PD"
            t.contains("QC3P5") || t.contains("HVDCP_3P5") -> "QC3.5"
            t.contains("QC3") || t.contains("HVDCP_3") -> "QC3.0"
            t.contains("QC2") || t.contains("HVDCP_2") -> "QC2.0"
            t.contains("QC") || t.contains("HVDCP") -> "QC 快充"
            t == "DCP" -> "普通充电器（DCP）"
            t == "SDP" -> "电脑 USB（SDP）"
            t == "CDP" -> "USB 连接（CDP）"
            t == "USB_C" -> "USB-C"
            t == "USB" || t == "USB_DCP" -> "USB"
            else -> raw.trim()
        }
    }

    private companion object {
        const val FAST_CHARGE_WATTS = 18.0
        const val CHARGER_REFRESH_MS = 30_000L
    }
}
