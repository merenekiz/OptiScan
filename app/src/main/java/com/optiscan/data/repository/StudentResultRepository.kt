package com.optiscan.data.repository

import com.optiscan.data.dao.StudentResultDao
import com.optiscan.data.entities.StudentResultEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StudentResultRepository @Inject constructor(
    private val dao: StudentResultDao
) {
    fun getResultsByExam(examId: Long): Flow<List<StudentResultEntity>> =
        dao.getResultsByExam(examId)

    suspend fun getResultById(id: Long): StudentResultEntity? = dao.getResultById(id)

    suspend fun getResultsSortedByScore(examId: Long): List<StudentResultEntity> =
        dao.getResultsByExamSortedByScore(examId)

    suspend fun getResultCount(examId: Long): Int = dao.getResultCountForExam(examId)

    suspend fun getAverageScore(examId: Long): Float? = dao.getAverageScoreForExam(examId)

    suspend fun insertResult(result: StudentResultEntity): Long = dao.insertResult(result)

    suspend fun insertResults(results: List<StudentResultEntity>) = dao.insertResults(results)

    suspend fun deleteResult(result: StudentResultEntity) = dao.deleteResult(result)

    suspend fun deleteAllForExam(examId: Long) = dao.deleteAllResultsForExam(examId)
}
