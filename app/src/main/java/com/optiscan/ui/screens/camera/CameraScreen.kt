package com.optiscan.ui.screens.camera

import android.Manifest
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.optiscan.ui.theme.CorrectGreen
import com.optiscan.ui.theme.Primary
import com.optiscan.ui.theme.WrongRed
import kotlinx.coroutines.delay


@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    examId: Long,
    onBack: () -> Unit,
    onResultSaved: (Long) -> Unit,
    viewModel: CameraViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val bitmap = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(context.contentResolver, it)
                    ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        decoder.isMutableRequired = true
                    }
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                }
            } catch (e: Exception) { null }

            if (bitmap != null) {
                viewModel.processGalleryImage(bitmap)
            }
        }
    }

    LaunchedEffect(examId) {
        viewModel.loadExam(examId)
    }

    LaunchedEffect(cameraPermission.status.isGranted) {
        if (!cameraPermission.status.isGranted) {
            cameraPermission.launchPermissionRequest()
        }
    }

    // Auto-capture when sheet is detected
    LaunchedEffect(Unit) {
        viewModel.sheetDetectedEvent.collect {
            viewModel.captureAndProcess(context)
        }
    }

    // Poll detection progress for UI feedback
    val isScanning = uiState.phase is ScanPhase.QrScanning ||
            uiState.phase is ScanPhase.QrDetected ||
            uiState.phase is ScanPhase.Idle
    LaunchedEffect(isScanning) {
        while (isScanning) {
            viewModel.updateDetectionProgress()
            delay(200)
        }
    }

    val hasCamera = remember {
        context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_ANY)
    }

    if (!hasCamera || !cameraPermission.status.isGranted) {
        GalleryOnlyScreen(
            uiState = uiState,
            onBack = onBack,
            onPickImage = { galleryLauncher.launch("image/*") },
            onNext = { viewModel.resetForNextScan() },
            hasCameraFeature = hasCamera,
            onRequestPermission = { cameraPermission.launchPermissionRequest() }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Form Tara", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        uiState.currentExam?.let {
                            Text(it.title, fontSize = 11.sp, color = Color.White.copy(0.8f))
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Geri", tint = Color.White) }
                },
                actions = {
                    IconButton(onClick = { galleryLauncher.launch("image/*") }) {
                        Icon(Icons.Default.PhotoLibrary, "Galeriden Seç", tint = Color.White)
                    }
                    Text(
                        "Taranan: ${uiState.batchCount}",
                        modifier = Modifier.padding(end = 12.dp),
                        color = Color.White,
                        fontSize = 12.sp
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(padding)
        ) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    }.also { previewView ->
                        viewModel.camera.startCamera(
                            context = context,
                            lifecycleOwner = lifecycleOwner,
                            previewView = previewView,
                            onQrDetected = { metadata ->
                                viewModel.onQrDetected(metadata)
                            },
                            onSheetDetected = {
                                viewModel.onSheetDetected()
                            }
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Scan overlay frame with detection feedback
            ScanGuideOverlay(detectionProgress = uiState.detectionProgress)

            // Phase-specific UI
            when (val phase = uiState.phase) {
                is ScanPhase.Idle -> {}

                is ScanPhase.QrScanning -> {
                    ScanStatusBanner(
                        message = "Formu çerçeveye yerleştirin — otomatik taranacak",
                        color = Color(0xCC000000)
                    )
                }

                is ScanPhase.QrDetected -> {
                    ScanStatusBanner(
                        message = "QR okundu — formu çerçeveye yerleştirin, otomatik taranacak",
                        color = CorrectGreen.copy(alpha = 0.9f),
                        icon = Icons.Default.CheckCircle
                    )
                }

                is ScanPhase.Capturing -> {
                    ScanStatusBanner("Görüntü alınıyor...", Color(0xFF1565C0).copy(0.9f))
                }

                is ScanPhase.Processing -> {
                    ProcessingOverlay()
                }

                is ScanPhase.Done -> {
                    ResultOverlay(
                        phase = phase,
                        onNext = { viewModel.resetForNextScan() },
                        onBack = onBack
                    )
                }

                is ScanPhase.Error -> {
                    ErrorBanner(
                        message = phase.message,
                        onRetry = { viewModel.resetForNextScan() }
                    )
                }
            }

            // Manual capture button as fallback
            val showCapture = uiState.phase is ScanPhase.QrDetected ||
                    uiState.phase is ScanPhase.QrScanning ||
                    uiState.phase is ScanPhase.Idle
            if (showCapture && uiState.currentExam != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 40.dp)
                ) {
                    CaptureButton(
                        enabled = true,
                        onClick = { viewModel.captureAndProcess(context) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GalleryOnlyScreen(
    uiState: CameraUiState,
    onBack: () -> Unit,
    onPickImage: () -> Unit,
    onNext: () -> Unit,
    hasCameraFeature: Boolean,
    onRequestPermission: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Form Tara", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        uiState.currentExam?.let {
                            Text(it.title, fontSize = 11.sp, color = Color.White.copy(0.8f))
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Geri", tint = Color.White) }
                },
                actions = {
                    Text(
                        "Taranan: ${uiState.batchCount}",
                        modifier = Modifier.padding(end = 12.dp),
                        color = Color.White,
                        fontSize = 12.sp
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF121212))
                .padding(padding)
        ) {
            when (val phase = uiState.phase) {
                is ScanPhase.Processing -> ProcessingOverlay()
                is ScanPhase.Done -> ResultOverlay(phase = phase, onNext = onNext, onBack = onBack)
                is ScanPhase.Error -> ErrorBanner(message = phase.message, onRetry = onNext)
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.PhotoLibrary, null,
                            modifier = Modifier.size(80.dp),
                            tint = Primary
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            if (!hasCameraFeature) "Emülatör Modu" else "Galeri Modu",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color.White
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (!hasCameraFeature)
                                "Kamera bulunamadı. Test formlarını galeriden seçerek tarayabilirsiniz."
                            else
                                "Kamera izni verilmedi. Galeriden form seçerek tarayabilirsiniz.",
                            fontSize = 13.sp,
                            color = Color.White.copy(0.7f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        Spacer(Modifier.height(32.dp))

                        Button(
                            onClick = onPickImage,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Primary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Image, null, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Galeriden Form Seç", fontSize = 16.sp)
                        }

                        if (hasCameraFeature) {
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = onRequestPermission,
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.CameraAlt, null, tint = Primary)
                                Spacer(Modifier.width(8.dp))
                                Text("Kamera İzni Ver", color = Primary)
                            }
                        }

                        if (uiState.batchCount > 0) {
                            Spacer(Modifier.height(24.dp))
                            Surface(
                                color = CorrectGreen.copy(0.15f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    "${uiState.batchCount} form tarandı",
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    color = CorrectGreen,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Camera overlay with form guide frame and detection progress feedback.
 * Corner markers grow larger and change color as detection progresses.
 */
@Composable
private fun ScanGuideOverlay(detectionProgress: Int = 0) {
    val baseColor = Color(0xFF00E5FF)
    val detectedColor = CorrectGreen
    val cornerLen = 64.dp
    val strokeW = 5.dp

    // Animate corner color based on detection progress (0=cyan, 3=green)
    val progressFraction = (detectionProgress / 3f).coerceIn(0f, 1f)
    val cornerColor by animateColorAsState(
        targetValue = if (progressFraction > 0.3f) {
            Color(
                red = baseColor.red + (detectedColor.red - baseColor.red) * progressFraction,
                green = baseColor.green + (detectedColor.green - baseColor.green) * progressFraction,
                blue = baseColor.blue + (detectedColor.blue - baseColor.blue) * progressFraction,
                alpha = 1f
            )
        } else baseColor,
        animationSpec = tween(300),
        label = "cornerColor"
    )

    // Pulse animation when detection is in progress
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val frameAlpha = if (detectionProgress > 0) pulseAlpha else 0.3f

    Box(modifier = Modifier.fillMaxSize()) {
        val sheetRatio = 800f / 1100f
        Box(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .aspectRatio(sheetRatio)
                .align(Alignment.Center)
                .border(
                    width = if (detectionProgress > 0) 2.dp else 1.dp,
                    color = cornerColor.copy(alpha = frameAlpha),
                    shape = RoundedCornerShape(4.dp)
                )
        ) {
            // Top-left
            Box(Modifier.align(Alignment.TopStart).size(cornerLen)
                .border(width = strokeW, color = cornerColor, shape = RoundedCornerShape(topStart = 10.dp)))
            // Top-right
            Box(Modifier.align(Alignment.TopEnd).size(cornerLen)
                .border(width = strokeW, color = cornerColor, shape = RoundedCornerShape(topEnd = 10.dp)))
            // Bottom-left
            Box(Modifier.align(Alignment.BottomStart).size(cornerLen)
                .border(width = strokeW, color = cornerColor, shape = RoundedCornerShape(bottomStart = 10.dp)))
            // Bottom-right
            Box(Modifier.align(Alignment.BottomEnd).size(cornerLen)
                .border(width = strokeW, color = cornerColor, shape = RoundedCornerShape(bottomEnd = 10.dp)))
        }

        // Status text at bottom
        val statusText = when {
            detectionProgress >= 3 -> "Form algılandı — çekiliyor..."
            detectionProgress > 0 -> "Form algılanıyor... ($detectionProgress/3)"
            else -> "Formu çerçeveye yerleştirin — otomatik taranacak"
        }
        val statusBg = when {
            detectionProgress >= 2 -> CorrectGreen.copy(0.8f)
            detectionProgress > 0 -> Color(0xFF1565C0).copy(0.8f)
            else -> Color.Black.copy(0.6f)
        }

        Text(
            statusText,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 110.dp)
                .background(statusBg, RoundedCornerShape(8.dp))
                .padding(horizontal = 14.dp, vertical = 7.dp),
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = if (detectionProgress > 0) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun ScanStatusBanner(
    message: String,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .background(color, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon?.let { Icon(it, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
            if (icon != null) Spacer(Modifier.width(8.dp))
            Text(message, color = Color.White, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ProcessingOverlay() {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.7f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Primary)
            Spacer(Modifier.height(16.dp))
            Text("İşleniyor...", color = Color.White, fontWeight = FontWeight.Bold)
            Text("OMR + OCR analizi yapılıyor", color = Color.White.copy(0.7f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun ResultOverlay(
    phase: ScanPhase.Done,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val result = phase.result
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.CheckCircle, null, tint = CorrectGreen, modifier = Modifier.size(36.dp))
                Text("Tarama Tamamlandı", fontWeight = FontWeight.Bold, fontSize = 18.sp)

                Text(result.studentInfo.name.ifBlank { "Öğrenci adı okunamadı" }, fontSize = 14.sp)
                Text(
                    "${result.studentInfo.studentNumber.ifBlank { "-" }} · ${result.studentInfo.className.ifBlank { "-" }}",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
                )

                if (phase.warnings.isNotEmpty()) {
                    Surface(
                        color = Color(0xFFFFF3E0),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, null, tint = Color(0xFFE65100), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Column {
                                phase.warnings.forEach { warning ->
                                    Text(warning, fontSize = 11.sp, color = Color(0xFFE65100), fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ScoreChip("D: ${result.correctCount}", CorrectGreen)
                    ScoreChip("Y: ${result.wrongCount}", WrongRed)
                    ScoreChip("B: ${result.emptyCount}", Color.Gray)
                }

                Text(
                    "%.1f / %.1f Puan".format(result.score, result.maxScore),
                    fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Primary
                )
                Text("%.0f%%".format(result.percentage), fontSize = 14.sp)

                result.gradedBitmap?.let { bitmap ->
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Text("Optik Form Sonucu", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Değerlendirilmiş optik form",
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
                            .padding(vertical = 4.dp),
                        contentScale = ContentScale.Fit
                    )
                }

                Spacer(Modifier.height(4.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                        Text("Bitir")
                    }
                    Button(
                        onClick = onNext,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
                    ) {
                        Text("Sonraki")
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoreChip(text: String, color: Color) {
    Surface(color = color.copy(0.15f), shape = RoundedCornerShape(8.dp)) {
        Text(text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ErrorBanner(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.8f)), contentAlignment = Alignment.Center) {
        Card(modifier = Modifier.padding(24.dp).fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Error, null, tint = WrongRed, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(8.dp))
                Text("Tarama Hatası", fontWeight = FontWeight.Bold)
                Text(message, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.7f))
                Spacer(Modifier.height(16.dp))
                Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = Primary)) {
                    Text("Tekrar Dene")
                }
            }
        }
    }
}

@Composable
private fun CaptureButton(enabled: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(72.dp)
            .background(if (enabled) Color.White else Color.Gray, CircleShape)
            .border(4.dp, Color.White.copy(0.5f), CircleShape)
    ) {
        Icon(Icons.Default.Camera, "Tara",
            tint = if (enabled) Primary else Color.DarkGray,
            modifier = Modifier.size(36.dp))
    }
}
