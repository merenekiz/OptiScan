package com.optiscan.data

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import com.optiscan.data.dao.ExamDao
import com.optiscan.data.dao.ScanSessionDao
import com.optiscan.data.dao.StudentResultDao
import com.optiscan.data.entities.ExamEntity
import com.optiscan.data.entities.ScanSessionEntity
import com.optiscan.data.entities.StudentResultEntity

@Database(
    entities = [
        ExamEntity::class,
        StudentResultEntity::class,
        ScanSessionEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class OptiScanDatabase : RoomDatabase() {

    abstract fun examDao(): ExamDao
    abstract fun studentResultDao(): StudentResultDao
    abstract fun scanSessionDao(): ScanSessionDao

    companion object {
        @Volatile private var INSTANCE: OptiScanDatabase? = null

        fun getInstance(context: Context): OptiScanDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    OptiScanDatabase::class.java,
                    "optiscan.db"
                )
                .fallbackToDestructiveMigration()
                .build()
                .also { INSTANCE = it }
            }
    }
}
