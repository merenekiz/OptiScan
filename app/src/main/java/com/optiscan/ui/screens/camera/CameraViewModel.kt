package com.optiscan.ui.screens.camera

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.optiscan.analysis.GradingEngine
import com.optiscan.analysis.models.GradingResult
import com.optiscan.analysis.models.ScoringConfig
import com.optiscan.camera.CameraManager
import com.optiscan.data.entities.ExamEntity
import com.optiscan.data.entities.StudentResultEntity
import com.optiscan.data.repository.ExamRepository
import com.optiscan.data.repository.StudentResultRepository
import com.optiscan.ocr.OcrProcessor
import com.optiscan.processing.OmrProcessor
import com.optiscan.qr.QrScanner
import com.optiscan.qr.models.ExamMetadata
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ScanPhase {
    object Idle : ScanPhase()
    object QrScanning : ScanPhase()
    data class QrDetected(val metadata: ExamMetadata) : ScanPhase()
    object Capturing : ScanPhase()
    object Processing : ScanPhase()
    data class Done(
        val result: GradingResult,
        val warnings: List<String> = emptyList()
    ) : ScanPhase()
    data class Error(val message: String) : ScanPhase()
}

data class CameraUiState(
    val phase: ScanPhase = ScanPhase.Idle,
    val currentExam: ExamEntity? = null,
    val previewBitmap: Bitmap? = null,
    val batchCount: Int = 0
)

@HiltViewModel
class CameraViewModel @Inject constructor(
    private val cameraManager: CameraManager,
    private val omrProcessor: OmrProcessor,
    private val ocrProcessor: OcrProcessor,
    private val qrScanner: QrScanner,
    private val gradingEngine: GradingEngine,
    private val examRepo: ExamRepository,
    private val resultRepo: StudentResultRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private val gson = Gson()

    // Expose for CameraScreen — needed to wire PreviewView
    val camera: CameraManager get() = cameraManager

    fun loadExam(examId: Long) {
        viewModelScope.launch {
            val exam = examRepo.getExamById(examId)
            _uiState.update { it.copy(currentExam = exam, phase = ScanPhase.QrScanning) }
        }
    }

    fun onQrDetected(metadata: ExamMetadata) {
        viewModelScope.launch {
            _uiState.update { it.copy(phase = ScanPhase.QrDetected(metadata)) }
        }
    }

    fun captureAndProcess(context: Context) {
        val exam = _uiState.value.currentExam ?: return

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(phase = ScanPhase.Capturing) }

                val bitmap = cameraManager.captureImage(context)
                if (bitmap == null) {
                    _uiState.update { it.copy(phase = ScanPhase.Error("Kamera görüntüsü alınamadı")) }
                    return@launch
                }

                _uiState.update { it.copy(phase = ScanPhase.Processing) }

                val omrResult = omrProcessor.process(bitmap, exam.questionCount)
                if (!bitmap.isRecycled) bitmap.recycle()

                if (!omrResult.isSuccess) {
                    _uiState.update {
                        it.copy(phase = ScanPhase.Error(omrResult.errorMessage ?: "OMR başarısız"))
                    }
                    return@launch
                }

                val studentInfo = omrResult.warpedBitmap?.let {
                    ocrProcessor.extractStudentInfo(it)
                } ?: com.optiscan.ocr.models.StudentInfo.EMPTY

                val answerKeyList: List<String> = try {
                    val type = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
                    gson.fromJson(exam.answerKey, type) ?: emptyList()
                } catch (e: Exception) { emptyList() }

                val config = ScoringConfig(
                    questionCount = exam.questionCount,
                    correctPoint = exam.correctPoint,
                    wrongPenalty = exam.wrongPenalty,
                    answerKey = answerKeyList
                )

                val gradingResult = gradingEngine.grade(
                    detectedAnswers = omrResult.detectedAnswers,
                    bubbles = omrResult.bubbles,
                    studentInfo = studentInfo,
                    config = config,
                    warpedBitmap = omrResult.warpedBitmap,
                    examId = exam.id
                )

                saveResult(exam.id, gradingResult, omrResult.detectedAnswers)
                val warnings = buildWarnings(exam.id, gradingResult)

                _uiState.update { state ->
                    state.copy(
                        phase = ScanPhase.Done(gradingResult, warnings),
                        batchCount = state.batchCount + 1,
                        previewBitmap = gradingResult.gradedBitmap
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(phase = ScanPhase.Error("Tarama hatası: ${e.message ?: "Bilinmeyen"}"))
                }
            }
        }
    }

    private suspend fun saveResult(
        examId: Long,
        result: GradingResult,
        detectedAnswers: List<String>
    ) {
        val entity = StudentResultEntity(
            examId = examId,
            studentName = result.studentInfo.name,
            studentNumber = result.studentInfo.studentNumber,
            className = result.studentInfo.className,
            detectedAnswers = gson.toJson(detectedAnswers),
            correctCount = result.correctCount,
            wrongCount = result.wrongCount,
            emptyCount = result.emptyCount,
            score = result.score,
            scannedImagePath = null
        )
        resultRepo.insertResult(entity)
    }

    private suspend fun buildWarnings(examId: Long, result: GradingResult): List<String> {
        val warnings = mutableListOf<String>()

        // Check if student name is blank
        if (result.studentInfo.name.isBlank()) {
            warnings.add("Ad Soyad algılanamadı")
        }
        // Check if student number is blank
        if (result.studentInfo.studentNumber.isBlank()) {
            warnings.add("Öğrenci No algılanamadı")
        }
        // Check for duplicate student number
        if (result.studentInfo.studentNumber.isNotBlank()) {
            val existingResults = resultRepo.getResultsSortedByScore(examId)
            val duplicates = existingResults.filter {
                it.studentNumber == result.studentInfo.studentNumber
            }
            if (duplicates.size > 1) {
                warnings.add("Bu öğrenci numarası (${result.studentInfo.studentNumber}) ile ${duplicates.size} kayıt var!")
            }
        }

        return warnings
    }

    /**
     * Process a bitmap picked from the gallery (emulator / no-camera mode).
     * Same pipeline as captureAndProcess but skips CameraX capture.
     */
    fun processGalleryImage(bitmap: android.graphics.Bitmap) {
        val exam = _uiState.value.currentExam ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(phase = ScanPhase.Processing) }

            try {
                // Ensure bitmap is ARGB_8888 and mutable for OpenCV
                val safeBitmap = if (bitmap.config != android.graphics.Bitmap.Config.ARGB_8888) {
                    bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, true).also {
                        bitmap.recycle()
                    }
                } else {
                    bitmap
                }

                val omrResult = omrProcessor.process(safeBitmap, exam.questionCount)
                if (!safeBitmap.isRecycled) safeBitmap.recycle()

                if (!omrResult.isSuccess) {
                    _uiState.update {
                        it.copy(phase = ScanPhase.Error(omrResult.errorMessage ?: "OMR başarısız"))
                    }
                    return@launch
                }

                val studentInfo = omrResult.warpedBitmap?.let {
                    ocrProcessor.extractStudentInfo(it)
                } ?: com.optiscan.ocr.models.StudentInfo.EMPTY

                val answerKeyList: List<String> = try {
                    val type = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
                    gson.fromJson(exam.answerKey, type) ?: emptyList()
                } catch (e: Exception) { emptyList() }

                val config = ScoringConfig(
                    questionCount = exam.questionCount,
                    correctPoint = exam.correctPoint,
                    wrongPenalty = exam.wrongPenalty,
                    answerKey = answerKeyList
                )

                val gradingResult = gradingEngine.grade(
                    detectedAnswers = omrResult.detectedAnswers,
                    bubbles = omrResult.bubbles,
                    studentInfo = studentInfo,
                    config = config,
                    warpedBitmap = omrResult.warpedBitmap,
                    examId = exam.id
                )

                saveResult(exam.id, gradingResult, omrResult.detectedAnswers)
                val warnings = buildWarnings(exam.id, gradingResult)

                _uiState.update { state ->
                    state.copy(
                        phase = ScanPhase.Done(gradingResult, warnings),
                        batchCount = state.batchCount + 1,
                        previewBitmap = gradingResult.gradedBitmap
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(phase = ScanPhase.Error("İşleme hatası: ${e.message ?: "Bilinmeyen"}"))
                }
            }
        }
    }

    fun resetForNextScan() {
        _uiState.update { it.copy(phase = ScanPhase.QrScanning, previewBitmap = null) }
    }

    override fun onCleared() {
        super.onCleared()
        cameraManager.shutdown()
        // Release held bitmaps to avoid leaks
        _uiState.value.previewBitmap?.let { if (!it.isRecycled) it.recycle() }
        val phase = _uiState.value.phase
        if (phase is ScanPhase.Done) {
            phase.result.gradedBitmap?.let { if (!it.isRecycled) it.recycle() }
        }
    }
}
