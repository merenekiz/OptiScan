package com.optiscan.processing.models

import android.graphics.Bitmap

enum class BubbleState { FILLED, EMPTY, AMBIGUOUS }

data class DetectedBubble(
    val questionIndex: Int,        // 0-based
    val optionIndex: Int,          // 0=A, 1=B, 2=C, 3=D, 4=E
    val state: BubbleState,
    val fillPercentage: Float,     // 0.0 - 1.0
    val centerX: Int,
    val centerY: Int
)

data class OmrResult(
    val detectedAnswers: List<String>,   // index = question, value = "A"/"B"/"C"/"D"/"E"/""
    val bubbles: List<DetectedBubble>,
    val warpedBitmap: Bitmap?,
    val isSuccess: Boolean,
    val errorMessage: String? = null,
    val sheetConfidence: Float = 1f      // 0-1, how confident we are in sheet alignment
)
