package com.example.myapplication.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

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
    val arrhythmiaStatus: String = "SCANNING",
    val isStabilityReached: Boolean = false
)

class VitalSignsViewModel : ViewModel() {
    
    private val _uiState = MutableStateFlow(VitalSignsState())
    val uiState = _uiState.asStateFlow()

    val ppgPoints = mutableStateListOf<Float>()
    private val maxPoints = 150

    fun addPpgPoint(value: Double) {
        ppgPoints.add(value.toFloat())
        if (ppgPoints.size > maxPoints) {
            ppgPoints.removeAt(0)
        }
    }

    fun updateResults(
        bpm: Int, 
        spo2: Int, 
        breathRate: Int, 
        sys: Int, 
        dia: Int, 
        sqi: Float,
        sdnn: Double = 0.0,
        rmssd: Double = 0.0,
        lfhf: Double = 0.0,
        arrhythmia: String = "NORMAL"
    ) {
        _uiState.value = _uiState.value.copy(
            bpm = bpm,
            spo2 = spo2,
            respiratoryRate = breathRate,
            bloodPressureSys = sys,
            bloodPressureDia = dia,
            sqi = sqi,
            sdnn = sdnn,
            rmssd = rmssd,
            lfhfRatio = lfhf,
            arrhythmiaStatus = arrhythmia,
            isStabilityReached = sqi > 0.4f && bpm > 0
        )
    }
}
