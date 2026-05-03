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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MedicalBlack)
            .padding(16.dp)
    ) {
        // Título Estilo Hospitalario
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "CHARS HEALTH",
                    color = NeonCyan,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "BIOSIGNAL ACQUISITION UNIT v1.0",
                    color = Color.Gray,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Text(
                text = "SYSTEM READY",
                color = NeonGreen,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // MONITOR CARDIACO PRINCIPAL (EGG/PPG)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1F1F1F)),
            shape = RoundedCornerShape(4.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Dibujamos el Grid de fondo
                MedicalGrid()
                
                // Dibujamos la Onda PPG
                PPGGraph(points = viewModel.ppgPoints)
                
                // Overlay de Información de Tiempo Real
                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "HEART RATE",
                        color = NeonGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${uiState.bpm}",
                        color = NeonGreen,
                        fontSize = 54.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "BPM",
                        color = NeonGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // PANELES LATERALES DE SIGNOS VITALES
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            VitalSignPanel(
                label = "SPO2 %",
                value = if(uiState.spo2 > 0) "${uiState.spo2}" else "--",
                unit = "Oxygen Sat.",
                color = NeonCyan,
                modifier = Modifier.weight(1f)
            )
            VitalSignPanel(
                label = "NIBP mmHg",
                value = if(uiState.bloodPressureSys > 0) "${uiState.bloodPressureSys}/${uiState.bloodPressureDia}" else "--/--",
                unit = "Art. Pressure",
                color = BloodRed,
                modifier = Modifier.weight(1f)
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            VitalSignPanel(
                label = "RESP br/min",
                value = if(uiState.respiratoryRate > 0) "${uiState.respiratoryRate}" else "--",
                unit = "Breath Rate",
                color = WarningYellow,
                modifier = Modifier.weight(1f)
            )
            VitalSignPanel(
                label = "PI %",
                value = "4.2", // Perfusion Index (calculado en fase 2)
                unit = "Perfusion",
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // AREA DE CAPTACION DEL SENSOR
        Box(
            modifier = Modifier
                .size(120.dp)
                .align(Alignment.CenterHorizontally)
                .background(MedicalDarkGray, RoundedCornerShape(60.dp))
                .padding(4.dp)
        ) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = androidx.camera.core.Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .setTargetRotation(previewView.display.rotation)
                            .build()
                            .also {
                                it.setAnalyzer(executor, PPGAnalyzer(
                                    onPointProcessed = { point ->
                                        viewModel.addPpgPoint(point)
                                    },
                                    onResultUpdate = { bpm, spo2, breath ->
                                        viewModel.updateResults(bpm, spo2, breath)
                                    }
                                ))
                            }

                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                        try {
                            cameraProvider.unbindAll()
                            val camera = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageAnalysis
                            )
                            if (camera.cameraInfo.hasFlashUnit()) {
                                camera.cameraControl.enableTorch(true)
                            }
                        } catch (e: Exception) { e.printStackTrace() }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        
        Text(
            text = "SCANNING BIOMETRIC DATA...",
            color = Color.Gray,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp)
        )
    }
}

@Composable
fun MedicalGrid() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val stepX = 40.dp.toPx()
        val stepY = 40.dp.toPx()
        
        // Líneas Verticales
        for (x in 0 until (size.width / stepX).toInt()) {
            drawLine(
                color = Color(0xFF1A1A1A),
                start = Offset(x * stepX, 0f),
                end = Offset(x * stepX, size.height),
                strokeWidth = 1f
            )
        }
        // Líneas Horizontales
        for (y in 0 until (size.height / stepY).toInt()) {
            drawLine(
                color = Color(0xFF1A1A1A),
                start = Offset(0f, y * stepY),
                end = Offset(size.width, y * stepY),
                strokeWidth = 1f
            )
        }
    }
}

@Composable
fun PPGGraph(points: List<Float>) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        if (points.size < 2) return@Canvas
        
        val path = Path()
        val width = size.width
        val height = size.height
        
        // Auto-scaling dinámico para máxima resolución visual
        val minVal = points.minOrNull() ?: 0f
        val maxVal = points.maxOrNull() ?: 1f
        val range = (maxVal - minVal).coerceAtLeast(0.01f)

        points.forEachIndexed { index, value ->
            val x = index * (width / 150f)
            val y = height - ((value - minVal) / range) * height
            
            // Suavizado básico mediante interpolación de puntos
            if (index == 0) path.moveTo(x, y)
            else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = NeonGreen,
            style = Stroke(width = 2.dp.toPx())
        )
    }
}

@Composable
fun VitalSignPanel(label: String, value: String, unit: String, color: Color, modifier: Modifier) {
    Surface(
        modifier = modifier.height(110.dp),
        color = Color(0xFF0F0F0F),
        shape = RoundedCornerShape(4.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1F1F1F))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Spacer(modifier = Modifier.weight(1f))
            Text(text = value, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
            Text(text = unit, color = Color.Gray, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        }
    }
}
