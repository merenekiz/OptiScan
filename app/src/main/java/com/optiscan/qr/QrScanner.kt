package com.optiscan.qr

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.optiscan.qr.models.ExamMetadata
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Scans the QR code printed on the answer sheet to extract exam configuration.
 * Expected JSON format:
 * {
 *   "examId": "MATH-2024-01",
 *   "questionCount": 40,
 *   "correctPoint": 2.5,
 *   "wrongPenalty": 0.833,
 *   "title": "Matematik Sınavı",
 *   "subject": "Matematik"
 * }
 */
@Singleton
class QrScanner @Inject constructor() {

    companion object {
        private const val TAG = "QrScanner"
    }

    private val scanner = BarcodeScanning.getClient()

    data class QrScanResult(
        val metadata: ExamMetadata?,
        val rawValue: String?,
        val isSuccess: Boolean
    )

    suspend fun scanFromBitmap(bitmap: Bitmap): QrScanResult {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val barcodes = scanImage(image)

            val qrBarcode = barcodes.firstOrNull { barcode ->
                barcode.format == Barcode.FORMAT_QR_CODE ||
                barcode.format == Barcode.FORMAT_DATA_MATRIX
            } ?: barcodes.firstOrNull()

            if (qrBarcode?.rawValue == null) {
                Log.w(TAG, "No QR code found in image")
                return QrScanResult(null, null, false)
            }

            val raw = qrBarcode.rawValue!!
            Log.d(TAG, "QR raw value: $raw")

            val metadata = ExamMetadata.fromJson(raw)

            if (metadata != null && metadata.isValid()) {
                QrScanResult(metadata, raw, true)
            } else {
                Log.w(TAG, "QR value not valid ExamMetadata JSON: $raw")
                QrScanResult(null, raw, false)
            }

        } catch (e: Exception) {
            Log.e(TAG, "QR scan error: ${e.message}", e)
            QrScanResult(null, null, false)
        }
    }

    private suspend fun scanImage(image: InputImage): List<Barcode> =
        suspendCancellableCoroutine { cont ->
            scanner.process(image)
                .addOnSuccessListener { barcodes -> cont.resume(barcodes) }
                .addOnFailureListener { cont.resume(emptyList()) }
        }

    fun close() {
        scanner.close()
    }
}
