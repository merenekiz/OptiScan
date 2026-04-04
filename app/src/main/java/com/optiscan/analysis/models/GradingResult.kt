package com.optiscan.analysis.models

import android.graphics.Bitmap
import com.optiscan.ocr.models.StudentInfo

enum class AnswerResult { CORRECT, WRONG, EMPTY }

data class QuestionResult(
    val questionIndex: Int,
    val detectedAnswer: String,
    val correctAnswer: String,
    val result: AnswerResult
)

data class GradingResult(
    val studentInfo: StudentInfo,
    val questionResults: List<QuestionResult>,
    val correctCount: Int,
    val wrongCount: Int,
    val emptyCount: Int,
    val score: Float,
    val maxScore: Float,
    val percentage: Float,
    val gradedBitmap: Bitmap?,    // annotated image with color overlays
    val examId: Long
) {
    companion object {
        fun empty(examId: Long) = GradingResult(
            studentInfo = StudentInfo.EMPTY,
            questionResults = emptyList(),
            correctCount = 0,
            wrongCount = 0,
            emptyCount = 0,
            score = 0f,
            maxScore = 0f,
            percentage = 0f,
            gradedBitmap = null,
            examId = examId
        )
    }
}
