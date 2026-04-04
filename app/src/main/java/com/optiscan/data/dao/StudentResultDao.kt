package com.optiscan.data.dao

import androidx.room.*
import com.optiscan.data.entities.StudentResultEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentResultDao {

    @Query("SELECT * FROM student_results WHERE examId = :examId ORDER BY scannedAt DESC")
    fun getResultsByExam(examId: Long): Flow<List<StudentResultEntity>>

    @Query("SELECT * FROM student_results WHERE id = :id")
    suspend fun getResultById(id: Long): StudentResultEntity?

    @Query("SELECT * FROM student_results WHERE examId = :examId ORDER BY score DESC")
    suspend fun getResultsByExamSortedByScore(examId: Long): List<StudentResultEntity>

    @Query("SELECT COUNT(*) FROM student_results WHERE examId = :examId")
    suspend fun getResultCountForExam(examId: Long): Int

    @Query("SELECT AVG(score) FROM student_results WHERE examId = :examId")
    suspend fun getAverageScoreForExam(examId: Long): Float?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResult(result: StudentResultEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResults(results: List<StudentResultEntity>)

    @Delete
    suspend fun deleteResult(result: StudentResultEntity)

    @Query("DELETE FROM student_results WHERE examId = :examId")
    suspend fun deleteAllResultsForExam(examId: Long)
}
