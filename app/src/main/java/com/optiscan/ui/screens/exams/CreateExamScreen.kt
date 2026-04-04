package com.optiscan.ui.screens.exams

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.optiscan.ui.theme.CorrectGreen
import com.optiscan.ui.theme.Primary

private val OPTIONS = listOf("A", "B", "C", "D", "E")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateExamScreen(
    onBack: () -> Unit,
    onCreated: () -> Unit,
    viewModel: ExamViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var title by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var questionCount by remember { mutableStateOf("20") }
    var wrongPenalty by remember { mutableStateOf("0") }

    // Optical answer key: list of selected option per question ("" = unset)
    val qCount = questionCount.toIntOrNull()?.coerceIn(1, 100) ?: 20
    val answerKey = remember { mutableStateListOf<String>() }

    // Keep answerKey list in sync with question count
    LaunchedEffect(qCount) {
        while (answerKey.size < qCount) answerKey.add("")
        while (answerKey.size > qCount) answerKey.removeAt(answerKey.lastIndex)
    }

    // Auto-calculate: 100 / question count
    val correctPoint = if (qCount > 0) 100f / qCount else 0f

    LaunchedEffect(uiState.successMessage) {
        if (uiState.successMessage != null) {
            viewModel.clearMessage()
            onCreated()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sınav Oluştur") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Geri")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Primary)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // ── Exam Info Fields ──
            item {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Sınav Adı *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            item {
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Ders") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = questionCount,
                        onValueChange = { questionCount = it },
                        label = { Text("Soru Sayısı") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = wrongPenalty,
                        onValueChange = { wrongPenalty = it },
                        label = { Text("Yanlış Ceza") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        supportingText = { Text("0 = ceza yok") },
                        singleLine = true
                    )
                }
            }

            // ── Auto-calculated scoring info ──
            item {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Text("Toplam: 100 puan", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text(
                            "Her doğru: %.2f puan".format(correctPoint),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // ── Optical Answer Key Header ──
            item {
                Text(
                    "Cevap Anahtarı",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    "Her soru için doğru cevabı işaretleyin",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
                )
            }

            // ── Option column labels ──
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(Modifier.width(44.dp)) // question number width
                    OPTIONS.forEach { opt ->
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(opt, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Primary)
                        }
                    }
                }
            }

            // ── Bubble rows ──
            items(qCount) { qIndex ->
                AnswerBubbleRow(
                    questionNumber = qIndex + 1,
                    selectedOption = answerKey.getOrElse(qIndex) { "" },
                    onOptionSelected = { opt ->
                        if (qIndex < answerKey.size) {
                            answerKey[qIndex] = if (answerKey[qIndex] == opt) "" else opt
                        }
                    }
                )
            }

            // ── Error ──
            if (uiState.error != null) {
                item {
                    Text(uiState.error!!, color = MaterialTheme.colorScheme.error)
                }
            }

            // ── Save Button ──
            item {
                val filledCount = answerKey.count { it.isNotBlank() }
                if (filledCount < qCount && filledCount > 0) {
                    Text(
                        "$filledCount / $qCount soru işaretlendi",
                        fontSize = 12.sp,
                        color = Color(0xFFFF9800),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                Button(
                    onClick = {
                        val wPen = wrongPenalty.toFloatOrNull() ?: 0f
                        viewModel.createExam(
                            title, subject, qCount, correctPoint, wPen,
                            answerKey.toList()
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    enabled = title.isNotBlank() && !uiState.isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(Icons.Default.Save, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Kaydet")
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

/**
 * Single question row with 5 tappable bubbles (A-E).
 */
@Composable
private fun AnswerBubbleRow(
    questionNumber: Int,
    selectedOption: String,
    onOptionSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Question number
        Text(
            "$questionNumber.",
            modifier = Modifier.width(44.dp),
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(0.7f)
        )

        // Option bubbles
        OPTIONS.forEach { opt ->
            val isSelected = selectedOption == opt
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) CorrectGreen else Color.Transparent,
                            CircleShape
                        )
                        .border(
                            width = if (isSelected) 2.dp else 1.5.dp,
                            color = if (isSelected) CorrectGreen else Color.Gray.copy(0.5f),
                            shape = CircleShape
                        )
                        .clickable { onOptionSelected(opt) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        opt,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) Color.White else Color.Gray
                    )
                }
            }
        }
    }
}
