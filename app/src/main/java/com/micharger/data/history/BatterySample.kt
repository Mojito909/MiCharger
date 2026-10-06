package com.micharger.data.history

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "battery_samples")
data class BatterySample(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val level: Int,      // 0-100
    val charging: Boolean,
)
