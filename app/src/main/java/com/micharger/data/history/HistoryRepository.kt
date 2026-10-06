package com.micharger.data.history

import android.content.Context
import androidx.room.Room
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

    suspend fun todayChargingCount(): Int {
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return chargingTransitions(dao.since(start))
    }

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
