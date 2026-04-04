package com.optiscan.qr.models

import com.google.gson.Gson

data class ExamMetadata(
    val examId: String,
    val questionCount: Int,
    val correctPoint: Float,
    val wrongPenalty: Float,
    val title: String = "",
    val subject: String = "",
    val version: Int = 1
) {
    companion object {
        private val gson = Gson()

        fun fromJson(json: String): ExamMetadata? = try {
            gson.fromJson(json, ExamMetadata::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun toJson(): String = gson.toJson(this)

    fun isValid(): Boolean =
        examId.isNotBlank() && questionCount > 0 && correctPoint > 0
}
