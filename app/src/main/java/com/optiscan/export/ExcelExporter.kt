package com.optiscan.export

import android.content.Context
import android.util.Log
import com.optiscan.data.entities.ExamEntity
import com.optiscan.data.entities.StudentResultEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Exports exam results to XLSX using Apache POI.
 * Single sheet with per-student results (no autoSizeColumn — it uses java.awt which doesn't exist on Android).
 */
@Singleton
class ExcelExporter @Inject constructor() {

    companion object {
        private const val TAG = "ExcelExporter"
        private fun timestampFormat() = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
    }

    data class ExportResult(
        val file: File?,
        val isSuccess: Boolean,
        val errorMessage: String? = null
    )

    suspend fun export(
        context: Context,
        exam: ExamEntity,
        results: List<StudentResultEntity>
    ): ExportResult = withContext(Dispatchers.IO) {
        var workbook: XSSFWorkbook? = null
        try {
            workbook = XSSFWorkbook()
            val styles = StyleCache(workbook)
            buildResultsSheet(workbook, exam, results, styles)

            val dir = File(context.filesDir, "exports").also { it.mkdirs() }
            val timestamp = timestampFormat().format(Date())
            val safeTitle = exam.title
                .replace(Regex("[^a-zA-Z0-9ÇĞİÖŞÜçğıöşü_ -]"), "")
                .replace(" ", "_")
                .take(50)
                .ifBlank { "sinav" }
            val filename = "${safeTitle}_${timestamp}.xlsx"
            val file = File(dir, filename)

            FileOutputStream(file).use { fos ->
                workbook.write(fos)
            }

            Log.d(TAG, "Excel exported: ${file.absolutePath}")
            ExportResult(file, true)

        } catch (e: Exception) {
            Log.e(TAG, "Export failed: ${e.message}", e)
            ExportResult(null, false, e.message)
        } finally {
            try { workbook?.close() } catch (_: Exception) {}
        }
    }

    private class StyleCache(wb: XSSFWorkbook) {
        val title: XSSFCellStyle = wb.createCellStyle().apply {
            val font = wb.createFont(); font.bold = true; font.fontHeightInPoints = 14; setFont(font)
        }
        val header: XSSFCellStyle = wb.createCellStyle().apply {
            fillForegroundColor = IndexedColors.CORNFLOWER_BLUE.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            val font = wb.createFont(); font.bold = true; font.color = IndexedColors.WHITE.index; setFont(font)
            alignment = HorizontalAlignment.CENTER; borderBottom = BorderStyle.THIN
        }
        val correct: XSSFCellStyle = wb.createCellStyle().apply {
            fillForegroundColor = IndexedColors.LIGHT_GREEN.index
            fillPattern = FillPatternType.SOLID_FOREGROUND; alignment = HorizontalAlignment.CENTER
        }
        val wrong: XSSFCellStyle = wb.createCellStyle().apply {
            fillForegroundColor = IndexedColors.ROSE.index
            fillPattern = FillPatternType.SOLID_FOREGROUND; alignment = HorizontalAlignment.CENTER
        }
        val empty: XSSFCellStyle = wb.createCellStyle().apply {
            fillForegroundColor = IndexedColors.GREY_25_PERCENT.index
            fillPattern = FillPatternType.SOLID_FOREGROUND; alignment = HorizontalAlignment.CENTER
        }
        val normal: XSSFCellStyle = wb.createCellStyle().apply {
            borderBottom = BorderStyle.THIN; borderLeft = BorderStyle.THIN; borderRight = BorderStyle.THIN
        }
    }

    private fun buildResultsSheet(
        workbook: XSSFWorkbook,
        exam: ExamEntity,
        results: List<StudentResultEntity>,
        styles: StyleCache
    ) {
        val sheet = workbook.createSheet("Sonuçlar")

        // Title row
        val titleRow = sheet.createRow(0)
        val titleCell = titleRow.createCell(0)
        titleCell.setCellValue("${exam.title} - Sınav Sonuçları")
        titleCell.cellStyle = styles.title
        sheet.addMergedRegion(org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 8))

        // Exam info row
        val infoRow = sheet.createRow(1)
        infoRow.createCell(0).setCellValue("Sınav ID: ${exam.examId}")
        infoRow.createCell(2).setCellValue("Soru: ${exam.questionCount}")
        infoRow.createCell(4).setCellValue("Doğru Puan: ${exam.correctPoint}")
        infoRow.createCell(6).setCellValue("Yanlış Ceza: ${exam.wrongPenalty}")

        // Header row
        val headerRow = sheet.createRow(3)
        val headers = listOf(
            "Sıra", "Ad Soyad", "Öğrenci No", "Şube",
            "Doğru", "Yanlış", "Boş", "Puan", "Tarih"
        )
        headers.forEachIndexed { i, h ->
            headerRow.createCell(i).apply {
                setCellValue(h)
                cellStyle = styles.header
            }
        }

        // Data rows
        results.forEachIndexed { rowIdx, result ->
            val row = sheet.createRow(4 + rowIdx)

            row.createCell(0).apply { setCellValue((rowIdx + 1).toDouble()); cellStyle = styles.normal }
            row.createCell(1).apply { setCellValue(result.studentName); cellStyle = styles.normal }
            row.createCell(2).apply { setCellValue(result.studentNumber); cellStyle = styles.normal }
            row.createCell(3).apply { setCellValue(result.className); cellStyle = styles.normal }
            row.createCell(4).apply { setCellValue(result.correctCount.toDouble()); cellStyle = styles.correct }
            row.createCell(5).apply { setCellValue(result.wrongCount.toDouble()); cellStyle = styles.wrong }
            row.createCell(6).apply { setCellValue(result.emptyCount.toDouble()); cellStyle = styles.empty }
            row.createCell(7).apply { setCellValue(result.score.toDouble()); cellStyle = styles.normal }
            row.createCell(8).apply {
                setCellValue(SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                    .format(Date(result.scannedAt)))
                cellStyle = styles.normal
            }
        }

        // Set manual column widths (in units of 1/256th of a character width)
        // No autoSizeColumn — it uses java.awt which doesn't exist on Android
        sheet.setColumnWidth(0, 2000)   // Sıra
        sheet.setColumnWidth(1, 7000)   // Ad Soyad
        sheet.setColumnWidth(2, 5000)   // Öğrenci No
        sheet.setColumnWidth(3, 4000)   // Şube
        sheet.setColumnWidth(4, 3000)   // Doğru
        sheet.setColumnWidth(5, 3000)   // Yanlış
        sheet.setColumnWidth(6, 3000)   // Boş
        sheet.setColumnWidth(7, 3500)   // Puan
        sheet.setColumnWidth(8, 5500)   // Tarih
    }
}
