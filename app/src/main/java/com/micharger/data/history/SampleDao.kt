package com.micharger.data.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SampleDao {
    @Insert
    suspend fun insert(sample: BatterySample)

    @Query("SELECT * FROM battery_samples WHERE timestamp >= :since ORDER BY timestamp ASC")
    suspend fun since(since: Long): List<BatterySample>

    @Query("DELETE FROM battery_samples WHERE timestamp < :before")
    suspend fun trim(before: Long)
}
