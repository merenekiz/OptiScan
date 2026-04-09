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
import kotlin.math.sqrt

/**
 * CameraX analyzer that detects the form's 4 black alignment markers
 * in the camera preview. After [REQUIRED_STABLE_FRAMES] consecutive
 * positive detections it fires [onSheetDetected] once and disables itself.
 *
 * Also exposes [detectionProgress] (0..REQUIRED_STABLE_FRAMES) for UI feedback.
 */
class SheetDetectorAnalyzer(
    private val onSheetDetected: () -> Unit
) : ImageAnalysis.Analyzer {

    companion object {
        private const val TAG = "SheetDetector"
        private const val REQUIRED_STABLE_FRAMES = 3
        private const val DOWNSCALE_WIDTH = 400
        // Marker detection params (relative to downscaled image)
        private const val MIN_MARKER_AREA_RATIO = 0.0001
        private const val MAX_MARKER_AREA_RATIO = 0.02
        // Quad formed by markers should cover a reasonable portion of the image
        private const val MIN_QUAD_AREA_RATIO = 0.08
        private const val MAX_QUAD_AREA_RATIO = 0.95
        // Expected aspect ratio of the form (width/height)
        private const val EXPECTED_RATIO = 800.0 / 1100.0  // ~0.727
        private const val RATIO_TOLERANCE = 0.15
    }

    private var stableCount = 0
    @Volatile private var enabled = true
    @Volatile private var processing = false

    /** Observable detection progress for UI (0 = nothing, REQUIRED_STABLE_FRAMES = about to trigger) */
    @Volatile var detectionProgress: Int = 0
        private set

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

            val scale = DOWNSCALE_WIDTH.toFloat() / bitmap.width.coerceAtLeast(1)
            val small = Bitmap.createScaledBitmap(
                bitmap,
                DOWNSCALE_WIDTH,
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                false
            )
            bitmap.recycle()

            val detected = detectMarkers(small)
            small.recycle()

            if (detected) {
                stableCount++
                detectionProgress = stableCount.coerceAtMost(REQUIRED_STABLE_FRAMES)
                Log.d(TAG, "Markers detected ($stableCount/$REQUIRED_STABLE_FRAMES)")
                if (stableCount >= REQUIRED_STABLE_FRAMES) {
                    Log.d(TAG, "Sheet stable — triggering capture")
                    enabled = false
                    stableCount = 0
                    detectionProgress = REQUIRED_STABLE_FRAMES
                    onSheetDetected()
                }
            } else {
                if (stableCount > 0) stableCount--
                detectionProgress = stableCount
            }
        } catch (e: Exception) {
            Log.w(TAG, "Frame analysis error: ${e.message}")
        } finally {
            processing = false
        }
    }

    /**
     * Detects 4 alignment markers (filled black squares) and validates
     * they form a quadrilateral with roughly the expected form aspect ratio.
     */
    private fun detectMarkers(bitmap: Bitmap): Boolean {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        val gray = Mat()
        val thresh = Mat()

        try {
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)

            val imageArea = mat.rows().toDouble() * mat.cols().toDouble()
            val minMarkerArea = imageArea * MIN_MARKER_AREA_RATIO
            val maxMarkerArea = imageArea * MAX_MARKER_AREA_RATIO

            // Try multiple binarization strategies
            val strategies = listOf(
                // Strategy 1: Otsu threshold
                { src: Mat, dst: Mat ->
                    Imgproc.threshold(src, dst, 0.0, 255.0,
                        Imgproc.THRESH_BINARY_INV or Imgproc.THRESH_OTSU)
                },
                // Strategy 2: Adaptive threshold (small block)
                { src: Mat, dst: Mat ->
                    Imgproc.adaptiveThreshold(src, dst, 255.0,
                        Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY_INV,
                        21, 10.0)
                },
                // Strategy 3: Adaptive threshold (large block)
                { src: Mat, dst: Mat ->
                    Imgproc.adaptiveThreshold(src, dst, 255.0,
                        Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY_INV,
                        51, 12.0)
                }
            )

            for (strategy in strategies) {
                strategy(gray, thresh)

                val corners = findMarkerCorners(thresh, mat.cols(), mat.rows(),
                    minMarkerArea, maxMarkerArea, imageArea)
                if (corners != null) {
                    // Validate aspect ratio of the quadrilateral
                    val w = ((distance(corners[0], corners[1]) + distance(corners[3], corners[2])) / 2.0)
                    val h = ((distance(corners[0], corners[3]) + distance(corners[1], corners[2])) / 2.0)
                    if (h > 0) {
                        val ratio = w / h
                        if (abs(ratio - EXPECTED_RATIO) < RATIO_TOLERANCE) {
                            return true
                        }
                    }
                }
            }

            return false
        } finally {
            mat.release()
            gray.release()
            thresh.release()
        }
    }

    private fun findMarkerCorners(
        thresh: Mat, imgW: Int, imgH: Int,
        minArea: Double, maxArea: Double, imageArea: Double
    ): Array<Point>? {
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(thresh, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
        hierarchy.release()

        data class Candidate(val cx: Double, val cy: Double)
        val candidates = mutableListOf<Candidate>()

        for (contour in contours) {
            val area = Imgproc.contourArea(contour)
            if (area < minArea || area > maxArea) continue

            val rect = Imgproc.boundingRect(contour)
            val aspect = rect.width.toDouble() / rect.height.toDouble()
            // Must be roughly square
            if (aspect < 0.5 || aspect > 2.0) continue

            // Solidity check — must be mostly filled
            val hull = MatOfInt()
            Imgproc.convexHull(contour, hull)
            val hullIndices = hull.toArray()
            val contourPts = contour.toArray()
            if (hullIndices.size >= 3) {
                val hullPoints = hullIndices.map { contourPts[it] }
                val hullMat = MatOfPoint(*hullPoints.toTypedArray())
                val hullArea = Imgproc.contourArea(hullMat)
                val solidity = if (hullArea > 0) area / hullArea else 0.0
                hullMat.release()
                if (solidity < 0.75) { hull.release(); continue }
            }
            hull.release()

            candidates.add(Candidate(rect.x + rect.width / 2.0, rect.y + rect.height / 2.0))
        }
        contours.forEach { it.release() }

        if (candidates.size < 4) return null

        // Pick the 4 candidates closest to image corners
        val imgCorners = arrayOf(
            Point(0.0, 0.0),
            Point(imgW.toDouble(), 0.0),
            Point(imgW.toDouble(), imgH.toDouble()),
            Point(0.0, imgH.toDouble())
        )

        val found = Array(4) { idx ->
            val target = imgCorners[idx]
            val best = candidates.minByOrNull { distance(Point(it.cx, it.cy), target) }
                ?: return null
            Point(best.cx, best.cy)
        }

        // Validate quadrilateral area
        val quadArea = shoelaceArea(found)
        if (quadArea < imageArea * MIN_QUAD_AREA_RATIO || quadArea > imageArea * MAX_QUAD_AREA_RATIO) {
            return null
        }

        return found // TL, TR, BR, BL
    }

    private fun shoelaceArea(pts: Array<Point>): Double {
        val n = pts.size
        var area = 0.0
        for (i in 0 until n) {
            val j = (i + 1) % n
            area += pts[i].x * pts[j].y
            area -= pts[j].x * pts[i].y
        }
        return abs(area) / 2.0
    }

    private fun distance(a: Point, b: Point): Double =
        sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y))

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
            yuv.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 50, out)
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
        detectionProgress = 0
        enabled = true
    }

    fun disable() {
        enabled = false
    }
}
