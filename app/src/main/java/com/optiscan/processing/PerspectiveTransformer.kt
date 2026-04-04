package com.optiscan.processing

import android.graphics.Bitmap
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Detects the answer sheet in an image and applies perspective correction.
 * Uses edge detection + contour finding to locate the four sheet corners,
 * then warpPerspective to produce a flat top-down view.
 */
@Singleton
class PerspectiveTransformer @Inject constructor() {

    companion object {
        private const val TAG = "PerspectiveTransformer"
        private const val OUTPUT_WIDTH = 800
        private const val OUTPUT_HEIGHT = 1100
        private const val MIN_AREA_RATIO = 0.1      // sheet must cover ≥10% of image
        private const val MAX_AREA_RATIO = 0.95
    }

    data class TransformResult(
        val bitmap: Bitmap?,
        val corners: Array<Point>?,
        val isSuccess: Boolean,
        val confidence: Float = 1f
    )

    fun transform(source: Bitmap): TransformResult {
        val srcMat = Mat()
        Utils.bitmapToMat(source, srcMat)

        return try {
            Log.d(TAG, "Input image size: ${srcMat.cols()}x${srcMat.rows()}")

            // If image is already close to target dimensions, just resize directly
            // (test forms from gallery are already 800x1100 or very close ratio)
            val ratio = srcMat.cols().toFloat() / srcMat.rows().toFloat()
            val targetRatio = OUTPUT_WIDTH.toFloat() / OUTPUT_HEIGHT.toFloat()
            if (kotlin.math.abs(ratio - targetRatio) < 0.05f) {
                Log.d(TAG, "Image ratio matches target (${ratio} vs ${targetRatio}), resizing directly")
                val resized = Mat()
                Imgproc.resize(srcMat, resized, Size(OUTPUT_WIDTH.toDouble(), OUTPUT_HEIGHT.toDouble()))
                val bitmap = matToBitmap(resized)
                resized.release()
                return TransformResult(bitmap, null, true, 0.9f)
            }

            val corners = detectSheetCorners(srcMat)
            if (corners != null) {
                Log.d(TAG, "Corners detected: TL=${corners[0]}, TR=${corners[1]}, BR=${corners[2]}, BL=${corners[3]}")
                val warped = applyWarp(srcMat, corners)
                Log.d(TAG, "Warped output size: ${warped.cols()}x${warped.rows()}")
                val bitmap = matToBitmap(warped)
                warped.release()
                TransformResult(bitmap, corners, true, 1f)
            } else {
                // Fallback: assume full image is the sheet, resize to target
                Log.w(TAG, "Sheet corners not detected, using resize fallback")
                val resized = Mat()
                Imgproc.resize(srcMat, resized, Size(OUTPUT_WIDTH.toDouble(), OUTPUT_HEIGHT.toDouble()))
                Log.d(TAG, "Fallback output size: ${resized.cols()}x${resized.rows()}")
                val bitmap = matToBitmap(resized)
                resized.release()
                TransformResult(bitmap, null, true, 0.5f)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Transform failed: ${e.message}", e)
            TransformResult(null, null, false)
        } finally {
            srcMat.release()
        }
    }

    private fun detectSheetCorners(src: Mat): Array<Point>? {
        val gray = Mat()
        val blurred = Mat()
        val edges = Mat()

        try {
            // Convert to grayscale (Utils.bitmapToMat produces RGBA, not BGR)
            Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)

            // Gaussian blur to reduce noise
            Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)

            // Adaptive Canny — auto-compute thresholds from median intensity
            val median = computeMedian(blurred)
            val lower = max(0.0, 0.67 * median)
            val upper = min(255.0, 1.33 * median)
            Imgproc.Canny(blurred, edges, lower, upper)

            // Dilate edges to close gaps
            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
            Imgproc.dilate(edges, edges, kernel)
            kernel.release()

            // Find contours
            val contours = mutableListOf<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(
                edges, contours, hierarchy,
                Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE
            )
            hierarchy.release()

            val imageArea = src.rows().toDouble() * src.cols().toDouble()

            // Find the largest quadrilateral contour
            val sortedContours = contours.sortedByDescending { Imgproc.contourArea(it) }

            for (contour in sortedContours) {
                val area = Imgproc.contourArea(contour)
                if (area < imageArea * MIN_AREA_RATIO) break
                if (area > imageArea * MAX_AREA_RATIO) continue

                val peri = Imgproc.arcLength(MatOfPoint2f(*contour.toArray()), true)
                val approx = MatOfPoint2f()
                Imgproc.approxPolyDP(MatOfPoint2f(*contour.toArray()), approx, 0.02 * peri, true)

                if (approx.rows() == 4) {
                    val pts = approx.toArray()
                    val ordered = orderPoints(pts)
                    approx.release()
                    contours.forEach { it.release() }
                    return ordered
                }
                approx.release()
            }
            contours.forEach { it.release() }
            return null

        } finally {
            gray.release()
            blurred.release()
            edges.release()
        }
    }

    /**
     * Orders corner points as [topLeft, topRight, bottomRight, bottomLeft]
     * using the sum/difference method which is robust against rotation.
     * topLeft has the smallest (x+y), bottomRight has the largest.
     * topRight has the smallest (y-x), bottomLeft has the largest.
     */
    private fun orderPoints(pts: Array<Point>): Array<Point> {
        val topLeft = pts.minByOrNull { it.x + it.y }!!
        val bottomRight = pts.maxByOrNull { it.x + it.y }!!
        val topRight = pts.minByOrNull { it.y - it.x }!!
        val bottomLeft = pts.maxByOrNull { it.y - it.x }!!
        return arrayOf(topLeft, topRight, bottomRight, bottomLeft)
    }

    private fun applyWarp(src: Mat, corners: Array<Point>): Mat {
        val tl = corners[0]; val tr = corners[1]
        val br = corners[2]; val bl = corners[3]

        // Compute output dimensions based on detected rectangle
        val widthA = distance(br, bl)
        val widthB = distance(tr, tl)
        val maxWidth = max(widthA, widthB).toInt()

        val heightA = distance(tr, br)
        val heightB = distance(tl, bl)
        val maxHeight = max(heightA, heightB).toInt()

        val w = if (maxWidth > 0) maxWidth else OUTPUT_WIDTH
        val h = if (maxHeight > 0) maxHeight else OUTPUT_HEIGHT

        val srcPts = MatOfPoint2f(tl, tr, br, bl)
        val dstPts = MatOfPoint2f(
            Point(0.0, 0.0),
            Point(w.toDouble() - 1, 0.0),
            Point(w.toDouble() - 1, h.toDouble() - 1),
            Point(0.0, h.toDouble() - 1)
        )

        val M = Imgproc.getPerspectiveTransform(srcPts, dstPts)
        val warped = Mat()
        Imgproc.warpPerspective(src, warped, M, Size(w.toDouble(), h.toDouble()))

        srcPts.release(); dstPts.release(); M.release()

        // Resize to standard output for consistent bubble grid
        val resized = Mat()
        Imgproc.resize(warped, resized, Size(OUTPUT_WIDTH.toDouble(), OUTPUT_HEIGHT.toDouble()))
        warped.release()
        return resized
    }

    private fun computeMedian(mat: Mat): Double {
        val hist = Mat()
        val channels = MatOfInt(0)
        val histSize = MatOfInt(256)
        val ranges = MatOfFloat(0f, 256f)
        val emptyMask = Mat()
        Imgproc.calcHist(listOf(mat), channels, emptyMask, hist, histSize, ranges)

        val totalPixels = mat.rows() * mat.cols()
        var cumulative = 0.0
        var median = 127.0
        for (i in 0 until 256) {
            cumulative += hist.get(i, 0)[0]
            if (cumulative >= totalPixels / 2.0) {
                median = i.toDouble()
                break
            }
        }
        hist.release()
        channels.release()
        histSize.release()
        ranges.release()
        emptyMask.release()
        return median
    }

    private fun distance(a: Point, b: Point): Double =
        sqrt((a.x - b.x).pow(2) + (a.y - b.y).pow(2))

    private fun matToBitmap(mat: Mat): Bitmap {
        val bitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(mat, bitmap)
        return bitmap
    }
}
