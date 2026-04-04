package com.optiscan.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.optiscan.ui.theme.CorrectGreen
import com.optiscan.ui.theme.Primary
import com.optiscan.ui.theme.WrongRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToExams: () -> Unit,
    onNavigateToScan: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "OptiScan",
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Primary,
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Primary)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(listOf(Primary, Color(0xFF1976D2)))
                        )
                        .padding(24.dp)
                ) {
                    Column {
                        Text(
                            "Optik Form Okuyucu",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "QR kod ile otomatik sınav yapılandırma,\nbaloncuk algılama ve otomatik not hesaplama",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = onNavigateToScan,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Primary
                            )
                        ) {
                            Icon(Icons.Default.CameraAlt, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Hızlı Tarama", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Menu grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HomeMenuCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Assignment,
                    title = "Sınavlar",
                    subtitle = "Oluştur & Yönet",
                    color = CorrectGreen,
                    onClick = onNavigateToExams
                )
                HomeMenuCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.QrCodeScanner,
                    title = "Tara",
                    subtitle = "Form Oku",
                    color = Primary,
                    onClick = onNavigateToScan
                )
            }

            // Feature list
            Text(
                "Özellikler",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                modifier = Modifier.padding(top = 8.dp)
            )

            FeatureRow(icon = Icons.Default.QrCode, text = "QR kod ile otomatik sınav yükleme")
            FeatureRow(icon = Icons.Default.CameraAlt, text = "Gerçek zamanlı optik form tarama")
            FeatureRow(icon = Icons.Default.Person, text = "OCR ile öğrenci bilgisi okuma")
            FeatureRow(icon = Icons.Default.Analytics, text = "Otomatik not hesaplama")
            FeatureRow(icon = Icons.Default.TableChart, text = "Excel'e aktarma")
            FeatureRow(icon = Icons.Default.WifiOff, text = "Tamamen çevrimdışı çalışır")

            Spacer(Modifier.weight(1f))

            Text(
                "Geliştiren: merenekiz",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.35f),
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    }
}

@Composable
private fun HomeMenuCard(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(36.dp))
            Spacer(Modifier.height(8.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
        }
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Icon(icon, null, modifier = Modifier.size(20.dp), tint = Primary)
        Spacer(Modifier.width(12.dp))
        Text(text, fontSize = 14.sp)
    }
}
