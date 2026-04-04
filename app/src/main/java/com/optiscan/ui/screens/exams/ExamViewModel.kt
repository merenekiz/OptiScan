package com.optiscan.ui.screens.exams

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.optiscan.data.entities.ExamEntity
import com.optiscan.data.repository.ExamRepository
import com.optiscan.export.FormPdfGenerator
import com.optiscan.qr.models.ExamMetadata
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExamUiState(
    val exams: List<ExamEntity> = emptyList(),
    val selectedExam: ExamEntity? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val generatingPdfForExamId: Long? = null,
    val pdfResult: FormPdfGenerator.GenerateResult? = null
)

@HiltViewModel
class ExamViewModel @Inject constructor(
    private val examRepo: ExamRepository,
    private val formPdfGenerator: FormPdfGenerator
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExamUiState())
    val uiState: StateFlow<ExamUiState> = _uiState.asStateFlow()

    private val gson = Gson()

    init {
        loadExams()
    }

    private fun loadExams() {
        viewModelScope.launch {
            examRepo.getAllExams().collect { exams ->
                _uiState.update { it.copy(exams = exams) }
            }
        }
    }

    fun loadExamById(id: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val exam = examRepo.getExamById(id)
            _uiState.update { it.copy(selectedExam = exam, isLoading = false) }
        }
    }

    fun createExam(
        title: String,
        subject: String,
        questionCount: Int,
        correctPoint: Float,
        wrongPenalty: Float,
        answerKey: List<String>
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val exam = ExamEntity(
                examId = System.currentTimeMillis().toString(),
                title = title,
                subject = subject,
                questionCount = questionCount,
                correctPoint = correctPoint,
                wrongPenalty = wrongPenalty,
                answerKey = gson.toJson(answerKey)
            )
            val id = examRepo.insertExam(exam)
            _uiState.update { it.copy(isLoading = false, successMessage = "Sınav oluşturuldu: $id") }
        }
    }

    fun createExamFromMetadata(metadata: ExamMetadata) {
        viewModelScope.launch {
            val existing = examRepo.getExamByExamId(metadata.examId)
            if (existing != null) {
                _uiState.update { it.copy(selectedExam = existing) }
                return@launch
            }
            val exam = ExamEntity(
                examId = metadata.examId,
                title = metadata.title.ifBlank { metadata.examId },
                subject = metadata.subject,
                questionCount = metadata.questionCount,
                correctPoint = metadata.correctPoint,
                wrongPenalty = metadata.wrongPenalty,
                answerKey = gson.toJson(List(metadata.questionCount) { "" })
            )
            val id = examRepo.insertExam(exam)
            _uiState.update { it.copy(selectedExam = exam.copy(id = id)) }
        }
    }

    fun deleteExam(exam: ExamEntity) {
        viewModelScope.launch {
            examRepo.deleteExam(exam)
        }
    }

    fun generateForm(context: Context, exam: ExamEntity) {
        viewModelScope.launch {
            _uiState.update { it.copy(generatingPdfForExamId = exam.id) }
            val result = formPdfGenerator.generate(context, exam)
            _uiState.update { it.copy(generatingPdfForExamId = null, pdfResult = result) }
        }
    }

    fun clearPdfResult() {
        _uiState.update { it.copy(pdfResult = null) }
    }

    fun clearMessage() {
        _uiState.update { it.copy(error = null, successMessage = null) }
    }
}
