package com.optiscan.data.repository

import com.optiscan.data.dao.ExamDao
import com.optiscan.data.entities.ExamEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExamRepository @Inject constructor(
    private val examDao: ExamDao
) {
    fun getAllExams(): Flow<List<ExamEntity>> = examDao.getAllExams()

    suspend fun getExamById(id: Long): ExamEntity? = examDao.getExamById(id)

    suspend fun getExamByExamId(examId: String): ExamEntity? = examDao.getExamByExamId(examId)

    suspend fun insertExam(exam: ExamEntity): Long = examDao.insertExam(exam)

    suspend fun updateExam(exam: ExamEntity) = examDao.updateExam(exam)

    suspend fun deleteExam(exam: ExamEntity) = examDao.deleteExam(exam)
}
