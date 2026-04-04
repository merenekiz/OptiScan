package com.optiscan.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "student_results",
    foreignKeys = [
        ForeignKey(
            entity = ExamEntity::class,
            parentColumns = ["id"],
            childColumns = ["examId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("examId")]
)
data class StudentResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val examId: Long,
    val studentName: String,
    val studentNumber: String,
    val className: String,
    val detectedAnswers: String,    // JSON: ["A","B","","C",...]
    val correctCount: Int,
    val wrongCount: Int,
    val emptyCount: Int,
    val score: Float,
    val scannedImagePath: String?,
    val scannedAt: Long = System.currentTimeMillis()
)
