package com.micharger.util

import java.util.Locale

object Formatters {

    fun temp(tenths: Int?): String =
        tenths?.let { String.format(Locale.US, "%.1f ℃", it / 10.0) } ?: "--"

    fun current(ma: Double?): String =
        ma?.let { String.format(Locale.US, "%.0f mA", it) } ?: "--"

    fun voltage(mv: Int?): String =
        mv?.let { String.format(Locale.US, "%.3f V", it / 1000.0) } ?: "--"

    fun power(w: Double?): String =
        w?.let { String.format(Locale.US, "%.2f W", it) } ?: "--"

    fun capacity(mah: Int?): String =
        mah?.let { String.format(Locale.US, "%d mAh", it) } ?: "--"

    fun duration(ms: Long): String {
        val totalMin = ms / 60000
        val h = totalMin / 60
        val m = totalMin % 60
        return when {
            h > 0 -> "${h}小时${m}分"
            else -> "${m}分钟"
        }
    }

    fun hhmm(ts: Long): String =
        java.text.SimpleDateFormat("HH:mm", Locale.US).format(java.util.Date(ts))
}
