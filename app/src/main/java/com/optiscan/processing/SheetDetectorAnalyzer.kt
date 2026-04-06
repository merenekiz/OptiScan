package com.optiscan.processing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * CameraX analyzer that detects a sheet (quadrilateral with ~A4 ratio)
 * in the camera preview. After [REQUIRED_STABLE_FRAMES] consecutive
 * positive detections it fires [onSheetDetected] once and disables itself.
 */
class SheetDetectorAnalyzer(
    private val onSheetDetected: () -> Unit
) : ImageAnalysis.Analyzer {

    companion object {
        private const val TAG = "SheetDetector"
        private const val REQUIRED_STABLE_FRAMES = 4
        private const val EXPECTED_RATIO = 800.0 / 1100.0  // ~0.727
        private const val RATIO_TOLERANCE = 0.22
        private const val MIN_AREA_RATIO = 0.05
        private const val MAX_AREA_RATIO = 0.92
        private const val DOWNSCALE_WIDTH = 320  // process at low res for speed
    }

    private var stableCount = 0
    @Volatile private var enabled = true
    @Volatile private var processing = false

    @androidx.camera.core.ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        if (!enabled || processing) {
            imageProxy.close()
            return
        }
        processing = true

        val bitmap: Bitmap?
        try {
            bitmap = imageProxyToBitmap(imageProxy)
        } finally {
            imageProxy.close()
        }

        try {
            if (bitmap == null) return

            // Downscale for fast processing
            val scale = DOWNSCALE_WIDTH.toFloat() / bitmap.width.coerceAtLeast(1)
            val small = Bitmap.createScaledBitmap(
                bitmap,
                DOWNSCALE_WIDTH,
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                false
            )
            bitmap.recycle()

            val detected = detectSheet(small)
            small.recycle()

            if (detected) {
                stableCount++
                Log.d(TAG, "Sheet detected ($stableCount/$REQUIRED_STABLE_FRAMES)")
                if (stableCount >= REQUIRED_STABLE_FRAMES) {
                    Log.d(TAG, "Sheet stable — triggering capture")
                    enabled = false
                    stableCount = 0
                    onSheetDetected()
                }
            } else {
                // Decay slowly so brief occlusion doesn't reset progress
                if (stableCount > 0) stableCount--
            }
        } catch (e: Exception) {
            Log.w(TAG, "Frame analysis error: ${e.message}")
        } finally {
            processing = false
        }
    }

    private fun detectSheet(bitmap: Bitmap): Boolean {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        val gray = Mat()
        val blurred = Mat()
        val edges = Mat()

        try {
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)

            // Try two threshold strategies
            for (factor in doubleArrayOf(0.5, 0.33)) {
                val median = computeMedian(blurred)
                val lower = max(0.0, factor * median)
                val upper = min(255.0, (2.0 - factor) * median)
                Imgproc.Canny(blurred, edges, lower, upper)

                val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
                Imgproc.dilate(edges, edges, kernel)
                kernel.release()

                val contours = mutableListOf<MatOfPoint>()
                val hierarchy = Mat()
                Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
                hierarchy.release()

                val imageArea = mat.rows().toDouble() * mat.cols().toDouble()
                val sorted = contours.sortedByDescending { Imgproc.contourArea(it) }

                for (contour in sorted) {
                    val area = Imgproc.contourArea(contour)
                    if (area < imageArea * MIN_AREA_RATIO) break
                    if (area > imageArea * MAX_AREA_RATIO) continue

                    val peri = Imgproc.arcLength(MatOfPoint2f(*contour.toArray()), true)
                    val approx = MatOfPoint2f()
                    Imgproc.approxPolyDP(MatOfPoint2f(*contour.toArray()), approx, 0.02 * peri, true)

                    if (approx.rows() == 4) {
                        val pts = approx.toArray()
                        val rect = Imgproc.minAreaRect(MatOfPoint2f(*pts))
                        val w = min(rect.size.width, rect.size.height)
                        val h = max(rect.size.width, rect.size.height)
                        if (h > 0) {
                            val ratio = w / h
                            approx.release()
                            contours.forEach { it.release() }
                            return abs(ratio - EXPECTED_RATIO) < RATIO_TOLERANCE
                        }
                        approx.release()
                    } else {
                        approx.release()
                    }
                }
                contours.forEach { it.release() }
            }
            return false

        } finally {
            mat.release()
            gray.release()
            blurred.release()
            edges.release()
        }
    }

    private fun computeMedian(mat: Mat): Double {
        val hist = Mat()
        val channels = MatOfInt(0)
        val histSize = MatOfInt(256)
        val ranges = MatOfFloat(0f, 256f)
        val mask = Mat()
        Imgproc.calcHist(listOf(mat), channels, mask, hist, histSize, ranges)
        channels.release(); histSize.release(); ranges.release(); mask.release()

        val total = mat.rows() * mat.cols()
        var cum = 0.0
        for (i in 0 until 256) {
            cum += hist.get(i, 0)[0]
            if (cum >= total / 2.0) { hist.release(); return i.toDouble() }
        }
        hist.release()
        return 127.0
    }

    @androidx.camera.core.ExperimentalGetImage
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        val format = imageProxy.format
        if (format == ImageFormat.JPEG) {
            val buffer = imageProxy.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?.applyRotation(imageProxy.imageInfo.rotationDegrees)
        }
        if (format == ImageFormat.YUV_420_888) {
            val yBuffer = imageProxy.planes[0].buffer
            val uBuffer = imageProxy.planes[1].buffer
            val vBuffer = imageProxy.planes[2].buffer
            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()
            val nv21 = ByteArray(ySize + uSize + vSize)
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)
            val yuv = YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
            val out = ByteArrayOutputStream()
            yuv.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 40, out)
            return BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
                ?.applyRotation(imageProxy.imageInfo.rotationDegrees)
        }
        return null
    }

    private fun Bitmap.applyRotation(degrees: Int): Bitmap {
        if (degrees == 0) return this
        val m = Matrix().apply { postRotate(degrees.toFloat()) }
        val rotated = Bitmap.createBitmap(this, 0, 0, width, height, m, true)
        if (rotated != this) recycle()
        return rotated
    }

    fun reset() {
        stableCount = 0
        enabled = true
    }

    fun disable() {
        enabled = false
    }
}
