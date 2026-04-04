package com.optiscan.ui.screens.exams

import android.content.Intent
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.optiscan.data.entities.ExamEntity
import com.optiscan.ui.theme.Primary
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamListScreen(
    onNavigateToCreate: () -> Unit,
    onNavigateToExam: (Long) -> Unit,
    onNavigateToCamera: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: ExamViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var examToDelete by remember { mutableStateOf<ExamEntity?>(null) }
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Handle PDF generation result
    LaunchedEffect(uiState.pdfResult) {
        val result = uiState.pdfResult ?: return@LaunchedEffect
        viewModel.clearPdfResult()
        if (result.isSuccess && result.file != null) {
            try {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    result.file
                )
                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/pdf")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(viewIntent)
                } catch (_: android.content.ActivityNotFoundException) {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(
                        Intent.createChooser(shareIntent, "Optik Form PDF")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("PDF açılamadı: ${e.message}")
            }
        } else {
            snackbarHostState.showSnackbar("PDF oluşturulamadı: ${result.errorMessage}")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sınavlar") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Geri")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Primary)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToCreate,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Sınav Oluştur") },
                containerColor = Primary
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (uiState.exams.isEmpty()) {
            EmptyExamList(
                modifier = Modifier.fillMaxSize().padding(padding),
                onCreateClick = onNavigateToCreate
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.exams, key = { it.id }) { exam ->
                    ExamCard(
                        exam = exam,
                        isGeneratingPdf = uiState.generatingPdfForExamId == exam.id,
                        onScan = { onNavigateToCamera(exam.id) },
                        onResults = { onNavigateToExam(exam.id) },
                        onDelete = { examToDelete = exam },
                        onGenerateForm = { viewModel.generateForm(context, exam) }
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    examToDelete?.let { exam ->
        AlertDialog(
            onDismissRequest = { examToDelete = null },
            title = { Text("Sınavı Sil") },
            text = { Text("'${exam.title}' sınavı ve tüm sonuçları silinecek. Emin misiniz?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteExam(exam)
                    examToDelete = null
                }) { Text("Sil", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { examToDelete = null }) { Text("İptal") }
            }
        )
    }
}

@Composable
private fun ExamCard(
    exam: ExamEntity,
    isGeneratingPdf: Boolean,
    onScan: () -> Unit,
    onResults: () -> Unit,
    onDelete: () -> Unit,
    onGenerateForm: () -> Unit
) {
    val fmt = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(exam.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        exam.subject.ifBlank { exam.examId },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ExamChip("${exam.questionCount} Soru")
                ExamChip("+%.1f / -%.1f".format(exam.correctPoint, exam.wrongPenalty))
                ExamChip(fmt.format(Date(exam.createdAt)))
            }

            Spacer(Modifier.height(12.dp))

            // Action buttons: Scan, Results, Generate Form
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = onScan,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Icon(Icons.Default.CameraAlt, null, Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Tara", fontSize = 12.sp, maxLines = 1)
                }
                OutlinedButton(
                    onClick = onResults,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.BarChart, null, Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Sonuçlar", fontSize = 12.sp, maxLines = 1)
                }
                OutlinedButton(
                    onClick = onGenerateForm,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    enabled = !isGeneratingPdf
                ) {
                    if (isGeneratingPdf) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.PictureAsPdf, null, Modifier.size(14.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                    Text("Optik", fontSize = 12.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun ExamChip(text: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun EmptyExamList(modifier: Modifier, onCreateClick: () -> Unit) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Assignment,
            null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(0.3f)
        )
        Spacer(Modifier.height(16.dp))
        Text("Henüz sınav oluşturulmadı", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
        Spacer(Modifier.height(20.dp))
        Button(onClick = onCreateClick, colors = ButtonDefaults.buttonColors(containerColor = Primary)) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("Sınav Oluştur")
        }
    }
}
