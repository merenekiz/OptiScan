package com.optiscan.ui.screens.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.optiscan.data.entities.ExamEntity
import com.optiscan.data.repository.ExamRepository
import com.optiscan.data.repository.StudentResultRepository
import com.optiscan.export.ExcelExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class ExportUiState(
    val exam: ExamEntity? = null,
    val isExporting: Boolean = false,
    val exportedFile: File? = null,
    val error: String? = null
)

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val examRepo: ExamRepository,
    private val resultRepo: StudentResultRepository,
    private val excelExporter: ExcelExporter
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    fun loadExam(examId: Long) {
        viewModelScope.launch {
            val exam = examRepo.getExamById(examId)
            _uiState.update { it.copy(exam = exam) }
        }
    }

    fun exportToExcel(context: Context, examId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, error = null) }

            val exam = examRepo.getExamById(examId)
            if (exam == null) {
                _uiState.update { it.copy(isExporting = false, error = "Sınav bulunamadı") }
                return@launch
            }

            val results = resultRepo.getResultsSortedByScore(examId)
            val exportResult = excelExporter.export(context, exam, results)

            if (exportResult.isSuccess && exportResult.file != null) {
                _uiState.update {
                    it.copy(isExporting = false, exportedFile = exportResult.file)
                }
            } else {
                _uiState.update {
                    it.copy(isExporting = false, error = exportResult.errorMessage ?: "Dışa aktarma hatası")
                }
            }
        }
    }

    fun shareFile(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = getMimeType(file)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Dosyayı Paylaş"))
        } catch (e: Exception) {
            _uiState.update { it.copy(error = "Dosya paylaşılamadı: ${e.message}") }
        }
    }

    fun viewFile(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, getMimeType(file))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            _uiState.update { it.copy(error = "Bu dosyayı açabilecek uygulama bulunamadı") }
        } catch (e: Exception) {
            _uiState.update { it.copy(error = "Dosya açılamadı: ${e.message}") }
        }
    }

    private fun getMimeType(file: File): String = when {
        file.name.endsWith(".xlsx") -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        file.name.endsWith(".pdf") -> "application/pdf"
        else -> "application/octet-stream"
    }

    fun clearExported() {
        _uiState.update { it.copy(exportedFile = null) }
    }
}
