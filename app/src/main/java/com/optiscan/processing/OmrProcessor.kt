package com.optiscan.processing

import android.graphics.Bitmap
import android.util.Log
import com.optiscan.processing.models.OmrResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Mat
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Main OMR pipeline orchestrator.
 * Input: raw Bitmap from camera
 * Output: OmrResult with detected answers + annotated image
 */
@Singleton
class OmrProcessor @Inject constructor(
    private val perspectiveTransformer: PerspectiveTransformer,
    private val bubbleDetector: BubbleDetector
) {

    companion object {
        private const val TAG = "OmrProcessor"
    }

    suspend fun process(bitmap: Bitmap, questionCount: Int): OmrResult =
        withContext(Dispatchers.Default) {
            try {
                // Step 1: Perspective correction
                val transformResult = perspectiveTransformer.transform(bitmap)

                if (!transformResult.isSuccess || transformResult.bitmap == null) {
                    return@withContext OmrResult(
                        detectedAnswers = emptyList(),
                        bubbles = emptyList(),
                        warpedBitmap = null,
                        isSuccess = false,
                        errorMessage = "Perspektif düzeltme başarısız"
                    )
                }

                val warpedBitmap = transformResult.bitmap

                // Step 2: Convert to Mat for OpenCV processing
                val sheetMat = Mat()
                Utils.bitmapToMat(warpedBitmap, sheetMat)

                // Step 3: Detect bubbles
                val bubbles = bubbleDetector.detectBubbles(sheetMat, questionCount)
                sheetMat.release()

                if (bubbles.isEmpty()) {
                    return@withContext OmrResult(
                        detectedAnswers = emptyList(),
                        bubbles = emptyList(),
                        warpedBitmap = warpedBitmap,
                        isSuccess = false,
                        errorMessage = "Baloncuk algılanamadı",
                        sheetConfidence = transformResult.confidence
                    )
                }

                // Step 4: Resolve answers
                val answers = bubbleDetector.resolveAnswers(bubbles, questionCount)

                OmrResult(
                    detectedAnswers = answers,
                    bubbles = bubbles,
                    warpedBitmap = warpedBitmap,
                    isSuccess = true,
                    sheetConfidence = transformResult.confidence
                )

            } catch (e: Exception) {
                Log.e(TAG, "OMR processing error: ${e.message}", e)
                OmrResult(
                    detectedAnswers = emptyList(),
                    bubbles = emptyList(),
                    warpedBitmap = null,
                    isSuccess = false,
                    errorMessage = e.message ?: "Bilinmeyen hata"
                )
            }
        }

}
