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
    val isStabilityReached: Boolean = false
)

class VitalSignsViewModel : ViewModel() {
    
    private val _uiState = MutableStateFlow(VitalSignsState())
    val uiState = _uiState.asStateFlow()

    // Flujo de datos para el gráfico en tiempo real
    val ppgPoints = mutableStateListOf<Float>()
    private val maxPoints = 150

    fun addPpgPoint(value: Double) {
        // Normalización básica para visualización (esto se mejorará con auto-scaling)
        ppgPoints.add(value.toFloat())
        if (ppgPoints.size > maxPoints) {
            ppgPoints.removeAt(0)
        }
    }

    fun updateResults(bpm: Int, spo2: Int, breathRate: Int) {
        // Cálculo de presión arterial (PA) basado en morfología
        // Algoritmo PTT (Pulse Transit Time) estimado
        val sys = 110 + (bpm / 10) // Placeholder matemático inicial
        val dia = 70 + (bpm / 20)
        
        _uiState.value = _uiState.value.copy(
            bpm = bpm,
            spo2 = spo2,
            respiratoryRate = breathRate,
            bloodPressureSys = sys,
            bloodPressureDia = dia,
            isStabilityReached = bpm > 0
        )
    }
}
