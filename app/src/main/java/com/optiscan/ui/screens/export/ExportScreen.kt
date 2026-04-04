package com.optiscan.ui.screens.export

import androidx.compose.foundation.layout.*
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.optiscan.ui.theme.CorrectGreen
import com.optiscan.ui.theme.Primary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    examId: Long,
    onBack: () -> Unit,
    viewModel: ExportViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(examId) {
        viewModel.loadExam(examId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Excel Dışa Aktar") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Geri") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Primary)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(Icons.Default.TableChart, null, modifier = Modifier.size(80.dp).padding(top = 16.dp), tint = Primary)

            Text("Excel Raporu Oluştur", fontWeight = FontWeight.Bold, fontSize = 20.sp)

            uiState.exam?.let { exam ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        InfoRow("Sınav", exam.title)
                        InfoRow("Ders", exam.subject.ifBlank { "-" })
                        InfoRow("Soru Sayısı", "${exam.questionCount}")
                        InfoRow("Puanlama", "+${exam.correctPoint} / -${exam.wrongPenalty}")
                    }
                }
            }

            Text(
                "Excel dosyası şu sütunları içerir:\nSıra, Ad Soyad, Öğrenci No, Şube, Doğru, Yanlış, Boş, Puan, Tarih\n\nSoru bazlı detay ve istatistikler uygulama içinde görüntülenebilir.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.7f)
            )

            uiState.error?.let {
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small) {
                    Text(it, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }

            uiState.exportedFile?.let { file ->
                Surface(color = CorrectGreen.copy(0.1f), shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = CorrectGreen)
                            Spacer(Modifier.width(8.dp))
                            Text("Dosya oluşturuldu!", fontWeight = FontWeight.SemiBold, color = CorrectGreen)
                        }
                        Text(file.name, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.viewFile(context, file) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Visibility, null, tint = Primary)
                                Spacer(Modifier.width(4.dp))
                                Text("Görüntüle", color = Primary)
                            }
                            Button(
                                onClick = { viewModel.shareFile(context, file) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = CorrectGreen)
                            ) {
                                Icon(Icons.Default.Share, null)
                                Spacer(Modifier.width(4.dp))
                                Text("Paylaş")
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = { viewModel.exportToExcel(context, examId) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = !uiState.isExporting,
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                if (uiState.isExporting) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(12.dp))
                    Text("Oluşturuluyor...")
                } else {
                    Icon(Icons.Default.Download, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Excel Raporu Oluştur", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurface.copy(0.6f), fontSize = 13.sp)
        Text(value, fontWeight = FontWeight.Medium, fontSize = 13.sp)
    }
}
