package com.optiscan.ui.screens.results

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.optiscan.data.entities.ExamEntity
import com.optiscan.data.entities.StudentResultEntity
import com.optiscan.ui.theme.CorrectGreen
import com.optiscan.ui.theme.Primary
import com.optiscan.ui.theme.WrongRed
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    examId: Long,
    onBack: () -> Unit,
    onExport: () -> Unit,
    viewModel: ResultsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showStats by remember { mutableStateOf(false) }

    LaunchedEffect(examId) {
        viewModel.loadResultsForExam(examId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(uiState.exam?.title ?: "Sonuçlar", fontWeight = FontWeight.Bold)
                        uiState.averageScore?.let {
                            Text("Ortalama: %.1f".format(it), fontSize = 11.sp, color = Color.White.copy(0.8f))
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Geri", tint = Color.White) }
                },
                actions = {
                    IconButton(onClick = { showStats = !showStats }) {
                        Icon(Icons.Default.BarChart, "İstatistik", tint = Color.White)
                    }
                    IconButton(onClick = onExport) {
                        Icon(Icons.Default.Download, "Excel'e Aktar", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Primary)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Stats header
            uiState.exam?.let { exam ->
                StatsBanner(
                    totalStudents = uiState.results.size,
                    avgScore = uiState.averageScore,
                    maxScore = exam.questionCount * exam.correctPoint
                )
            }

            // Question-level statistics panel
            AnimatedVisibility(visible = showStats && uiState.results.isNotEmpty() && uiState.exam != null) {
                uiState.exam?.let { exam ->
                    QuestionStatsPanel(exam = exam, results = uiState.results)
                }
            }

            if (uiState.results.isEmpty()) {
                EmptyResults(Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.results, key = { it.id }) { result ->
                        StudentResultCard(
                            result = result,
                            exam = uiState.exam,
                            maxScore = uiState.exam?.let { it.questionCount * it.correctPoint } ?: 100f,
                            onDelete = { viewModel.deleteResult(result) }
                        )
                    }
                    item { Spacer(Modifier.height(20.dp)) }
                }
            }
        }
    }
}

@Composable
private fun StatsBanner(totalStudents: Int, avgScore: Float?, maxScore: Float) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StatItem("Toplam", "$totalStudents öğrenci")
        StatItem("Ortalama", avgScore?.let { "%.1f".format(it) } ?: "-")
        StatItem("Max Puan", "%.0f".format(maxScore))
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Primary)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
    }
}

/**
 * Question-level statistics: shows correct/wrong/empty percentages per question.
 */
@Composable
private fun QuestionStatsPanel(exam: ExamEntity, results: List<StudentResultEntity>) {
    val gson = remember { Gson() }
    val answerKey: List<String> = remember(exam.answerKey) {
        try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson(exam.answerKey, type) ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    // Parse all student answers
    val allAnswers: List<List<String>> = remember(results) {
        results.map { r ->
            try {
                val type = object : TypeToken<List<String>>() {}.type
                gson.fromJson<List<String>>(r.detectedAnswers, type) ?: emptyList()
            } catch (e: Exception) { emptyList() }
        }
    }

    val totalStudents = results.size

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("Soru Bazlı İstatistik", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))

            // Header
            Row(Modifier.fillMaxWidth()) {
                Text("S.", Modifier.width(30.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text("Cevap", Modifier.width(40.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text("Doğru %", Modifier.weight(1f), fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text("Yanlış %", Modifier.weight(1f), fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text("Boş %", Modifier.weight(1f), fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            for (q in 0 until exam.questionCount) {
                val key = answerKey.getOrElse(q) { "" }
                var correctPct = 0
                var wrongPct = 0
                var emptyPct = 0

                if (totalStudents > 0) {
                    var c = 0; var w = 0; var e = 0
                    for (answers in allAnswers) {
                        val ans = answers.getOrElse(q) { "" }
                        when {
                            ans.isBlank() -> e++
                            ans == key -> c++
                            else -> w++
                        }
                    }
                    correctPct = (c * 100) / totalStudents
                    wrongPct = (w * 100) / totalStudents
                    emptyPct = (e * 100) / totalStudents
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${q + 1}", Modifier.width(30.dp), fontSize = 11.sp, textAlign = TextAlign.Center)
                    Text(key, Modifier.width(40.dp), fontSize = 11.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                    Text("$correctPct%", Modifier.weight(1f), fontSize = 11.sp, textAlign = TextAlign.Center, color = CorrectGreen)
                    Text("$wrongPct%", Modifier.weight(1f), fontSize = 11.sp, textAlign = TextAlign.Center, color = WrongRed)
                    Text("$emptyPct%", Modifier.weight(1f), fontSize = 11.sp, textAlign = TextAlign.Center, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
private fun StudentResultCard(
    result: StudentResultEntity,
    exam: ExamEntity?,
    maxScore: Float,
    onDelete: () -> Unit
) {
    val fmt = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }
    val scorePercent = if (maxScore > 0) (result.score / maxScore).coerceIn(0f, 1f) else 0f
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(result.studentName.ifBlank { "Bilinmeyen" }, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${result.studentNumber} · ${result.className}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "%.1f".format(result.score),
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = when {
                            scorePercent >= 0.85f -> CorrectGreen
                            scorePercent >= 0.5f -> Primary
                            else -> WrongRed
                        }
                    )
                    Text("/ %.0f".format(maxScore), fontSize = 10.sp)
                }
            }

            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { scorePercent },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = when {
                    scorePercent >= 0.85f -> CorrectGreen
                    scorePercent >= 0.5f -> Primary
                    else -> WrongRed
                },
                trackColor = MaterialTheme.colorScheme.onSurface.copy(0.1f)
            )

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniChip("D: ${result.correctCount}", CorrectGreen)
                    MiniChip("Y: ${result.wrongCount}", WrongRed)
                    MiniChip("B: ${result.emptyCount}", Color.Gray)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(fmt.format(Date(result.scannedAt)), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error.copy(0.5f), modifier = Modifier.size(16.dp))
                    }
                }
            }

            // Expandable answer detail
            AnimatedVisibility(visible = expanded && exam != null) {
                exam?.let { AnswerDetailGrid(result = result, exam = it) }
            }
        }
    }
}

/**
 * Shows per-question answer detail for a student when their card is expanded.
 */
@Composable
private fun AnswerDetailGrid(result: StudentResultEntity, exam: ExamEntity) {
    val gson = remember { Gson() }
    val answerKey: List<String> = remember(exam.answerKey) {
        try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson(exam.answerKey, type) ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }
    val studentAnswers: List<String> = remember(result.detectedAnswers) {
        try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson(result.detectedAnswers, type) ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    Column(Modifier.padding(top = 8.dp)) {
        HorizontalDivider(Modifier.padding(bottom = 8.dp))
        Text("Soru Detayı", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))

        // Show in rows of 5
        for (startQ in 0 until exam.questionCount step 5) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (q in startQ until minOf(startQ + 5, exam.questionCount)) {
                    val key = answerKey.getOrElse(q) { "" }
                    val ans = studentAnswers.getOrElse(q) { "" }
                    val isCorrect = ans.isNotBlank() && ans == key
                    val isWrong = ans.isNotBlank() && ans != key

                    val bgColor = when {
                        isCorrect -> CorrectGreen.copy(0.15f)
                        isWrong -> WrongRed.copy(0.15f)
                        else -> Color.Gray.copy(0.1f)
                    }
                    val textColor = when {
                        isCorrect -> CorrectGreen
                        isWrong -> WrongRed
                        else -> Color.Gray
                    }

                    Surface(
                        color = bgColor,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            Modifier.padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("${q + 1}", fontSize = 9.sp, color = Color.Gray)
                            Text(
                                ans.ifBlank { "-" },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                            if (isWrong) {
                                Text(key, fontSize = 9.sp, color = CorrectGreen)
                            }
                        }
                    }
                }
                // Fill remaining space if less than 5
                val remaining = 5 - minOf(5, exam.questionCount - startQ)
                repeat(remaining) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MiniChip(text: String, color: Color) {
    Surface(color = color.copy(0.12f), shape = RoundedCornerShape(4.dp)) {
        Text(text, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            fontSize = 11.sp, color = color, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun EmptyResults(modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.BarChart, null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
        Spacer(Modifier.height(12.dp))
        Text("Henüz sonuç yok", color = Color.Gray)
        Text("Sınav formlarını taramaya başlayın", fontSize = 12.sp, color = Color.LightGray)
    }
}
