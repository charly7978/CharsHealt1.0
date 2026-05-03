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
            .padding(12.dp)
    ) {
        // CABECERA: SISTEMA DE DIAGNÓSTICO
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "CHARS HEALTH | PRO-DIAGNOSTIC",
                    color = NeonCyan,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "REAL-TIME ARRHYTHMIA & HRV ANALYSIS",
                    color = Color.Gray,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            
            StatusIndicator(status = uiState.arrhythmiaStatus)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // MONITOR PRINCIPAL
        Card(
            modifier = Modifier.fillMaxWidth().height(220.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1F1F1F)),
            shape = RoundedCornerShape(4.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                MedicalGrid()
                PPGGraph(points = viewModel.ppgPoints)
                
                // Readout Digital
                Column(
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(text = "HR (BPM)", color = NeonGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = if(uiState.sqi > 0.3f) "${uiState.bpm}" else "--",
                        color = NeonGreen,
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "SQI: ${(uiState.sqi * 100).toInt()}%",
                        color = if(uiState.sqi > 0.6f) NeonGreen else BloodRed,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // GRID DE MÉTRICAS AVANZADAS
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VitalSignPanel(label = "SPO2 %", value = if(uiState.sqi > 0.4f) "${uiState.spo2}" else "--", color = NeonCyan, modifier = Modifier.weight(1f))
            VitalSignPanel(label = "BP (mmHg)", value = if(uiState.sqi > 0.5f) "${uiState.bloodPressureSys}/${uiState.bloodPressureDia}" else "--/--", color = BloodRed, modifier = Modifier.weight(1f))
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // HRV PANEL (NUEVO)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VitalSignPanel(
                label = "SDNN (ms)", 
                value = if(uiState.sdnn > 0) "%.1f".format(uiState.sdnn) else "--", 
                color = WarningYellow, 
                modifier = Modifier.weight(1f)
            )
            VitalSignPanel(
                label = "RMSSD (ms)", 
                value = if(uiState.rmssd > 0) "%.1f".format(uiState.rmssd) else "--", 
                color = Color.Magenta, 
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ANALIZADOR DE RITMO
        Surface(
            modifier = Modifier.fillMaxWidth().height(60.dp),
            color = Color(0xFF0A0A0A),
            shape = RoundedCornerShape(4.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1A1A1A))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "RHYTHM:", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = uiState.arrhythmiaStatus,
                    color = if(uiState.arrhythmiaStatus.contains("NORMAL")) NeonGreen else if(uiState.arrhythmiaStatus.contains("SCANNING")) Color.Gray else BloodRed,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // SENSOR & FEEDBACK
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Box(
                modifier = Modifier.size(80.dp).background(MedicalDarkGray, RoundedCornerShape(40.dp)).padding(4.dp)
            ) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = androidx.camera.core.Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                                .also {
                                    it.setAnalyzer(executor, PPGAnalyzer(
                                        onPointProcessed = { point -> viewModel.addPpgPoint(point) },
                                        onResultUpdate = { bpm, spo2, breath, sys, dia, sqi, sdnn, rmssd, lfhf, arrhythmia ->
                                            viewModel.updateResults(bpm, spo2, breath, sys, dia, sqi, sdnn, rmssd, lfhf, arrhythmia)
                                        }
                                    ))
                                }
                            try {
                                cameraProvider.unbindAll()
                                val camera = cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis)
                                camera.cameraControl.enableTorch(true)
                            } catch (e: Exception) { e.printStackTrace() }
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "BIOMETRIC ACQUISITION\nIN PROGRESS...",
                color = Color.Gray,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun StatusIndicator(status: String) {
    val color = if(status.contains("NORMAL")) NeonGreen else if(status.contains("SCANNING")) Color.Gray else BloodRed
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).background(color, RoundedCornerShape(4.dp)))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = "LIVE", color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun MedicalGrid() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val step = 30.dp.toPx()
        for (x in 0 until (size.width / step).toInt()) drawLine(color = Color(0xFF121212), start = Offset(x * step, 0f), end = Offset(x * step, size.height), strokeWidth = 1f)
        for (y in 0 until (size.height / step).toInt()) drawLine(color = Color(0xFF121212), start = Offset(0f, y * step), end = Offset(size.width, y * step), strokeWidth = 1f)
    }
}

@Composable
fun PPGGraph(points: List<Float>) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        if (points.size < 2) return@Canvas
        val path = Path()
        val width = size.width
        val height = size.height
        val minVal = points.minOrNull() ?: 0f
        val maxVal = points.maxOrNull() ?: 1f
        val range = (maxVal - minVal).coerceAtLeast(0.00001f)
        points.forEachIndexed { index, value ->
            val x = index * (width / 150f)
            val y = height / 2 - ((value - minVal) / range - 0.5f) * height * 0.8f
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path = path, color = NeonGreen, style = Stroke(width = 2.dp.toPx()))
    }
}

@Composable
fun VitalSignPanel(label: String, value: String, color: Color, modifier: Modifier) {
    Surface(
        modifier = modifier.height(80.dp),
        color = Color(0xFF0A0A0A),
        shape = RoundedCornerShape(2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1A1A1A))
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(text = label, color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Spacer(modifier = Modifier.weight(1f))
            Text(text = value, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
        }
    }
}
