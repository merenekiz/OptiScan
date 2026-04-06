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
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * CameraX ImageAnalysis.Analyzer that runs on every preview frame
 * and checks whether a valid answer sheet (quadrilateral) is visible.
 * When a stable detection is confirmed over several consecutive frames,
 * it calls [onSheetDetected].
 *
 * Runs at low resolution for speed — this is only a trigger, not the actual capture.
 */
class SheetDetectorAnalyzer(
    private val onSheetDetected: () -> Unit
) : ImageAnalysis.Analyzer {

    companion object {
        private const val TAG = "SheetDetector"
        private const val REQUIRED_STABLE_FRAMES = 5
        private const val EXPECTED_RATIO = 800.0 / 1100.0  // ~0.727
        private const val RATIO_TOLERANCE = 0.20
        private const val MIN_AREA_RATIO = 0.03  // small printed sheets (scaled down from A4)
        private const val MAX_AREA_RATIO = 0.92
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

        try {
            val bitmap = imageProxyToBitmap(imageProxy)
            imageProxy.close()

            if (bitmap == null) {
                processing = false
                return
            }

            val detected = detectSheet(bitmap)
            bitmap.recycle()

            if (detected) {
                stableCount++
                if (stableCount >= REQUIRED_STABLE_FRAMES) {
                    Log.d(TAG, "Sheet stable for $REQUIRED_STABLE_FRAMES frames — triggering capture")
                    enabled = false
                    stableCount = 0
                    onSheetDetected()
                }
            } else {
                stableCount = max(0, stableCount - 1)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Frame analysis error: ${e.message}")
            imageProxy.close()
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

            val median = computeMedian(blurred)
            val lower = max(0.0, 0.5 * median)
            val upper = min(255.0, 1.5 * median)
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
                    // Check aspect ratio matches A4/sheet
                    val rect = Imgproc.minAreaRect(MatOfPoint2f(*pts))
                    val w = min(rect.size.width, rect.size.height)
                    val h = max(rect.size.width, rect.size.height)
                    val ratio = w / h

                    approx.release()
                    contours.forEach { it.release() }

                    if (abs(ratio - EXPECTED_RATIO) < RATIO_TOLERANCE) {
                        return true
                    }
                    return false
                }
                approx.release()
            }
            contours.forEach { it.release() }
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
        Imgproc.calcHist(listOf(mat), MatOfInt(0), Mat(), hist, MatOfInt(256), MatOfFloat(0f, 256f))
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
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            return bmp?.applyRotation(imageProxy.imageInfo.rotationDegrees)
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
            yuv.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 50, out)
            val bmp = BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
            return bmp?.applyRotation(imageProxy.imageInfo.rotationDegrees)
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
