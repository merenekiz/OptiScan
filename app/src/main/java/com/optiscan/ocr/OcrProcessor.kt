package com.optiscan.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.optiscan.ocr.models.StudentInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Extracts student information (name, number, class) from specific
 * regions of the warped 800×1100 answer sheet using ML Kit Text Recognition.
 *
 * Form layout (FormPdfGenerator coordinate space):
 *   - Ad Soyad label at x=30, box from (110,76) to (770,106) — text written inside
 *   - Öğrenci No label at x=30, box from (120,116) to (~454,146) — text written inside
 *   - Şube label, box from (~500,116) to (770,146) — text written inside
 *
 * Crops are slightly padded to capture handwritten text that may extend beyond boxes.
 * Each crop is preprocessed (grayscale + contrast boost + upscale) before OCR.
 */
@Singleton
class OcrProcessor @Inject constructor() {

    companion object {
        private const val TAG = "OcrProcessor"

        // Ad Soyad — inside name box (box is 110,76 to 770,106)
        // Start past label "Ad Soyad:" which is at x=30..~110
        private const val NAME_X1 = 108; private const val NAME_Y1 = 70
        private const val NAME_X2 = 775; private const val NAME_Y2 = 112

        // Öğrenci No — inside number box (box is 120,116 to ~454,146)
        // Start past label "Öğrenci No:" which ends at ~120
        private const val NO_X1 = 118; private const val NO_Y1 = 110
        private const val NO_X2 = 460; private const val NO_Y2 = 152

        // Şube — inside sube box (box is ~540,116 to 770,146)
        // Start past label "Şube:" which ends at ~540
        private const val SUBE_X1 = 505; private const val SUBE_Y1 = 110
        private const val SUBE_X2 = 775; private const val SUBE_Y2 = 152

        // Common OCR misreads for digits
        private val DIGIT_SUBSTITUTIONS = mapOf(
            'O' to '0', 'o' to '0',
            'I' to '1', 'l' to '1', 'i' to '1', '|' to '1',
            'Z' to '2', 'z' to '2',
            'S' to '5', 's' to '5',
            'G' to '6', 'g' to '9',
            'B' to '8', 'b' to '6',
            'q' to '9', 'D' to '0',
        )
    }

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun extractStudentInfo(warpedBitmap: Bitmap): StudentInfo =
        withContext(Dispatchers.Default) {
            try {
                val w = warpedBitmap.width
                val h = warpedBitmap.height

                val nameText = cropPreprocessAndRecognize(warpedBitmap, NAME_X1, NAME_Y1, NAME_X2, NAME_Y2, w, h)
                val noText = cropPreprocessAndRecognize(warpedBitmap, NO_X1, NO_Y1, NO_X2, NO_Y2, w, h)
                val subeText = cropPreprocessAndRecognize(warpedBitmap, SUBE_X1, SUBE_Y1, SUBE_X2, SUBE_Y2, w, h)

                Log.d(TAG, "OCR raw - name='$nameText' no='$noText' sube='$subeText'")

                val name = cleanName(nameText)
                val number = cleanNumber(noText)
                val sube = cleanClass(subeText)

                Log.d(TAG, "OCR cleaned - name='$name' no='$number' sube='$sube'")

                StudentInfo(
                    name = name,
                    studentNumber = number,
                    className = sube,
                    nameConfidence = if (name.isNotBlank()) 0.85f else 0.0f,
                    numberConfidence = if (number.isNotBlank()) 0.90f else 0.0f
                )

            } catch (e: Exception) {
                Log.e(TAG, "OCR failed: ${e.message}", e)
                StudentInfo.EMPTY
            }
        }

    /**
     * Crops a region, preprocesses it (grayscale + contrast + upscale 3x), then runs OCR.
     */
    private suspend fun cropPreprocessAndRecognize(
        bitmap: Bitmap,
        x1: Int, y1: Int, x2: Int, y2: Int,
        bitmapW: Int, bitmapH: Int
    ): String {
        val scaleX = bitmapW / 800f
        val scaleY = bitmapH / 1100f

        val cx1 = (x1 * scaleX).toInt().coerceIn(0, bitmapW - 1)
        val cy1 = (y1 * scaleY).toInt().coerceIn(0, bitmapH - 1)
        val cx2 = (x2 * scaleX).toInt().coerceIn(cx1 + 1, bitmapW)
        val cy2 = (y2 * scaleY).toInt().coerceIn(cy1 + 1, bitmapH)

        val cropped = Bitmap.createBitmap(bitmap, cx1, cy1, cx2 - cx1, cy2 - cy1)

        // Preprocess: convert to high-contrast grayscale and upscale
        val processed = preprocessForOcr(cropped)
        cropped.recycle()

        val text = recognizeText(processed)
        processed.recycle()
        return text ?: ""
    }

    /**
     * Converts to grayscale, boosts contrast, and upscales 3x for better ML Kit accuracy.
     */
    private fun preprocessForOcr(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height

        // Step 1: Convert to high-contrast grayscale
        val grayBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(grayBitmap)
        val paint = Paint().apply {
            // High contrast grayscale matrix
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply {
                setSaturation(0f)
                // Boost contrast: scale by 1.8, offset by -100
                val contrastArray = floatArrayOf(
                    1.8f, 0f, 0f, 0f, -100f,
                    0f, 1.8f, 0f, 0f, -100f,
                    0f, 0f, 1.8f, 0f, -100f,
                    0f, 0f, 0f, 1f, 0f
                )
                postConcat(ColorMatrix(contrastArray))
            })
        }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        // Step 2: Upscale 3x for better OCR of small text
        val scaledW = (w * 3).coerceAtMost(2400)
        val scaledH = (h * 3).coerceAtMost(600)
        val upscaled = Bitmap.createScaledBitmap(grayBitmap, scaledW, scaledH, true)
        grayBitmap.recycle()

        return upscaled
    }

    private suspend fun recognizeText(bitmap: Bitmap): String? =
        suspendCancellableCoroutine { cont ->
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    cont.resume(result.text)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "ML Kit OCR error: ${e.message}")
                    cont.resume(null)
                }
        }

    private fun cleanName(raw: String): String {
        var text = raw.trim()
        // Remove label prefix if OCR picked it up
        text = text.replace(Regex("(?i)^ad\\s*soyad\\s*:?\\s*"), "")
        text = text.replace(Regex("(?i)^isim\\s*:?\\s*"), "")
        // Keep only letters, spaces, and Turkish chars
        text = text.replace(Regex("[^A-Za-zÇĞİÖŞÜçğışöüşÂâÎîÛû\\s]"), "").trim()
        text = text.replace(Regex("\\s+"), " ")
        return text
    }

    private fun cleanNumber(raw: String): String {
        var text = raw.trim()
        // Remove label prefixes
        text = text.replace(Regex("(?i)^(?:okul\\s*|öğrenci\\s*)?no\\s*:?\\s*"), "")
        text = text.replace(Regex("(?i)^numara\\s*:?\\s*"), "")

        // Apply digit substitutions for common OCR misreads, then extract ALL digits
        val corrected = StringBuilder()
        for (ch in text) {
            when {
                ch.isDigit() -> corrected.append(ch)
                DIGIT_SUBSTITUTIONS.containsKey(ch) -> corrected.append(DIGIT_SUBSTITUTIONS[ch])
                // Skip non-digit characters (spaces, dashes, etc.)
            }
        }
        return corrected.toString()
    }

    private fun cleanClass(raw: String): String {
        var text = raw.trim()
        // Remove label prefix — handles Şube/Sube/şube and partial OCR reads
        text = text.replace(Regex("(?i)^[sşŞS]?[uüUÜ]?[bB]?[eE]\\s*:?\\s*"), "")
        text = text.replace(Regex("(?i)^sınıf\\s*:?\\s*"), "")
        text = text.replace(Regex("(?i)^section\\s*:?\\s*"), "")
        return text.trim().take(10)
    }

    fun close() {
        recognizer.close()
    }
}
