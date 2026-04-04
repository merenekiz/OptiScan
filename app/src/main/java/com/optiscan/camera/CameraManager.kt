package com.optiscan.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.optiscan.qr.BarcodeAnalyzer
import com.optiscan.qr.models.ExamMetadata
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class CameraManager @Inject constructor() {

    companion object {
        private const val TAG = "CameraManager"
    }

    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var imageCapture: ImageCapture? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var barcodeAnalyzer: BarcodeAnalyzer? = null
    private var isBound = false

    fun startCamera(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onQrDetected: ((ExamMetadata) -> Unit)? = null
    ) {
        if (isBound) return

        if (cameraExecutor.isShutdown) {
            cameraExecutor = Executors.newSingleThreadExecutor()
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider
                bindCameraUseCases(provider, lifecycleOwner, previewView, onQrDetected, context)
            } catch (e: Exception) {
                Log.e(TAG, "Camera provider error: ${e.message}", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindCameraUseCases(
        provider: ProcessCameraProvider,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onQrDetected: ((ExamMetadata) -> Unit)?,
        context: Context
    ) {
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        val preview = Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_16_9)
            .build()
            .also { it.setSurfaceProvider(previewView.surfaceProvider) }

        // Use JPEG output for reliable decoding
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setTargetAspectRatio(AspectRatio.RATIO_16_9)
            .setFlashMode(ImageCapture.FLASH_MODE_AUTO)
            .build()

        val useCases = mutableListOf<UseCase>(preview, imageCapture!!)

        if (onQrDetected != null) {
            barcodeAnalyzer = BarcodeAnalyzer(onQrDetected)
            imageAnalysis = ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(cameraExecutor, barcodeAnalyzer!!) }
            useCases.add(imageAnalysis!!)
        }

        try {
            provider.unbindAll()
            isBound = false
            provider.bindToLifecycle(lifecycleOwner, cameraSelector, *useCases.toTypedArray())
            isBound = true
            Log.d(TAG, "Camera bound successfully")
        } catch (e: Exception) {
            isBound = false
            Log.e(TAG, "Use case binding failed: ${e.message}", e)
        }
    }

    suspend fun captureImage(context: Context): Bitmap? =
        suspendCancellableCoroutine { cont ->
            val capture = imageCapture ?: run {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }

            capture.takePicture(
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageCapturedCallback() {
                    @androidx.camera.core.ExperimentalGetImage
                    override fun onCaptureSuccess(image: ImageProxy) {
                        try {
                            val bitmap = imageProxyToBitmap(image)
                            image.close()
                            cont.resume(bitmap)
                        } catch (e: Exception) {
                            Log.e(TAG, "Bitmap conversion failed: ${e.message}", e)
                            image.close()
                            cont.resume(null)
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.e(TAG, "Capture error: ${exception.message}", exception)
                        cont.resume(null)
                    }
                }
            )
        }

    @androidx.camera.core.ExperimentalGetImage
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        val format = imageProxy.format

        // Try JPEG first (most common for ImageCapture)
        if (format == ImageFormat.JPEG) {
            val buffer = imageProxy.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            return bitmap?.applyRotation(imageProxy.imageInfo.rotationDegrees)
        }

        // YUV_420_888 fallback (some devices)
        if (format == ImageFormat.YUV_420_888) {
            val yBuffer = imageProxy.planes[0].buffer
            val uBuffer = imageProxy.planes[1].buffer
            val vBuffer = imageProxy.planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)
            // NV21: Y then VU interleaved
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            val yuvImage = YuvImage(
                nv21, ImageFormat.NV21,
                imageProxy.width, imageProxy.height, null
            )
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(
                Rect(0, 0, imageProxy.width, imageProxy.height),
                90, out
            )
            val jpegBytes = out.toByteArray()
            val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
            return bitmap?.applyRotation(imageProxy.imageInfo.rotationDegrees)
        }

        // Last resort: try Android Image API
        val image = imageProxy.image
        if (image != null) {
            val buffer = imageProxy.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            return bitmap?.applyRotation(imageProxy.imageInfo.rotationDegrees)
        }

        Log.e(TAG, "Unsupported image format: $format")
        return null
    }

    private fun Bitmap.applyRotation(degrees: Int): Bitmap {
        if (degrees == 0) return this
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        val rotated = Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
        if (rotated != this) recycle()
        return rotated
    }

    fun enableQrScan() {
        barcodeAnalyzer?.reset()
    }

    fun disableQrScan() {
        imageAnalysis?.clearAnalyzer()
    }

    fun shutdown() {
        cameraProvider?.unbindAll()
        isBound = false
        if (!cameraExecutor.isShutdown) {
            cameraExecutor.shutdown()
        }
    }
}
