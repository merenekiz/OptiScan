package com.optiscan.analysis

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import com.optiscan.analysis.models.AnswerResult
import com.optiscan.analysis.models.GradingResult
import com.optiscan.analysis.models.QuestionResult
import com.optiscan.analysis.models.ScoringConfig
import com.optiscan.ocr.models.StudentInfo
import com.optiscan.processing.BubbleDetector
import com.optiscan.processing.models.DetectedBubble
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Compares detected answers against the answer key and computes the score.
 * Also draws color-coded overlays (green/red/gray) on the warped sheet image.
 */
@Singleton
class GradingEngine @Inject constructor() {

    fun grade(
        detectedAnswers: List<String>,
        bubbles: List<DetectedBubble>,
        studentInfo: StudentInfo,
        config: ScoringConfig,
        warpedBitmap: Bitmap?,
        examId: Long
    ): GradingResult {

        val questionResults = mutableListOf<QuestionResult>()
        var correct = 0; var wrong = 0; var empty = 0

        for (i in 0 until config.questionCount) {
            val detected = detectedAnswers.getOrElse(i) { "" }
            val answer = config.answerKey.getOrElse(i) { "" }

            val result = when {
                detected.isBlank() -> AnswerResult.EMPTY
                answer.isBlank() -> AnswerResult.EMPTY   // no key = can't grade, treat as empty
                detected == answer -> AnswerResult.CORRECT
                else -> AnswerResult.WRONG
            }

            when (result) {
                AnswerResult.CORRECT -> correct++
                AnswerResult.WRONG -> wrong++
                AnswerResult.EMPTY -> empty++
            }

            questionResults.add(
                QuestionResult(
                    questionIndex = i,
                    detectedAnswer = detected,
                    correctAnswer = answer,
                    result = result
                )
            )
        }

        // score = (correct * correctPt) - (wrong * wrongPenalty), min = 0
        val rawScore = (correct * config.correctPoint) - (wrong * config.wrongPenalty)
        val score = max(rawScore, 0f)
        val maxScore = config.maxScore()
        val percentage = if (maxScore > 0) (score / maxScore) * 100f else 0f

        // Draw graded overlay (skip if nothing to draw)
        val gradedBitmap = if (warpedBitmap != null && questionResults.isNotEmpty()) {
            drawGradedOverlay(warpedBitmap, bubbles, questionResults)
        } else {
            warpedBitmap
        }

        return GradingResult(
            studentInfo = studentInfo,
            questionResults = questionResults,
            correctCount = correct,
            wrongCount = wrong,
            emptyCount = empty,
            score = score,
            maxScore = maxScore,
            percentage = percentage,
            gradedBitmap = gradedBitmap,
            examId = examId
        )
    }

    private fun drawGradedOverlay(
        base: Bitmap,
        bubbles: List<DetectedBubble>,
        questionResults: List<QuestionResult>
    ): Bitmap {
        // Software layer bitmap — guarantees paint effects work (no HW canvas issues)
        val result = base.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 2.5f
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER
        }

        val optionLabels = listOf("A", "B", "C", "D", "E")
        val r = BubbleDetector.BUBBLE_DIAMETER / 2f

        val greenFill = android.graphics.Color.argb(100, 76, 175, 80)
        val greenRing = android.graphics.Color.argb(210, 76, 175, 80)

        val redFill = android.graphics.Color.argb(100, 244, 67, 54)
        val redRing = android.graphics.Color.argb(210, 244, 67, 54)

        for (qResult in questionResults) {
            if (qResult.detectedAnswer.isBlank()) continue

            val qBubbles = bubbles.filter { it.questionIndex == qResult.questionIndex }

            for (bubble in qBubbles) {
                val label = optionLabels.getOrElse(bubble.optionIndex) { "" }
                if (qResult.detectedAnswer != label) continue

                val cx = bubble.centerX.toFloat()
                val cy = bubble.centerY.toFloat()
                val isCorrect = qResult.result == AnswerResult.CORRECT

                // Layer 1: semi-transparent fill
                fillPaint.color = if (isCorrect) greenFill else redFill
                canvas.drawCircle(cx, cy, r + 2f, fillPaint)

                // Layer 2: crisp ring stroke
                ringPaint.color = if (isCorrect) greenRing else redRing
                canvas.drawCircle(cx, cy, r, ringPaint)

                // Layer 3: letter label
                canvas.drawText(label, cx, cy + textPaint.textSize / 3, textPaint)
            }
        }

        return result
    }
}
