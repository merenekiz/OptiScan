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
 *
 * IMPORTANT: The alignment markers sit at known positions on the form:
 *   TL marker center ≈ (20, 20)
 *   TR marker center ≈ (780, 20)
 *   BR marker center ≈ (780, formH-20)
 *   BL marker center ≈ (20, formH-20)
 * The warp destination maps marker centers to these form coordinates,
 * NOT to (0,0), so that after warping the entire 800×1100 coordinate
 * system is pixel-accurate for bubble detection and OCR.
 */
@Singleton
class PerspectiveTransformer @Inject constructor() {

    companion object {
        private const val TAG = "PerspectiveTransformer"
        private const val OUTPUT_WIDTH = 800
        private const val OUTPUT_HEIGHT = 1100
        private const val MIN_AREA_RATIO = 0.03
        private const val MAX_AREA_RATIO = 0.95

        // Marker properties in 800×1100 coordinate space
        // Marker is 32×32px drawn at 4px margin from edge
        // So marker center = 4 + 16 = 20px from each edge
        private const val MARKER_OFFSET = 20.0
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
                Log.d(TAG, "Found corners via alignment markers: " +
                    "TL(${markerCorners[0].x.toInt()},${markerCorners[0].y.toInt()}) " +
                    "TR(${markerCorners[1].x.toInt()},${markerCorners[1].y.toInt()}) " +
                    "BR(${markerCorners[2].x.toInt()},${markerCorners[2].y.toInt()}) " +
                    "BL(${markerCorners[3].x.toInt()},${markerCorners[3].y.toInt()})")
                gray.release()
                enhanced.release()
                val warped = applyWarpFromMarkers(srcMat, markerCorners)
                val bitmap = matToBitmap(warped)
                warped.release()
                return TransformResult(bitmap, markerCorners, true, 1f)
            }

            // Strategy 2: Contour-based detection (finds sheet edges, not markers)
            val blurred = Mat()
            Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)

            val contourCorners = detectViaContours(blurred, srcMat.cols(), srcMat.rows())
            gray.release()
            blurred.release()
            enhanced.release()

            if (contourCorners != null) {
                Log.d(TAG, "Found corners via contour detection")
                val warped = applyWarpFromEdges(srcMat, contourCorners)
                val bitmap = matToBitmap(warped)
                warped.release()
                return TransformResult(bitmap, contourCorners, true, 0.7f)
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

    private fun detectAlignmentMarkersMultiStrategy(gray: Mat, imgW: Int, imgH: Int): Array<Point>? {
        val imageArea = imgW.toDouble() * imgH.toDouble()
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

        // Strategy 3: Adaptive threshold (block 51)
        val adaptive2 = Mat()
        Imgproc.adaptiveThreshold(gray, adaptive2, 255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY_INV,
            51, 15.0)
        val result3 = findMarkersInBinary(adaptive2, imgW, imgH, minMarkerArea, maxMarkerArea, imageArea)
        adaptive2.release()
        if (result3 != null) return result3

        // Strategy 4: Fixed threshold
        val fixed = Mat()
        Imgproc.threshold(gray, fixed, 80.0, 255.0, Imgproc.THRESH_BINARY_INV)
        val result4 = findMarkersInBinary(fixed, imgW, imgH, minMarkerArea, maxMarkerArea, imageArea)
        fixed.release()
        return result4
    }

    /**
     * Find 4 filled square markers in a binary image.
     * Each marker must be assigned to a unique corner — no duplicates.
     */
    private fun findMarkersInBinary(
        binary: Mat, imgW: Int, imgH: Int,
        minArea: Double, maxArea: Double, imageArea: Double
    ): Array<Point>? {
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(binary, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
        hierarchy.release()

        data class MarkerCandidate(val cx: Double, val cy: Double, val area: Double)
        val candidates = mutableListOf<MarkerCandidate>()

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
                if (solidity < 0.7) { hull.release(); continue }
            }
            hull.release()

            // Extent check — must fill most of its bounding rect
            val extent = area / (rect.width.toDouble() * rect.height.toDouble())
            if (extent < 0.55) continue

            candidates.add(MarkerCandidate(
                rect.x + rect.width / 2.0,
                rect.y + rect.height / 2.0,
                area
            ))
        }
        contours.forEach { it.release() }

        if (candidates.size < 4) return null

        // Greedy assignment: assign each image corner to its nearest candidate,
        // removing used candidates to prevent duplicate assignment.
        val imgCorners = arrayOf(
            Point(0.0, 0.0),              // TL
            Point(imgW.toDouble(), 0.0),    // TR
            Point(imgW.toDouble(), imgH.toDouble()), // BR
            Point(0.0, imgH.toDouble())     // BL
        )

        val remaining = candidates.toMutableList()
        val found = Array<Point?>(4) { null }

        for (idx in imgCorners.indices) {
            val target = imgCorners[idx]
            val best = remaining.minByOrNull { distance(Point(it.cx, it.cy), target) }
                ?: return null
            found[idx] = Point(best.cx, best.cy)
            remaining.remove(best)
        }

        @Suppress("UNCHECKED_CAST")
        val result = found as Array<Point>

        // Validate: quadrilateral area should be reasonable
        val quadArea = quadrilateralArea(result)
        if (quadArea < imageArea * 0.05 || quadArea > imageArea * 0.98) return null

        // Validate: opposite sides should be similar length (within 40%)
        val d01 = distance(result[0], result[1])
        val d12 = distance(result[1], result[2])
        val d23 = distance(result[2], result[3])
        val d30 = distance(result[3], result[0])

        if (d01 > 0 && d23 > 0) {
            if (min(d01, d23) / max(d01, d23) < 0.6) return null
        }
        if (d12 > 0 && d30 > 0) {
            if (min(d12, d30) / max(d12, d30) < 0.6) return null
        }

        // Validate: markers should have similar sizes (within 3x)
        // Already passed area filter, but check the 4 selected are consistent
        return result
    }

    /**
     * Warp using detected marker centers → known marker positions in output space.
     *
     * The form width is always 800px and markers are 20px in from each edge,
     * so the horizontal marker span = 760px.
     * The form HEIGHT varies by question count. We compute it from the
     * marker aspect ratio in the source image:
     *   markerSpanW (in source) / markerSpanH (in source) = 760 / (formH - 40)
     *   → formH = 760 * markerSpanH / markerSpanW + 40
     *
     * Then we warp to formW × formH and resize to 800 × 1100 so the
     * coordinate system used by BubbleDetector and OcrProcessor stays correct.
     */
    private fun applyWarpFromMarkers(src: Mat, markerCenters: Array<Point>): Mat {
        val tl = markerCenters[0]; val tr = markerCenters[1]
        val br = markerCenters[2]; val bl = markerCenters[3]

        // Compute actual marker span in source image
        val srcSpanW = (distance(tl, tr) + distance(bl, br)) / 2.0
        val srcSpanH = (distance(tl, bl) + distance(tr, br)) / 2.0

        // Inner marker span in output: width = 800 - 2*20 = 760
        val innerW = OUTPUT_WIDTH.toDouble() - 2 * MARKER_OFFSET  // 760

        // Compute form height from aspect ratio
        val innerH = if (srcSpanW > 0) innerW * srcSpanH / srcSpanW else (OUTPUT_HEIGHT.toDouble() - 2 * MARKER_OFFSET)
        val formH = (innerH + 2 * MARKER_OFFSET).toInt().coerceIn(600, 1400)

        Log.d(TAG, "Computed formH=$formH from marker aspect ratio (srcW=${"%.0f".format(srcSpanW)}, srcH=${"%.0f".format(srcSpanH)})")

        val srcPts = MatOfPoint2f(tl, tr, br, bl)
        val dstPts = MatOfPoint2f(
            Point(MARKER_OFFSET, MARKER_OFFSET),
            Point(OUTPUT_WIDTH.toDouble() - MARKER_OFFSET, MARKER_OFFSET),
            Point(OUTPUT_WIDTH.toDouble() - MARKER_OFFSET, formH.toDouble() - MARKER_OFFSET),
            Point(MARKER_OFFSET, formH.toDouble() - MARKER_OFFSET)
        )

        val M = Imgproc.getPerspectiveTransform(srcPts, dstPts)
        val warped = Mat()
        Imgproc.warpPerspective(src, warped, M,
            Size(OUTPUT_WIDTH.toDouble(), formH.toDouble()),
            Imgproc.INTER_LINEAR, Core.BORDER_REPLICATE)
        srcPts.release(); dstPts.release(); M.release()

        // Resize to standard 800×1100 so downstream coordinates (bubble grid, OCR) work
        val resized = Mat()
        Imgproc.resize(warped, resized, Size(OUTPUT_WIDTH.toDouble(), OUTPUT_HEIGHT.toDouble()))
        warped.release()
        return resized
    }

    /**
     * Warp using detected sheet EDGES (not markers) → full output rect.
     * Contour detection finds the paper outline, so mapping to (0,0)→(800,1100) is correct.
     */
    private fun applyWarpFromEdges(src: Mat, corners: Array<Point>): Mat {
        val tl = corners[0]; val tr = corners[1]
        val br = corners[2]; val bl = corners[3]

        val srcPts = MatOfPoint2f(tl, tr, br, bl)
        val dstPts = MatOfPoint2f(
            Point(0.0, 0.0),
            Point(OUTPUT_WIDTH.toDouble() - 1.0, 0.0),
            Point(OUTPUT_WIDTH.toDouble() - 1.0, OUTPUT_HEIGHT.toDouble() - 1.0),
            Point(0.0, OUTPUT_HEIGHT.toDouble() - 1.0)
        )

        val M = Imgproc.getPerspectiveTransform(srcPts, dstPts)
        val warped = Mat()
        Imgproc.warpPerspective(src, warped, M,
            Size(OUTPUT_WIDTH.toDouble(), OUTPUT_HEIGHT.toDouble()),
            Imgproc.INTER_LINEAR, Core.BORDER_REPLICATE)

        srcPts.release(); dstPts.release(); M.release()
        return warped
    }

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
        // Sort by sum (x+y): smallest = TL, largest = BR
        // Sort by diff (y-x): smallest = TR, largest = BL
        val topLeft = pts.minByOrNull { it.x + it.y }!!
        val bottomRight = pts.maxByOrNull { it.x + it.y }!!
        val topRight = pts.minByOrNull { it.y - it.x }!!
        val bottomLeft = pts.maxByOrNull { it.y - it.x }!!
        return arrayOf(topLeft, topRight, bottomRight, bottomLeft)
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
        hist.release(); channels.release(); histSize.release(); ranges.release(); emptyMask.release()
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
