package com.example.lunacycle.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [PeriodRecordEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(LocalDateConverter::class)
abstract class LunaDatabase : RoomDatabase() {

    abstract fun periodDao(): PeriodDao

    companion object {
        private const val DATABASE_NAME = "luna_cycle.db"

        @Volatile
        private var INSTANCE: LunaDatabase? = null

        fun getInstance(context: Context): LunaDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LunaDatabase::class.java,
                    DATABASE_NAME
                ).build().also { INSTANCE = it }
            }
        }
    }
}
