package com.optiscan.data.dao

import androidx.room.*
import com.optiscan.data.entities.ScanSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanSessionDao {

    @Query("SELECT * FROM scan_sessions WHERE examId = :examId ORDER BY sessionStart DESC")
    fun getSessionsByExam(examId: Long): Flow<List<ScanSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ScanSessionEntity): Long

    @Update
    suspend fun updateSession(session: ScanSessionEntity)

    @Query("DELETE FROM scan_sessions WHERE examId = :examId")
    suspend fun deleteSessionsForExam(examId: Long)
}
