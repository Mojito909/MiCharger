package com.micharger.data.history

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [BatterySample::class], version = 1, exportSchema = false)
abstract class HistoryDatabase : RoomDatabase() {
    abstract fun sampleDao(): SampleDao
}
