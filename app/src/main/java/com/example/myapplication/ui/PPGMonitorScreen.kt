package com.example.myapplication.ui

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.analyzer.PPGAnalyzer
import com.example.myapplication.viewmodel.VitalSignsViewModel
import java.util.concurrent.Executors
import com.example.myapplication.ui.theme.*

@Composable
fun PPGMonitorScreen(
    viewModel: VitalSignsViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }

    // Liberar recursos al salir
    DisposableEffect(Unit) {
        onDispose {
            executor.shutdown()
            try { cameraProviderFuture.get().unbindAll() } catch (_: Exception) {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MedicalBlack)
            .padding(12.dp)
    ) {
        // === CABECERA ===
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "CHARS HEALTH | PRO-DIAGNÓSTICO",
                    color = NeonCyan,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "ANÁLISIS CARDÍACO Y HRV EN TIEMPO REAL",
                    color = Color.Gray,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            StatusIndicator(
                fingerDetected = uiState.fingerDetected,
                status = uiState.arrhythmiaStatus
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // === MONITOR PPG PRINCIPAL ===
        Card(
            modifier = Modifier.fillMaxWidth().height(220.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1A1A1A)),
            shape = RoundedCornerShape(4.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                MedicalGrid()
                PPGSweepGraph(
                    points = viewModel.ppgPoints,
                    peaks = viewModel.peakFlags,
                    sweepIndex = viewModel.sweepIndex,
                    maxPoints = 300
                )

                // BPM Digital
                Column(
                    modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "FC (LPM)",
                        color = NeonGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = if (uiState.sqi > 0.3f && uiState.bpm > 0) "${uiState.bpm}" else "--",
                        color = NeonGreen,
                        fontSize = 52.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "SQI: ${(uiState.sqi * 100).toInt()}%",
                        color = if (uiState.sqi > 0.5f) NeonGreen else BloodRed,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // === MÉTRICAS VITALES ===
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            VitalSignPanel(
                label = "SpO2 %",
                value = if (uiState.sqi > 0.4f && uiState.spo2 > 0) "${uiState.spo2}" else "--",
                color = NeonCyan,
                modifier = Modifier.weight(1f)
            )
            VitalSignPanel(
                label = "PA (mmHg)",
                value = if (uiState.sqi > 0.5f && uiState.bloodPressureSys > 0) "${uiState.bloodPressureSys}/${uiState.bloodPressureDia}" else "--/--",
                color = BloodRed,
                modifier = Modifier.weight(1f)
            )
            VitalSignPanel(
                label = "FR (rpm)",
                value = if (uiState.respiratoryRate > 0) "${uiState.respiratoryRate}" else "--",
                color = WarningYellow,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // === HRV ===
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            VitalSignPanel(
                label = "SDNN (ms)",
                value = if (uiState.sdnn > 0) "%.1f".format(uiState.sdnn) else "--",
                color = Color(0xFFAA80FF),
                modifier = Modifier.weight(1f)
            )
            VitalSignPanel(
                label = "RMSSD (ms)",
                value = if (uiState.rmssd > 0) "%.1f".format(uiState.rmssd) else "--",
                color = Color.Magenta,
                modifier = Modifier.weight(1f)
            )
            VitalSignPanel(
                label = "LF/HF",
                value = if (uiState.lfhfRatio > 0) "%.2f".format(uiState.lfhfRatio) else "--",
                color = Color(0xFF4FC3F7),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // === PANEL DE RITMO ===
        Surface(
            modifier = Modifier.fillMaxWidth().height(50.dp),
            color = Color(0xFF0A0A0A),
            shape = RoundedCornerShape(4.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1A1A1A))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RITMO:",
                    color = Color.Gray,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = uiState.arrhythmiaStatus,
                    color = when {
                        uiState.arrhythmiaStatus.contains("NORMAL") -> NeonGreen
                        uiState.arrhythmiaStatus.contains("ESPERANDO") ||
                            uiState.arrhythmiaStatus.contains("ANALIZANDO") -> Color.Gray
                        else -> BloodRed
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.weight(1f))
                if (uiState.perfusionIndex > 0) {
                    Text(
                        text = "IP: ${"%.2f".format(uiState.perfusionIndex)}%",
                        color = Color.Gray,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // === SENSOR DE CÁMARA ===
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(MedicalDarkGray, RoundedCornerShape(36.dp))
                    .padding(4.dp)
            ) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = androidx.camera.core.Preview.Builder()
                                .build()
                                .also { it.setSurfaceProvider(previewView.surfaceProvider) }
                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                                .build()
                                .also {
                                    it.setAnalyzer(executor, PPGAnalyzer(
                                        onSignalPoint = { value, isPeak ->
                                            viewModel.addPpgPoint(value, isPeak)
                                        },
                                        onVitalsUpdate = { result ->
                                            viewModel.updateResults(result)
                                        }
                                    ))
                                }
                            try {
                                cameraProvider.unbindAll()
                                val camera = cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview, imageAnalysis
                                )
                                camera.cameraControl.enableTorch(true)
                            } catch (e: Exception) { e.printStackTrace() }
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = if (uiState.fingerDetected)
                        "ADQUISICIÓN BIOMÉTRICA\nEN CURSO..."
                    else
                        "COLOQUE SU DEDO\nSOBRE EL SENSOR Y FLASH",
                    color = if (uiState.fingerDetected) NeonGreen else WarningYellow,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

// === INDICADOR DE ESTADO ===

@Composable
fun StatusIndicator(fingerDetected: Boolean, status: String) {
    val color = when {
        !fingerDetected -> WarningYellow
        status.contains("NORMAL") -> NeonGreen
        status.contains("ANALIZANDO") || status.contains("ESPERANDO") -> Color.Gray
        else -> BloodRed
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).background(color, RoundedCornerShape(4.dp)))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = if (fingerDetected) "EN VIVO" else "INACTIVO",
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

// === CUADRÍCULA MÉDICA ===

@Composable
fun MedicalGrid() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val stepSmall = 15.dp.toPx()
        val stepLarge = 75.dp.toPx()

        // Cuadrícula menor
        for (x in 0 until (size.width / stepSmall).toInt() + 1) {
            drawLine(Color(0xFF0D0D0D), Offset(x * stepSmall, 0f), Offset(x * stepSmall, size.height), 0.5f)
        }
        for (y in 0 until (size.height / stepSmall).toInt() + 1) {
            drawLine(Color(0xFF0D0D0D), Offset(0f, y * stepSmall), Offset(size.width, y * stepSmall), 0.5f)
        }
        // Cuadrícula mayor
        for (x in 0 until (size.width / stepLarge).toInt() + 1) {
            drawLine(Color(0xFF1A1A1A), Offset(x * stepLarge, 0f), Offset(x * stepLarge, size.height), 1f)
        }
        for (y in 0 until (size.height / stepLarge).toInt() + 1) {
            drawLine(Color(0xFF1A1A1A), Offset(0f, y * stepLarge), Offset(size.width, y * stepLarge), 1f)
        }
    }
}

// === GRÁFICO PPG CON LÍNEA DE BARRIDO ===

@Composable
fun PPGSweepGraph(
    points: List<Float>,
    peaks: List<Boolean>,
    sweepIndex: Int,
    maxPoints: Int
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        if (points.size < 2) return@Canvas
        val w = size.width
        val h = size.height
        val n = points.size
        val gap = 8 // Puntos de gap en la línea de barrido
        val writePos = sweepIndex % maxPoints

        // Calcular rango dinámico
        val minVal = points.minOrNull() ?: 0f
        val maxVal = points.maxOrNull() ?: 1f
        val range = (maxVal - minVal).coerceAtLeast(0.0001f)

        fun xOf(i: Int) = i * (w / maxPoints.toFloat())
        fun yOf(v: Float) = h / 2f - ((v - minVal) / range - 0.5f) * h * 0.8f

        // Dibujar segmentos con fade basado en distancia al sweep
        for (i in 1 until n) {
            // Distancia circular al sweep
            val dist = ((writePos - i + maxPoints) % maxPoints)
            // Saltar gap justo después del sweep
            if (dist < gap) continue

            val alpha = (1f - dist.toFloat() / maxPoints).coerceIn(0.15f, 1f)
            val x0 = xOf(i - 1); val y0 = yOf(points[i - 1])
            val x1 = xOf(i); val y1 = yOf(points[i])

            // Glow: trazo ancho semi-transparente
            drawLine(
                color = NeonGreen.copy(alpha = alpha * 0.3f),
                start = Offset(x0, y0),
                end = Offset(x1, y1),
                strokeWidth = 6.dp.toPx(),
                cap = StrokeCap.Round
            )
            // Trazo principal
            drawLine(
                color = NeonGreen.copy(alpha = alpha),
                start = Offset(x0, y0),
                end = Offset(x1, y1),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Marcadores de picos
            if (i < peaks.size && peaks[i]) {
                drawCircle(
                    color = Color.White,
                    radius = 3.dp.toPx(),
                    center = Offset(x1, y1)
                )
                drawCircle(
                    color = NeonGreen.copy(alpha = 0.5f),
                    radius = 6.dp.toPx(),
                    center = Offset(x1, y1)
                )
            }
        }

        // Línea de barrido vertical
        if (n > 0) {
            val sweepX = xOf(writePos % n.coerceAtLeast(1))
            drawLine(
                color = Color.White.copy(alpha = 0.6f),
                start = Offset(sweepX, 0f),
                end = Offset(sweepX, h),
                strokeWidth = 1.5.dp.toPx()
            )
        }
    }
}

// === PANEL DE SIGNOS VITALES ===

@Composable
fun VitalSignPanel(label: String, value: String, color: Color, modifier: Modifier) {
    Surface(
        modifier = modifier.height(72.dp),
        color = Color(0xFF080808),
        shape = RoundedCornerShape(3.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1A1A1A))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Text(
                text = label,
                color = color,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = value,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
