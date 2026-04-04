package com.optiscan.qr

import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.optiscan.qr.models.ExamMetadata

/**
 * CameraX ImageAnalysis.Analyzer for real-time QR code detection
 * during the live camera preview phase.
 */
class BarcodeAnalyzer(
    private val onQrDetected: (ExamMetadata) -> Unit,
    private val onError: (String) -> Unit = {}
) : ImageAnalysis.Analyzer {

    companion object {
        private const val TAG = "BarcodeAnalyzer"
    }

    private val scanner = BarcodeScanning.getClient()
    private var lastDetectedId: String? = null

    @androidx.camera.core.ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                for (barcode in barcodes) {
                    if (barcode.format == Barcode.FORMAT_QR_CODE) {
                        val raw = barcode.rawValue ?: continue
                        // Deduplicate
                        if (raw == lastDetectedId) continue
                        lastDetectedId = raw

                        val metadata = ExamMetadata.fromJson(raw)
                        if (metadata != null && metadata.isValid()) {
                            Log.d(TAG, "Valid exam QR detected: ${metadata.examId}")
                            onQrDetected(metadata)
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Barcode analysis error: ${e.message}")
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    fun reset() {
        lastDetectedId = null
    }
}
