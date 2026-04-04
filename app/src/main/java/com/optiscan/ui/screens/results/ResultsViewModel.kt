package com.optiscan.ui.screens.results

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.optiscan.data.entities.ExamEntity
import com.optiscan.data.entities.StudentResultEntity
import com.optiscan.data.repository.ExamRepository
import com.optiscan.data.repository.StudentResultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ResultsUiState(
    val exam: ExamEntity? = null,
    val results: List<StudentResultEntity> = emptyList(),
    val averageScore: Float? = null,
    val isLoading: Boolean = false
)

@HiltViewModel
class ResultsViewModel @Inject constructor(
    private val examRepo: ExamRepository,
    private val resultRepo: StudentResultRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ResultsUiState())
    val uiState: StateFlow<ResultsUiState> = _uiState.asStateFlow()

    fun loadResultsForExam(examId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val exam = examRepo.getExamById(examId)
            _uiState.update { it.copy(exam = exam, isLoading = false) }
        }

        viewModelScope.launch {
            resultRepo.getResultsByExam(examId).collect { results ->
                val avg = if (results.isNotEmpty()) {
                    results.map { it.score }.average().toFloat()
                } else null
                _uiState.update { it.copy(results = results, averageScore = avg) }
            }
        }
    }

    fun deleteResult(result: StudentResultEntity) {
        viewModelScope.launch {
            resultRepo.deleteResult(result)
        }
    }
}
