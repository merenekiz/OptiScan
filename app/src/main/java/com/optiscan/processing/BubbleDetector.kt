package com.optiscan.processing

import android.util.Log
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import com.optiscan.processing.models.BubbleState
import com.optiscan.processing.models.DetectedBubble
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detects filled bubbles in a perspective-corrected answer sheet image.
 *
 * The bubble grid dynamically fills the available area based on question count.
 * Sheet is always 800×1100 px after warp.
 * Header occupies top ~200px, grid fills the rest.
 */
@Singleton
class BubbleDetector @Inject constructor() {

    companion object {
        private const val TAG = "BubbleDetector"

        // Sheet dimensions after warp
        const val SHEET_WIDTH = 800
        const val SHEET_HEIGHT = 1100

        // Fixed layout anchors
        const val GRID_START_Y = 240       // where bubble grid begins
        const val GRID_MARGIN_X = 30       // left/right margin
        const val GRID_BOTTOM_MARGIN = 30  // bottom margin
        const val NUM_OPTIONS = 5
        const val BUBBLE_DIAMETER = 22     // bubble circle diameter (for detection)

        // If >maxSingleColRows, use two columns
        const val MAX_SINGLE_COL_ROWS = 30

        // Fill thresholds
        private const val FILL_THRESHOLD = 0.40f
        private const val AMBIGUOUS_THRESHOLD = 0.20f
    }

    /**
     * Compute grid layout for a given question count.
     * Returns all the spacing/offset values needed for detection and drawing.
     */
    data class GridLayout(
        val useSecondCol: Boolean,
        val col1Count: Int,
        val col2Count: Int,
        val rowHeight: Int,       // px per row
        val optSpacing: Int,      // px between option centers
        val col1X: Int,           // x start of column 1 bubbles
        val col2X: Int,           // x start of column 2 bubbles (0 if single col)
        val questionNumX1: Int,   // x for question numbers col 1
        val questionNumX2: Int    // x for question numbers col 2
    )

    fun computeLayout(questionCount: Int): GridLayout {
        val safeCount = questionCount.coerceAtLeast(1)
        val availH = SHEET_HEIGHT - GRID_START_Y - GRID_BOTTOM_MARGIN  // ~870
        val availW = SHEET_WIDTH - 2 * GRID_MARGIN_X                    // ~740

        val useSecondCol = safeCount > MAX_SINGLE_COL_ROWS
        val col1Count: Int
        val col2Count: Int

        if (useSecondCol) {
            col1Count = (safeCount + 1) / 2  // e.g. 35 -> 18
            col2Count = safeCount - col1Count // e.g. 35 -> 17
        } else {
            col1Count = safeCount
            col2Count = 0
        }

        val maxRows = maxOf(col1Count, col2Count)
        // Row height: fill available height, cap at 36 for readability
        val rowHeight = minOf(availH / maxRows, 36)

        if (useSecondCol) {
            // Two columns: each gets half width minus gap
            val colGap = 40
            val colWidth = (availW - colGap) / 2
            // 5 options in colWidth: spacing = colWidth / 5
            // Leave room for question number (25px)
            val qNumWidth = 28
            val bubbleArea = colWidth - qNumWidth
            val optSpacing = bubbleArea / NUM_OPTIONS

            val col1X = GRID_MARGIN_X + qNumWidth
            val col2X = GRID_MARGIN_X + colWidth + colGap + qNumWidth

            return GridLayout(
                useSecondCol = true,
                col1Count = col1Count, col2Count = col2Count,
                rowHeight = rowHeight, optSpacing = optSpacing,
                col1X = col1X, col2X = col2X,
                questionNumX1 = GRID_MARGIN_X + qNumWidth - 4,
                questionNumX2 = GRID_MARGIN_X + colWidth + colGap + qNumWidth - 4
            )
        } else {
            // Single column: center in available width
            val qNumWidth = 28
            val bubbleArea = availW - qNumWidth
            val optSpacing = bubbleArea / NUM_OPTIONS
            val col1X = GRID_MARGIN_X + qNumWidth

            return GridLayout(
                useSecondCol = false,
                col1Count = col1Count, col2Count = 0,
                rowHeight = rowHeight, optSpacing = optSpacing,
                col1X = col1X, col2X = 0,
                questionNumX1 = GRID_MARGIN_X + qNumWidth - 4,
                questionNumX2 = 0
            )
        }
    }

    fun detectBubbles(sheetMat: Mat, questionCount: Int): List<DetectedBubble> {
        val gray = Mat()
        val thresh = Mat()
        val detectedBubbles = mutableListOf<DetectedBubble>()
        val layout = computeLayout(questionCount)

        try {
            Imgproc.cvtColor(sheetMat, gray, Imgproc.COLOR_RGBA2GRAY)

            Imgproc.adaptiveThreshold(
                gray, thresh, 255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY_INV,
                15, 8.0
            )

            val kernel = Imgproc.getStructuringElement(
                Imgproc.MORPH_ELLIPSE, Size(3.0, 3.0)
            )
            Imgproc.morphologyEx(thresh, thresh, Imgproc.MORPH_OPEN, kernel)
            kernel.release()

            Log.d(TAG, "detectBubbles: qCount=$questionCount, layout=$layout, sheet=${sheetMat.cols()}x${sheetMat.rows()}")

            // Column 1
            for (q in 0 until layout.col1Count) {
                val cy = GRID_START_Y + q * layout.rowHeight + layout.rowHeight / 2
                if (cy + BUBBLE_DIAMETER / 2 >= SHEET_HEIGHT) break

                for (opt in 0 until NUM_OPTIONS) {
                    val cx = layout.col1X + opt * layout.optSpacing + BUBBLE_DIAMETER / 2
                    if (cx + BUBBLE_DIAMETER / 2 >= SHEET_WIDTH) continue

                    val fillPct = measureBubbleFill(thresh, cx, cy, BUBBLE_DIAMETER / 2 - 2)
                    val state = classifyFill(fillPct)


                    detectedBubbles.add(DetectedBubble(q, opt, state, fillPct, cx, cy))
                }
            }

            // Column 2
            if (layout.useSecondCol) {
                for (q in 0 until layout.col2Count) {
                    val globalQ = layout.col1Count + q
                    val cy = GRID_START_Y + q * layout.rowHeight + layout.rowHeight / 2
                    if (cy + BUBBLE_DIAMETER / 2 >= SHEET_HEIGHT) break

                    for (opt in 0 until NUM_OPTIONS) {
                        val cx = layout.col2X + opt * layout.optSpacing + BUBBLE_DIAMETER / 2
                        if (cx + BUBBLE_DIAMETER / 2 >= SHEET_WIDTH) continue

                        val fillPct = measureBubbleFill(thresh, cx, cy, BUBBLE_DIAMETER / 2 - 2)
                        val state = classifyFill(fillPct)


                        detectedBubbles.add(DetectedBubble(globalQ, opt, state, fillPct, cx, cy))
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Bubble detection failed: ${e.message}", e)
        } finally {
            gray.release()
            thresh.release()
        }

        return detectedBubbles
    }

    private fun classifyFill(fillPct: Float) = when {
        fillPct >= FILL_THRESHOLD -> BubbleState.FILLED
        fillPct >= AMBIGUOUS_THRESHOLD -> BubbleState.AMBIGUOUS
        else -> BubbleState.EMPTY
    }

    private fun measureBubbleFill(threshMat: Mat, cx: Int, cy: Int, radius: Int): Float {
        val r = radius.coerceAtLeast(4)
        val x1 = (cx - r).coerceAtLeast(0)
        val y1 = (cy - r).coerceAtLeast(0)
        val x2 = (cx + r).coerceAtMost(threshMat.cols() - 1)
        val y2 = (cy + r).coerceAtMost(threshMat.rows() - 1)

        if (x2 <= x1 || y2 <= y1) return 0f

        val roi = threshMat.submat(Rect(x1, y1, x2 - x1, y2 - y1))
        val mask = Mat.zeros(roi.size(), CvType.CV_8UC1)
        Imgproc.circle(
            mask,
            Point((roi.cols() / 2).toDouble(), (roi.rows() / 2).toDouble()),
            r, Scalar(255.0), -1
        )

        val masked = Mat()
        Core.bitwise_and(roi, mask, masked)

        val darkPixels = Core.countNonZero(masked)
        val totalPixels = Core.countNonZero(mask)

        roi.release()
        mask.release()
        masked.release()

        return if (totalPixels == 0) 0f else darkPixels.toFloat() / totalPixels.toFloat()
    }

    fun resolveAnswers(bubbles: List<DetectedBubble>, questionCount: Int): List<String> {
        val answers = MutableList(questionCount) { "" }
        val optionLabels = listOf("A", "B", "C", "D", "E")

        for (q in 0 until questionCount) {
            val questionBubbles = bubbles.filter { it.questionIndex == q }
            val filledBubbles = questionBubbles.filter { it.state == BubbleState.FILLED }

            when {
                filledBubbles.size == 1 -> {
                    answers[q] = optionLabels.getOrElse(filledBubbles[0].optionIndex) { "" }
                }
                filledBubbles.size > 1 -> {
                    answers[q] = ""
                }
                else -> {
                    val ambiguous = questionBubbles.filter { it.state == BubbleState.AMBIGUOUS }
                    if (ambiguous.size == 1) {
                        answers[q] = optionLabels.getOrElse(ambiguous[0].optionIndex) { "" }
                    } else {
                        answers[q] = ""
                    }
                }
            }
        }

        return answers
    }
}
