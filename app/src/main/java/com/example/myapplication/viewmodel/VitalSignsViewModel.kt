package com.example.myapplication.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.analyzer.PPGAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class VitalSignsState(
    val bpm: Int = 0,
    val spo2: Int = 0,
    val respiratoryRate: Int = 0,
    val bloodPressureSys: Int = 0,
    val bloodPressureDia: Int = 0,
    val sqi: Float = 0f,
    val sdnn: Double = 0.0,
    val rmssd: Double = 0.0,
    val lfhfRatio: Double = 0.0,
    val arrhythmiaStatus: String = "ESPERANDO DEDO...",
    val fingerDetected: Boolean = false,
    val perfusionIndex: Double = 0.0,
    val isStabilityReached: Boolean = false,
    val lastPeakTimestamp: Long = 0L
)

class VitalSignsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(VitalSignsState())
    val uiState = _uiState.asStateFlow()

    // Buffer de puntos para la gráfica — 600 puntos (~20s a 30fps)
    val ppgPoints = mutableStateListOf<Float>()
    val peakFlags = mutableStateListOf<Boolean>()
    private val maxPoints = 600 
    var sweepIndex = 0
        private set

    // Smoothing de BPM (media de últimas 5 lecturas)
    private val bpmHistory = mutableListOf<Int>()
    private val smoothWindow = 5
    
    private var lastPeakTime = 0L

    fun addPpgPoint(value: Double, isPeak: Boolean) {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            if (isPeak) lastPeakTime = System.currentTimeMillis()
            
            if (ppgPoints.size >= maxPoints) {
                ppgPoints[sweepIndex % maxPoints] = value.toFloat()
                if (peakFlags.size > sweepIndex % maxPoints) {
                    peakFlags[sweepIndex % maxPoints] = isPeak
                }
            } else {
                ppgPoints.add(value.toFloat())
                peakFlags.add(isPeak)
            }
            sweepIndex++
        }
    }

    fun updateResults(result: PPGAnalyzer.VitalsResult) {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            // Smoothing BPM
            if (result.bpm > 0) {
                bpmHistory.add(result.bpm)
                if (bpmHistory.size > smoothWindow) bpmHistory.removeAt(0)
            }
            val smoothBpm = if (bpmHistory.isNotEmpty()) bpmHistory.average().toInt() else 0

            _uiState.value = VitalSignsState(
                bpm = smoothBpm,
                spo2 = result.spo2,
                respiratoryRate = result.respiratoryRate,
                bloodPressureSys = result.systolic,
                bloodPressureDia = result.diastolic,
                sqi = result.sqi,
                sdnn = result.sdnn,
                rmssd = result.rmssd,
                lfhfRatio = result.lfhfRatio,
                arrhythmiaStatus = result.arrhythmiaStatus,
                fingerDetected = result.fingerDetected,
                perfusionIndex = result.perfusionIndex,
                isStabilityReached = result.sqi > 0.4f && smoothBpm > 0,
                lastPeakTimestamp = lastPeakTime
            )
        }
    }
}
