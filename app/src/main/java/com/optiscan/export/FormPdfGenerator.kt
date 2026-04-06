package com.optiscan.export

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.util.Log
import com.optiscan.data.entities.ExamEntity
import com.optiscan.processing.BubbleDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generates a printable OMR form PDF.
 * Uses BubbleDetector.computeLayout() for grid coordinates —
 * bubbles fill the full form width and scale vertically by question count.
 */
@Singleton
class FormPdfGenerator @Inject constructor(
    private val bubbleDetector: BubbleDetector
) {

    companion object {
        private const val TAG = "FormPdfGenerator"
        private val OPTIONS = listOf("A", "B", "C", "D", "E")
        private const val FORM_W = BubbleDetector.SHEET_WIDTH   // 800
        private const val A4_W = 595
        private const val A4_H = 842
    }

    data class GenerateResult(
        val file: File?,
        val isSuccess: Boolean,
        val errorMessage: String? = null
    )

    suspend fun generate(context: Context, exam: ExamEntity, copies: Int = 1): GenerateResult =
        withContext(Dispatchers.Default) {
            try {
                val doc = PdfDocument()

                repeat(copies) { copyIndex ->
                    val pageInfo = PdfDocument.PageInfo.Builder(A4_W, A4_H, copyIndex + 1).create()
                    val page = doc.startPage(pageInfo)
                    drawPage(page.canvas, exam)
                    doc.finishPage(page)
                }

                val dir = File(context.filesDir, "forms").also { it.mkdirs() }
                val safeTitle = exam.title
                    .replace(Regex("[^a-zA-Z0-9ÇĞİÖŞÜçğıöşü_ -]"), "")
                    .replace(" ", "_")
                    .take(40)
                    .ifBlank { "form" }
                val file = File(dir, "${safeTitle}_optik.pdf")

                FileOutputStream(file).use { doc.writeTo(it) }
                doc.close()

                Log.d(TAG, "Form PDF: ${file.absolutePath}")
                GenerateResult(file, true)
            } catch (e: Exception) {
                Log.e(TAG, "PDF failed: ${e.message}", e)
                GenerateResult(null, false, e.message)
            }
        }

    private fun drawPage(canvas: Canvas, exam: ExamEntity) {
        val qCount = exam.questionCount
        val layout = bubbleDetector.computeLayout(qCount)
        val maxRows = maxOf(layout.col1Count, layout.col2Count)

        // Form height
        val gridBottom = BubbleDetector.GRID_START_Y + maxRows * layout.rowHeight + 15
        val formH = gridBottom + 25

        // Scale to fit A4 with 30pt margins
        val scale = minOf((A4_W - 60f) / FORM_W, (A4_H - 60f) / formH)
        val offsetX = (A4_W - FORM_W * scale) / 2f
        val offsetY = (A4_H - formH * scale) / 2f

        canvas.save()
        canvas.translate(offsetX, offsetY)
        canvas.scale(scale, scale)

        // Background + border
        val bgPaint = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }
        canvas.drawRect(0f, 0f, FORM_W.toFloat(), formH.toFloat(), bgPaint)
        val borderPaint = Paint().apply { color = Color.LTGRAY; style = Paint.Style.STROKE; strokeWidth = 1.5f }
        canvas.drawRect(0f, 0f, FORM_W.toFloat(), formH.toFloat(), borderPaint)

        // Corner markers
        drawAlignmentMarkers(canvas, FORM_W, formH)

        // Header
        drawHeader(canvas, exam)
        drawStudentInfo(canvas)

        // Bubble grid using layout
        drawBubbleGrid(canvas, layout)

        // Footer
        val footPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.GRAY; textSize = 7f; textAlign = Paint.Align.CENTER
        }
        canvas.drawText("OptiScan by merenekiz — ${exam.examId}", FORM_W / 2f, formH.toFloat() - 6, footPaint)

        canvas.restore()
    }

    private fun drawHeader(canvas: Canvas, exam: ExamEntity) {
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK; textSize = 20f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
        }
        canvas.drawText(exam.title, FORM_W / 2f, 42f, titlePaint)

        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY; textSize = 12f; textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            "Ders: ${exam.subject}  |  ${exam.questionCount} Soru  |  Her doğru: %.2f puan".format(exam.correctPoint),
            FORM_W / 2f, 62f, subPaint
        )
    }

    private fun drawStudentInfo(canvas: Canvas) {
        val m = BubbleDetector.GRID_MARGIN_X.toFloat()
        val endX = FORM_W - m
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK; textSize = 14f; typeface = Typeface.DEFAULT_BOLD
        }
        val boxPaint = Paint().apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 1f }

        // Row 1: Ad Soyad (box height = 30px)
        canvas.drawText("Ad Soyad:", m, 92f, labelPaint)
        canvas.drawRect(m + 80f, 76f, endX, 106f, boxPaint)

        // Row 2: Öğrenci No + Şube (box height = 30px)
        val midX = m + (endX - m) * 0.6f
        canvas.drawText("Öğrenci No:", m, 132f, labelPaint)
        canvas.drawRect(m + 90f, 116f, midX - 10f, 146f, boxPaint)
        canvas.drawText("Şube:", midX, 132f, labelPaint)
        canvas.drawRect(midX + 40f, 116f, endX, 146f, boxPaint)

        // Separator
        val sepPaint = Paint().apply { color = Color.LTGRAY; style = Paint.Style.STROKE; strokeWidth = 0.5f }
        canvas.drawLine(m, 165f, endX, 165f, sepPaint)

        // Instructions
        val instrPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.GRAY; textSize = 9f }
        canvas.drawText("Her soru için yalnızca bir seçenek işaretleyiniz.", m, 178f, instrPaint)
    }

    private fun drawAlignmentMarkers(canvas: Canvas, formW: Int, formH: Int) {
        val paint = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL }
        val s = 24f; val m = 4f
        canvas.drawRect(m, m, m + s, m + s, paint)
        canvas.drawRect(formW - m - s, m, formW - m, m + s, paint)
        canvas.drawRect(m, formH - m - s, m + s, formH - m, paint)
        canvas.drawRect(formW - m - s, formH - m - s, formW - m, formH - m, paint)
    }

    private fun drawBubbleGrid(canvas: Canvas, layout: BubbleDetector.GridLayout) {
        val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 1f
        }
        val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK; textSize = 9f; textAlign = Paint.Align.RIGHT
        }
        val optLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.GRAY; textSize = 6f; textAlign = Paint.Align.CENTER
        }
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK; textSize = 9f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
        }
        val r = BubbleDetector.BUBBLE_DIAMETER / 2f
        val startY = BubbleDetector.GRID_START_Y

        // Column 1 headers
        for (opt in OPTIONS.indices) {
            val cx = layout.col1X + opt * layout.optSpacing + r
            canvas.drawText(OPTIONS[opt], cx, (startY - 8).toFloat(), headerPaint)
        }
        // Column 2 headers
        if (layout.useSecondCol) {
            for (opt in OPTIONS.indices) {
                val cx = layout.col2X + opt * layout.optSpacing + r
                canvas.drawText(OPTIONS[opt], cx, (startY - 8).toFloat(), headerPaint)
            }
        }

        // Column 1 rows
        for (q in 0 until layout.col1Count) {
            val cy = startY + q * layout.rowHeight + layout.rowHeight / 2f
            canvas.drawText("${q + 1}.", layout.questionNumX1.toFloat(), cy + 3f, numPaint)
            for (opt in OPTIONS.indices) {
                val cx = layout.col1X + opt * layout.optSpacing + r
                canvas.drawCircle(cx, cy, r, bubblePaint)
                canvas.drawText(OPTIONS[opt], cx, cy + 2.5f, optLabelPaint)
            }
        }

        // Column 2 rows
        if (layout.useSecondCol) {
            for (q in 0 until layout.col2Count) {
                val globalQ = layout.col1Count + q
                val cy = startY + q * layout.rowHeight + layout.rowHeight / 2f
                canvas.drawText("${globalQ + 1}.", layout.questionNumX2.toFloat(), cy + 3f, numPaint)
                for (opt in OPTIONS.indices) {
                    val cx = layout.col2X + opt * layout.optSpacing + r
                    canvas.drawCircle(cx, cy, r, bubblePaint)
                    canvas.drawText(OPTIONS[opt], cx, cy + 2.5f, optLabelPaint)
                }
            }
        }
    }
}
