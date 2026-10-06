package com.micharger.data.history

import android.content.Context
import androidx.room.Room

class HistoryRepository(context: Context) {
    private val dao = Room.databaseBuilder(
        context.applicationContext,
        HistoryDatabase::class.java,
        "micharger_history.db",
    ).build().sampleDao()

    suspend fun insert(sample: BatterySample) = dao.insert(sample)

    suspend fun last24h(): List<BatterySample> =
        dao.since(System.currentTimeMillis() - 24L * 3600 * 1000)

    suspend fun trim() =
        dao.trim(System.currentTimeMillis() - 7L * 24 * 3600 * 1000)
}
