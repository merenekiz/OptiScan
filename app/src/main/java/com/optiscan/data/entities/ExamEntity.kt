package com.optiscan.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "exams")
data class ExamEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val examId: String,           // from QR or manual
    val title: String,
    val subject: String,
    val questionCount: Int,
    val correctPoint: Float,
    val wrongPenalty: Float,
    val answerKey: String,        // JSON: ["A","B","C",...]
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
