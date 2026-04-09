package com.optiscan.processing

import android.graphics.Bitmap
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Detects the answer sheet in an image and applies perspective correction.
 * Primary strategy: find 4 black alignment markers in the form corners.
 * Fallback: edge detection + contour finding for the sheet outline.
 * Then warpPerspective to produce a flat 800×1100 top-down view.
 */
@Singleton
class PerspectiveTransformer @Inject constructor() {

    companion object {
        private const val TAG = "PerspectiveTransformer"
        private const val OUTPUT_WIDTH = 800
        private const val OUTPUT_HEIGHT = 1100
        private const val MIN_AREA_RATIO = 0.03
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

            // If image is already exact target dimensions (test form from gallery)
            val ratio = srcMat.cols().toFloat() / srcMat.rows().toFloat()
            val targetRatio = OUTPUT_WIDTH.toFloat() / OUTPUT_HEIGHT.toFloat()
            if (abs(ratio - targetRatio) < 0.02f) {
                Log.d(TAG, "Image ratio matches target exactly, resizing directly")
                val resized = Mat()
                Imgproc.resize(srcMat, resized, Size(OUTPUT_WIDTH.toDouble(), OUTPUT_HEIGHT.toDouble()))
                val bitmap = matToBitmap(resized)
                resized.release()
                return TransformResult(bitmap, null, true, 0.9f)
            }

            // Enhance contrast for better marker detection on camera photos
            val enhanced = enhanceContrast(srcMat)

            // Strategy 1: Find alignment markers (highest priority, most reliable)
            val gray = Mat()
            Imgproc.cvtColor(enhanced, gray, Imgproc.COLOR_RGBA2GRAY)

            val markerCorners = detectAlignmentMarkersMultiStrategy(gray, srcMat.cols(), srcMat.rows())
            if (markerCorners != null) {
                Log.d(TAG, "Found corners via alignment markers")
                gray.release()
                enhanced.release()
                val warped = applyWarp(srcMat, markerCorners)
                val bitmap = matToBitmap(warped)
                warped.release()
                return TransformResult(bitmap, markerCorners, true, 1f)
            }

            // Strategy 2: Contour-based detection with multiple thresholds
            val blurred = Mat()
            Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)

            val contourCorners = detectViaContours(blurred, srcMat.cols(), srcMat.rows())
            gray.release()
            blurred.release()
            enhanced.release()

            if (contourCorners != null) {
                Log.d(TAG, "Found corners via contour detection")
                val warped = applyWarp(srcMat, contourCorners)
                val bitmap = matToBitmap(warped)
                warped.release()
                return TransformResult(bitmap, contourCorners, true, 0.8f)
            }

            Log.w(TAG, "Sheet corners not detected — form could not be found in image")
            TransformResult(null, null, false)

        } catch (e: Exception) {
            Log.e(TAG, "Transform failed: ${e.message}", e)
            TransformResult(null, null, false)
        } finally {
            srcMat.release()
        }
    }

    /**
     * Apply CLAHE contrast enhancement to improve marker detection on camera photos.
     */
    private fun enhanceContrast(src: Mat): Mat {
        val lab = Mat()
        Imgproc.cvtColor(src, lab, Imgproc.COLOR_RGBA2RGB)
        val labConverted = Mat()
        Imgproc.cvtColor(lab, labConverted, Imgproc.COLOR_RGB2Lab)
        lab.release()

        val channels = mutableListOf<Mat>()
        Core.split(labConverted, channels)

        val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
        clahe.apply(channels[0], channels[0])

        Core.merge(channels, labConverted)
        channels.forEach { it.release() }

        val result = Mat()
        Imgproc.cvtColor(labConverted, result, Imgproc.COLOR_Lab2RGB)
        labConverted.release()

        val rgba = Mat()
        Imgproc.cvtColor(result, rgba, Imgproc.COLOR_RGB2RGBA)
        result.release()
        return rgba
    }

    /**
     * Try multiple binarization strategies to find 4 alignment markers.
     */
    private fun detectAlignmentMarkersMultiStrategy(gray: Mat, imgW: Int, imgH: Int): Array<Point>? {
        val imageArea = imgW.toDouble() * imgH.toDouble()
        // Markers can range from tiny (gallery test forms) to large (camera close-up)
        val minMarkerArea = imageArea * 0.00003
        val maxMarkerArea = imageArea * 0.02

        // Strategy 1: Otsu global threshold
        val otsu = Mat()
        Imgproc.threshold(gray, otsu, 0.0, 255.0,
            Imgproc.THRESH_BINARY_INV or Imgproc.THRESH_OTSU)
        val result1 = findMarkersInBinary(otsu, imgW, imgH, minMarkerArea, maxMarkerArea, imageArea)
        otsu.release()
        if (result1 != null) return result1

        // Strategy 2: Adaptive threshold (block 31)
        val adaptive1 = Mat()
        Imgproc.adaptiveThreshold(gray, adaptive1, 255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY_INV,
            31, 12.0)
        val result2 = findMarkersInBinary(adaptive1, imgW, imgH, minMarkerArea, maxMarkerArea, imageArea)
        adaptive1.release()
        if (result2 != null) return result2

        // Strategy 3: Adaptive threshold (block 51, higher C)
        val adaptive2 = Mat()
        Imgproc.adaptiveThreshold(gray, adaptive2, 255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY_INV,
            51, 15.0)
        val result3 = findMarkersInBinary(adaptive2, imgW, imgH, minMarkerArea, maxMarkerArea, imageArea)
        adaptive2.release()
        if (result3 != null) return result3

        // Strategy 4: Fixed threshold for very high contrast prints
        val fixed = Mat()
        Imgproc.threshold(gray, fixed, 80.0, 255.0, Imgproc.THRESH_BINARY_INV)
        val result4 = findMarkersInBinary(fixed, imgW, imgH, minMarkerArea, maxMarkerArea, imageArea)
        fixed.release()
        return result4
    }

    /**
     * Find 4 filled square markers in a binary image.
     */
    private fun findMarkersInBinary(
        binary: Mat, imgW: Int, imgH: Int,
        minArea: Double, maxArea: Double, imageArea: Double
    ): Array<Point>? {
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(binary, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
        hierarchy.release()

        data class MarkerCenter(val cx: Double, val cy: Double, val area: Double)
        val candidates = mutableListOf<MarkerCenter>()

        for (contour in contours) {
            val area = Imgproc.contourArea(contour)
            if (area < minArea || area > maxArea) continue

            val rect = Imgproc.boundingRect(contour)
            val aspect = rect.width.toDouble() / rect.height.toDouble()
            if (aspect < 0.5 || aspect > 2.0) continue

            // Solidity check
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

            // Extent check — how much of bounding rect is filled
            val extent = area / (rect.width.toDouble() * rect.height.toDouble())
            if (extent < 0.6) continue

            candidates.add(MarkerCenter(rect.x + rect.width / 2.0, rect.y + rect.height / 2.0, area))
        }
        contours.forEach { it.release() }

        if (candidates.size < 4) return null

        // Pick the 4 candidates closest to image corners
        val corners = arrayOf(
            Point(0.0, 0.0),
            Point(imgW.toDouble(), 0.0),
            Point(imgW.toDouble(), imgH.toDouble()),
            Point(0.0, imgH.toDouble())
        )

        val found = Array(4) { idx ->
            val target = corners[idx]
            candidates.minByOrNull { distance(Point(it.cx, it.cy), target) }
                ?.let { Point(it.cx, it.cy) }
                ?: return null
        }

        // Validate: quadrilateral area should be reasonable
        val quadArea = quadrilateralArea(found)
        if (quadArea < imageArea * 0.05 || quadArea > imageArea * 0.98) return null

        // Validate: check that opposite sides are roughly parallel and angles ~90°
        val d01 = distance(found[0], found[1])
        val d12 = distance(found[1], found[2])
        val d23 = distance(found[2], found[3])
        val d30 = distance(found[3], found[0])

        // Opposite sides should be similar length (within 40%)
        if (d01 > 0 && d23 > 0) {
            val sideRatio1 = min(d01, d23) / max(d01, d23)
            if (sideRatio1 < 0.6) return null
        }
        if (d12 > 0 && d30 > 0) {
            val sideRatio2 = min(d12, d30) / max(d12, d30)
            if (sideRatio2 < 0.6) return null
        }

        return found // TL, TR, BR, BL
    }

    /**
     * Fallback: contour-based detection with multiple Canny thresholds.
     * Also applies morphological closing to connect broken edges.
     */
    private fun detectViaContours(blurred: Mat, imgW: Int, imgH: Int): Array<Point>? {
        val edges = Mat()
        val imageArea = imgW.toDouble() * imgH.toDouble()

        try {
            val thresholds = listOf(0.5, 0.67, 0.33)
            for (factor in thresholds) {
                val median = computeMedian(blurred)
                val lower = max(0.0, factor * median)
                val upper = min(255.0, (2.0 - factor) * median)
                Imgproc.Canny(blurred, edges, lower, upper)

                // Morphological closing to connect broken edges
                val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
                Imgproc.morphologyEx(edges, edges, Imgproc.MORPH_CLOSE, kernel)
                Imgproc.dilate(edges, edges, kernel)
                kernel.release()

                val contours = mutableListOf<MatOfPoint>()
                val hierarchy = Mat()
                Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
                hierarchy.release()

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
            }
            return null
        } finally {
            edges.release()
        }
    }

    private fun quadrilateralArea(pts: Array<Point>): Double {
        val n = pts.size
        var area = 0.0
        for (i in 0 until n) {
            val j = (i + 1) % n
            area += pts[i].x * pts[j].y
            area -= pts[j].x * pts[i].y
        }
        return abs(area) / 2.0
    }

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
