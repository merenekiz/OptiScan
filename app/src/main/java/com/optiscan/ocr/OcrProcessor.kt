package com.optiscan.ocr

import android.graphics.Bitmap
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
 * Form layout (BubbleDetector coordinate space):
 *   - Title: y=35-70 (NOT student info — skip this)
 *   - Ad Soyad box: y=76-106, x=110-770
 *   - Öğrenci No box: y=116-146, x=120-~454
 *   - Şube box:     y=116-146, x=~514-770
 */
@Singleton
class OcrProcessor @Inject constructor() {

    companion object {
        private const val TAG = "OcrProcessor"

        // Region coordinates in 800×1100 warped space
        // Form layout: Name box (110,76)-(770,106), No box (120,116)-(~454,146), Sube box (~514,116)-(770,146)
        // Crop inside the boxes with padding
        // Ad Soyad — inside name box
        private const val NAME_X1 = 105; private const val NAME_Y1 = 72
        private const val NAME_X2 = 775; private const val NAME_Y2 = 112

        // Öğrenci No — inside number box
        private const val NO_X1 = 110; private const val NO_Y1 = 112
        private const val NO_X2 = 470; private const val NO_Y2 = 152

        // Şube — inside sube box
        private const val SUBE_X1 = 500; private const val SUBE_Y1 = 112
        private const val SUBE_X2 = 775; private const val SUBE_Y2 = 152
    }

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun extractStudentInfo(warpedBitmap: Bitmap): StudentInfo =
        withContext(Dispatchers.Default) {
            try {
                val w = warpedBitmap.width
                val h = warpedBitmap.height

                // Extract each field from its specific region
                val nameText = cropAndRecognize(warpedBitmap, NAME_X1, NAME_Y1, NAME_X2, NAME_Y2, w, h)
                val noText = cropAndRecognize(warpedBitmap, NO_X1, NO_Y1, NO_X2, NO_Y2, w, h)
                val subeText = cropAndRecognize(warpedBitmap, SUBE_X1, SUBE_Y1, SUBE_X2, SUBE_Y2, w, h)

                Log.d(TAG, "OCR regions - name='$nameText' no='$noText' sube='$subeText'")

                val name = cleanName(nameText)
                val number = cleanNumber(noText)
                val sube = cleanClass(subeText)

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
     * Crops a specific region from the warped bitmap and runs OCR.
     * Coordinates are in 800×1100 space, scaled to actual bitmap dimensions.
     */
    private suspend fun cropAndRecognize(
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
        val text = recognizeText(cropped)
        cropped.recycle()
        return text ?: ""
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

    /**
     * Cleans up OCR name text — removes label prefixes and non-letter chars.
     */
    private fun cleanName(raw: String): String {
        var text = raw.trim()
        // Remove label prefix if OCR picked it up
        text = text.replace(Regex("(?i)^ad\\s*soyad\\s*:?\\s*"), "")
        text = text.replace(Regex("(?i)^isim\\s*:?\\s*"), "")
        // Keep only letters, spaces, and Turkish chars
        text = text.replace(Regex("[^A-Za-zÇĞİÖŞÜçğışöüşÂâÎîÛû\\s]"), "").trim()
        // Collapse multiple spaces
        text = text.replace(Regex("\\s+"), " ")
        return text
    }

    private fun cleanNumber(raw: String): String {
        var text = raw.trim()
        text = text.replace(Regex("(?i)^(?:okul\\s*|öğrenci\\s*)?no\\s*:?\\s*"), "")
        text = text.replace(Regex("(?i)^numara\\s*:?\\s*"), "")
        // Extract digits
        val digits = Regex("\\d+").find(text)?.value ?: ""
        return digits
    }

    private fun cleanClass(raw: String): String {
        var text = raw.trim()
        // Remove label prefix — handles Şube/Sube/şube and partial OCR reads like "be:" or "e:"
        text = text.replace(Regex("(?i)^[sşŞS]?[uüUÜ]?[bB]?[eE]\\s*:?\\s*"), "")
        text = text.replace(Regex("(?i)^sınıf\\s*:?\\s*"), "")
        text = text.replace(Regex("(?i)^section\\s*:?\\s*"), "")
        return text.trim().take(10)
    }

    fun close() {
        recognizer.close()
    }
}
