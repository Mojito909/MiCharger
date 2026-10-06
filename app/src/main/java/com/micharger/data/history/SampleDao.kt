package com.micharger.data.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SampleDao {
    // 返回值刻意非 Unit：KSP2 + Room 对返回 void 的 suspend DAO 有 "unexpected jvm signature V" 的 bug
    @Insert
    suspend fun insert(sample: BatterySample): Long

    @Query("SELECT * FROM battery_samples WHERE timestamp >= :since ORDER BY timestamp ASC")
    suspend fun since(since: Long): List<BatterySample>

    @Query("DELETE FROM battery_samples WHERE timestamp < :before")
    suspend fun trim(before: Long): Int
}
