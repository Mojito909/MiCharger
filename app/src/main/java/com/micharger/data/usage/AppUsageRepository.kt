package com.micharger.data.usage

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import com.micharger.ui.usage.UsageParser
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppUsage(
    val label: String,
    val packageName: String,
    val mah: Double?,        // root 精确模式
    val screenTimeMs: Long?, // 估算模式
)

class AppUsageRepository(private val context: Context) {

    fun hasUsageAccess(): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        // OP_GET_USAGE_STATS 常量在新 SDK 中已隐藏，改用字符串操作名（公开 API）
        val mode = ops.checkOpNoThrow(
            "android:get_usage_stats", Process.myUid(), context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Root 模式：batterystats 耗电排行 */
    suspend fun powerRanking(): List<AppUsage> = withContext(Dispatchers.IO) {
        val result = Shell.cmd("dumpsys batterystats --charged").exec()
        val uidPowers = UsageParser.parse(result.out.joinToString("\n"))
        uidPowers.mapNotNull { up ->
            val pkg = context.packageManager.getPackagesForUid(up.uid)?.firstOrNull()
                ?: return@mapNotNull null
            AppUsage(label = labelFor(pkg), packageName = pkg, mah = up.mah, screenTimeMs = null)
        }
    }

    /** 无 Root 模式：近 24h 前台时长排行 */
    suspend fun screenTimeRanking(): List<AppUsage> = withContext(Dispatchers.IO) {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val start = end - 24L * 3600 * 1000
        usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
            .filter { it.totalTimeInForeground > 0 }
            .sortedByDescending { it.totalTimeInForeground }
            .take(20)
            .map { stats ->
                AppUsage(
                    label = labelFor(stats.packageName),
                    packageName = stats.packageName,
                    mah = null,
                    screenTimeMs = stats.totalTimeInForeground,
                )
            }
    }

    private fun labelFor(pkg: String): String =
        runCatching {
            context.packageManager.getApplicationLabel(
                context.packageManager.getApplicationInfo(pkg, 0),
            ).toString()
        }.getOrDefault(pkg)
}
