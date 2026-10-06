package com.micharger.data.history

import android.content.Context
import androidx.room.Room
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

class HistoryRepository(context: Context) {
    private val dao = Room.databaseBuilder(
        context.applicationContext,
        HistoryDatabase::class.java,
        "micharger_history.db",
    ).build().sampleDao()

    suspend fun insert(sample: BatterySample) = dao.insert(sample)

    suspend fun last24h(): List<BatterySample> =
        dao.since(System.currentTimeMillis() - 24L * 3600 * 1000)

    /**
     * 今日充电次数：优先 root 解析系统电池历史（dumpsys batterystats），
     * 覆盖 App 未运行期间的会话；解析失败时回退本地样本统计。
     */
    suspend fun todayChargingCount(): Int = withContext(Dispatchers.IO) {
        val samples = dao.since(startOfToday())
        val localCount = chargingTransitions(samples)
        maxOf(localCount, countFromDumpsys() ?: 0)
    }

    /**
     * 从系统电池历史统计今天 0 点以来的充电开始次数（+charging 跳变数）。
     * 返回 null 表示 dumpsys 输出不可解析（选项不受支持/无 root），应走本地兜底。
     */
    private fun countFromDumpsys(): Int? {
        val result = runCatching {
            Shell.cmd("dumpsys batterystats --history-start=${startOfToday()}").exec()
        }.getOrNull() ?: return null
        if (!result.isSuccess) return null
        val out = result.out
        // 没有 Battery History 区块：选项不受支持或输出为错误信息
        if (out.none { it.contains("Battery History", ignoreCase = true) }) return null
        // 历史行格式：<时间偏移> [+charging|-charging] [+plugged...] <电量>%…
        // "+charging" 即一次充电开始；"-charging" 不包含 "+"，天然互斥
        return out.count { "+charging" in it }
    }

    private fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun chargingTransitions(samples: List<BatterySample>): Int =
        samples.countIndexed { index, sample ->
            sample.charging && (index == 0 || !samples[index - 1].charging)
        }

    private inline fun <T> List<T>.countIndexed(predicate: (Int, T) -> Boolean): Int {
        var count = 0
        forEachIndexed { index, item -> if (predicate(index, item)) count++ }
        return count
    }

    suspend fun trim() =
        dao.trim(System.currentTimeMillis() - 7L * 24 * 3600 * 1000)
}
