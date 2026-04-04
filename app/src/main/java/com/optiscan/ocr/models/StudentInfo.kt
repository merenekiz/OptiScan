package com.optiscan.ocr.models

data class StudentInfo(
    val name: String = "",
    val studentNumber: String = "",
    val className: String = "",
    val nameConfidence: Float = 0f,
    val numberConfidence: Float = 0f
) {
    fun isValid(): Boolean = name.isNotBlank() || studentNumber.isNotBlank()

    fun hasLowConfidence(): Boolean = nameConfidence < 0.6f || numberConfidence < 0.6f

    companion object {
        val EMPTY = StudentInfo(name = "Bilinmiyor", studentNumber = "---", className = "---")
    }
}
