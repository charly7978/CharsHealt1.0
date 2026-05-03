package com.example.myapplication.ui

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
    ) {
        // === HEADER ===
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "CHARS HEALTH | PRO-DIAGNÓSTICO",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "ECG/PPG MONITOR",
                    color = Color.Gray,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            StatusIndicator(
                fingerDetected = uiState.fingerDetected,
                status = uiState.arrhythmiaStatus
            )
        }

        // === MONITOR PRINCIPAL (60% de pantalla) ===
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.6f)
                .background(MedicalDarkGray)
        ) {
            // Grid y Gráfico
            MedicalGrid()
            PPGSweepGraph(
                points = viewModel.ppgPoints,
                peaks = viewModel.peakFlags,
                sweepIndex = viewModel.sweepIndex,
                maxPoints = 900
            )

            // Overlays de métricas (estilo Philips IntelliVue)
            Row(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Columna Izquierda: BPM y SpO2
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // BPM
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "FC",
                                color = NeonGreen,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            HeartBeatIndicator(uiState.lastPeakTimestamp)
                        }
                        Text(
                            text = if (uiState.isStabilityReached) "${uiState.bpm}" else "- - -",
                            color = NeonGreen,
                            fontSize = 64.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // SpO2
                    Column {
                        Text(
                            text = "SpO2 %",
                            color = NeonCyan,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = if (uiState.isStabilityReached && uiState.spo2 > 0) "${uiState.spo2}" else "- -",
                            color = NeonCyan,
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Columna Derecha: BP, FR, Calidad
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // NIBP (Presión)
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "NIBP mmHg",
                            color = BloodRed,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = if (uiState.isStabilityReached && uiState.bloodPressureSys > 0) 
                                "${uiState.bloodPressureSys}/${uiState.bloodPressureDia}" else "- -/- -",
                            color = BloodRed,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "ESTIMADO (SDPPG)",
                            color = BloodRed.copy(alpha=0.6f),
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // RESP (FR)
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "RESP rpm",
                            color = WarningYellow,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = if (uiState.respiratoryRate > 0) "${uiState.respiratoryRate}" else "- -",
                            color = WarningYellow,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
            
            // Indicador de Calidad Visual (SQI)
            SQIBar(
                sqi = uiState.sqi,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            )
        }

        // === SECCIÓN INFERIOR (Métricas extendidas y cámara) ===
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.4f)
                .padding(12.dp)
        ) {
            // Ritmo y PI
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = uiState.arrhythmiaStatus,
                    color = when {
                        uiState.arrhythmiaStatus.contains("NORMAL") -> NeonGreen
                        uiState.arrhythmiaStatus.contains("ESPERANDO") ||
                            uiState.arrhythmiaStatus.contains("ANALIZANDO") || 
                            uiState.arrhythmiaStatus.contains("ADQUIRIENDO") -> Color.Gray
                        else -> BloodRed
                    },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
                
                Text(
                    text = "PI: ${if (uiState.perfusionIndex > 0) "%.1f".format(uiState.perfusionIndex) else "- -"}%",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            // HRV Cards
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VitalSignCard(
                    label = "SDNN",
                    value = if (uiState.sdnn > 0) "%.1f".format(uiState.sdnn) else "-",
                    unit = "ms",
                    color = Purple,
                    modifier = Modifier.weight(1f)
                )
                VitalSignCard(
                    label = "RMSSD",
                    value = if (uiState.rmssd > 0) "%.1f".format(uiState.rmssd) else "-",
                    unit = "ms",
                    color = Magenta,
                    modifier = Modifier.weight(1f)
                )
                VitalSignCard(
                    label = "LF/HF",
                    value = if (uiState.lfhfRatio > 0) "%.2f".format(uiState.lfhfRatio) else "-",
                    unit = "",
                    color = LfHfCyan,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Cámara y Mensajes
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .background(Color(0xFF222222), RoundedCornerShape(8.dp))
                        .padding(2.dp)
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
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = if (uiState.fingerDetected)
                            "ADQUISICIÓN ESTABLE"
                        else
                            "CUBRA LA CÁMARA Y EL FLASH COMPLETAMENTE",
                        color = if (uiState.fingerDetected) NeonGreen else WarningYellow,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Mantenga el dedo firme sin presionar fuerte",
                        color = Color.Gray,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// === COMPONENTES ===

@Composable
fun HeartBeatIndicator(lastPeakTimestamp: Long) {
    val transition = updateTransition(targetState = lastPeakTimestamp, label = "beat")
    val alpha by transition.animateFloat(
        transitionSpec = {
            if (targetState > initialState) {
                // Aparece instantáneamente en el pico
                tween(durationMillis = 0)
            } else {
                // Desaparece lentamente
                tween(durationMillis = 500, easing = LinearOutSlowInEasing)
            }
        },
        label = "alpha"
    ) { state ->
        if (System.currentTimeMillis() - state < 50) 1f else 0.2f
    }

    Box(
        modifier = Modifier
            .size(12.dp)
            .background(NeonGreen.copy(alpha = alpha), RoundedCornerShape(6.dp))
    )
}

@Composable
fun StatusIndicator(fingerDetected: Boolean, status: String) {
    val color = when {
        !fingerDetected -> WarningYellow
        status.contains("NORMAL") -> NeonGreen
        status.contains("ANALIZANDO") || status.contains("ESPERANDO") || status.contains("ADQUIRIENDO") -> Color.Gray
        else -> BloodRed
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(Color(0xFF222222), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Box(modifier = Modifier.size(8.dp).background(color, RoundedCornerShape(4.dp)))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = if (fingerDetected) "SENSOR ACTIVO" else "SIN DEDO",
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun MedicalGrid() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        // Calibración típica: 25mm/s. 
        // 1 cuadro grande (5mm) = 0.2s. 1 cuadro pequeño (1mm) = 0.04s.
        val stepSmall = 10.dp.toPx()
        val stepLarge = 50.dp.toPx()

        for (x in 0 until (size.width / stepSmall).toInt() + 1) {
            drawLine(Color(0xFF1E1E1E), Offset(x * stepSmall, 0f), Offset(x * stepSmall, size.height), 1f)
        }
        for (y in 0 until (size.height / stepSmall).toInt() + 1) {
            drawLine(Color(0xFF1E1E1E), Offset(0f, y * stepSmall), Offset(size.width, y * stepSmall), 1f)
        }
        for (x in 0 until (size.width / stepLarge).toInt() + 1) {
            drawLine(Color(0xFF2A2A2A), Offset(x * stepLarge, 0f), Offset(x * stepLarge, size.height), 2f)
        }
        for (y in 0 until (size.height / stepLarge).toInt() + 1) {
            drawLine(Color(0xFF2A2A2A), Offset(0f, y * stepLarge), Offset(size.width, y * stepLarge), 2f)
        }
    }
}

@Composable
fun PPGSweepGraph(points: List<Float>, peaks: List<Boolean>, sweepIndex: Int, maxPoints: Int) {
    Canvas(modifier = Modifier.fillMaxSize().padding(vertical = 16.dp)) {
        if (points.size < 2) return@Canvas
        val w = size.width
        val h = size.height
        val n = points.size
        val gap = (maxPoints * 0.03).toInt() // 3% gap de borrado
        val writePos = sweepIndex % maxPoints

        // Descartar extremos ruidosos iniciales
        val validPoints = points.filter { !it.isNaN() }
        val minVal = validPoints.minOrNull() ?: -1f
        val maxVal = validPoints.maxOrNull() ?: 1f
        val range = (maxVal - minVal).coerceAtLeast(0.0001f)

        fun xOf(i: Int) = i * (w / maxPoints.toFloat())
        fun yOf(v: Float) = h - ((v - minVal) / range) * h

        for (i in 1 until n - 1) {
            val dist = ((writePos - i + maxPoints) % maxPoints)
            if (dist < gap) continue

            val alpha = (1f - dist.toFloat() / maxPoints).coerceIn(0.15f, 1f)
            
            val x0 = xOf(i - 1); val y0 = yOf(points[i - 1])
            val x1 = xOf(i); val y1 = yOf(points[i])
            val x2 = xOf(i + 1); val y2 = yOf(points[i + 1])
            
            // Punto medio para suavizado Bézier cuadrático continuo
            val xc1 = (x0 + x1) / 2
            val yc1 = (y0 + y1) / 2
            val xc2 = (x1 + x2) / 2
            val yc2 = (y1 + y2) / 2

            val path = Path().apply {
                moveTo(xc1, yc1)
                quadraticBezierTo(x1, y1, xc2, yc2)
            }

            drawPath(
                path = path,
                color = NeonGreen.copy(alpha = alpha),
                style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
            )

            // Marcador de pico (Triángulo invertido)
            if (i < peaks.size && peaks[i]) {
                val peakPath = Path().apply {
                    moveTo(x1, y1 - 15.dp.toPx())
                    lineTo(x1 - 6.dp.toPx(), y1 - 25.dp.toPx())
                    lineTo(x1 + 6.dp.toPx(), y1 - 25.dp.toPx())
                    close()
                }
                drawPath(peakPath, color = Color.White)
            }
        }

        if (n > 0) {
            val sweepX = xOf(writePos % n.coerceAtLeast(1))
            drawLine(
                color = NeonGreen.copy(alpha = 0.9f),
                start = Offset(sweepX, 0f),
                end = Offset(sweepX, h),
                strokeWidth = 2.dp.toPx()
            )
        }
    }
}

@Composable
fun SQIBar(sqi: Float, modifier: Modifier = Modifier) {
    val barColor = when {
        sqi > 0.6f -> NeonGreen
        sqi > 0.3f -> WarningYellow
        else -> BloodRed
    }
    
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "SQI",
            color = Color.White,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(40.dp)
                .background(Color(0xFF333333))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(sqi.coerceIn(0f, 1f))
                    .background(barColor)
                    .align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
fun VitalSignCard(label: String, value: String, unit: String, color: Color, modifier: Modifier) {
    Surface(
        modifier = modifier.height(64.dp),
        color = Color(0xFF111111),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                color = color,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
                if (unit.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = unit,
                        color = Color.Gray,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }
            }
        }
    }
}
