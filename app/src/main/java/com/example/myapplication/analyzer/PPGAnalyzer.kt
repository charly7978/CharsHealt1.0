package com.example.myapplication.analyzer

import android.graphics.ImageFormat
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics
import org.apache.commons.math3.transform.DftNormalization
import org.apache.commons.math3.transform.FastFourierTransformer
import org.apache.commons.math3.transform.TransformType
import java.nio.ByteBuffer
import kotlin.math.*

/**
 * PPGAnalyzer — Motor de Procesamiento Biomédico Profesional.
 */
class PPGAnalyzer(
    private val onSignalPoint: (filteredValue: Double, isPeak: Boolean) -> Unit,
    private val onVitalsUpdate: (VitalsResult) -> Unit
) : ImageAnalysis.Analyzer {

    data class VitalsResult(
        val bpm: Int,
        val spo2: Int,
        val respiratoryRate: Int,
        val systolic: Int,
        val diastolic: Int,
        val sqi: Float,
        val sdnn: Double,
        val rmssd: Double,
        val lfhfRatio: Double,
        val arrhythmiaStatus: String,
        val fingerDetected: Boolean,
        val perfusionIndex: Double
    )

    companion object {
        const val FS = 30.0
        const val WINDOW = 256
        const val POS_WIN = 48 // 1.6s a 30fps
        const val REFRACTORY_SAMPLES = 9 // 300ms a 30fps
        const val MIN_LUMA_FINGER = 150.0 // Umbral histéresis
        const val START_LUMA_FINGER = 180.0
        const val MAX_NN = 100
    }

    // Buffers de canales RGB y luminancia
    private val redBuf = CircularBuffer(WINDOW)
    private val greenBuf = CircularBuffer(WINDOW)
    private val blueBuf = CircularBuffer(WINDOW)
    private val lumaBuf = CircularBuffer(WINDOW)

    // Filtro Butterworth bandpass 0.5–4.0 Hz para señal cardíaca
    private val cardiacFilter = CascadedFilter.butterworthBandpass(0.5, 4.0, FS)

    // Estado de detección de picos
    private var lastPeakIdx = -REFRACTORY_SAMPLES
    private var sampleIdx = 0L
    private var adaptiveThr = 0.0
    private val thrAlpha = 0.02

    // Intervalos NN para HRV
    private val nnIntervals = mutableListOf<Double>()
    private var lastPeakTimeMs = 0L

    // Historial para frecuencia respiratoria y BP
    private val peakAmplitudes = mutableListOf<Double>()
    private val ibiHistory = mutableListOf<Double>()
    private val bpSysHistory = mutableListOf<Double>()
    private val bpDiaHistory = mutableListOf<Double>()

    // Señal POS filtrada
    private val posBuffer = CircularBuffer(WINDOW)

    // Detección de dedo
    private var fingerDetected = false
    private var stableCount = 0

    // DC components para SpO2
    private var dcRed = 0.0
    private var dcLuma = 0.0
    private val dcAlpha = 0.005

    // POS Window state
    private val posRWindow = mutableListOf<Double>()
    private val posGWindow = mutableListOf<Double>()
    private val posBWindow = mutableListOf<Double>()
    
    private var isPosReady = false

    override fun analyze(image: ImageProxy) {
        try {
            if (image.format != ImageFormat.YUV_420_888) return

            val yPlane = image.planes[0].buffer
            val uPlane = image.planes[1].buffer
            val vPlane = image.planes[2].buffer

            val yArr = yPlane.toByteArray()
            val uArr = uPlane.toByteArray()
            val vArr = vPlane.toByteArray()

            var sumR = 0.0; var sumG = 0.0; var sumB = 0.0; var sumY = 0.0; var cnt = 0
            
            // ROI Adaptativa: 50% central
            val roiWidth = image.width / 2
            val roiHeight = image.height / 2
            val sx = image.width / 4
            val sy = image.height / 4
            val step = 4

            for (dy in 0 until roiHeight step step) {
                for (dx in 0 until roiWidth step step) {
                    val px = (sy + dy) * image.width + (sx + dx)
                    if (px >= yArr.size) continue
                    val yVal = yArr[px].toInt() and 0xFF
                    
                    val uvIdx = ((sy + dy) / 2 * (image.width / 2) + (sx + dx) / 2)
                        .coerceIn(0, uArr.size - 1)
                    val u = (uArr[uvIdx].toInt() and 0xFF) - 128
                    val v = (vArr[uvIdx].toInt() and 0xFF) - 128

                    // YUV→RGB linealizado
                    val r = ((yVal + 1.402 * v).coerceIn(0.0, 255.0) / 255.0).pow(2.2)
                    val g = ((yVal - 0.344136 * u - 0.714136 * v).coerceIn(0.0, 255.0) / 255.0).pow(2.2)
                    val b = ((yVal + 1.772 * u).coerceIn(0.0, 255.0) / 255.0).pow(2.2)

                    sumR += r; sumG += g; sumB += b; sumY += yVal
                    cnt++
                }
            }

            if (cnt == 0) return
            val avgR = sumR / cnt; val avgG = sumG / cnt; val avgB = sumB / cnt
            val avgY = sumY / cnt

            // Detección de dedo mejorada con histéresis en luminancia
            val wasDetected = fingerDetected
            if (avgY > START_LUMA_FINGER) {
                fingerDetected = true
            } else if (avgY < MIN_LUMA_FINGER) {
                fingerDetected = false
            }

            if (!fingerDetected) {
                if (wasDetected) resetState() // Reset al perder el dedo
                onSignalPoint(0.0, false)
                return
            }
            
            if (!wasDetected) {
                // Acaba de detectar el dedo
                resetState()
                fingerDetected = true
            }
            
            stableCount++

            synchronized(this) {
                redBuf.add(avgR)
                greenBuf.add(avgG)
                blueBuf.add(avgB)
                lumaBuf.add(avgY)

                // Actualizar DC
                dcRed = dcRed * (1 - dcAlpha) + avgR * dcAlpha
                dcLuma = dcLuma * (1 - dcAlpha) + avgY * dcAlpha
                if (dcRed == 0.0) { dcRed = avgR; dcLuma = avgY }

                // POS Algorithm
                val posValue = computePOS(avgR, avgG, avgB)
                
                if (isPosReady) {
                    val filtered = cardiacFilter.process(posValue)
                    posBuffer.add(filtered)

                    // Detección de picos
                    val isPeak = detectPeak(filtered)
                    onSignalPoint(filtered, isPeak)

                    // Procesar vitales si hay estabilidad
                    if (stableCount > 90) { // 3 segundos de estabilidad
                        processVitals()
                    }
                } else {
                    onSignalPoint(0.0, false)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            image.close()
        }
    }

    private fun resetState() {
        stableCount = 0
        cardiacFilter.reset()
        redBuf.clear()
        greenBuf.clear()
        blueBuf.clear()
        lumaBuf.clear()
        posBuffer.clear()
        posRWindow.clear()
        posGWindow.clear()
        posBWindow.clear()
        isPosReady = false
        nnIntervals.clear()
        ibiHistory.clear()
        peakAmplitudes.clear()
        bpSysHistory.clear()
        bpDiaHistory.clear()
        dcRed = 0.0
        dcLuma = 0.0
        lastPeakIdx = -REFRACTORY_SAMPLES
        adaptiveThr = 0.0
        onVitalsUpdate(VitalsResult(0,0,0,0,0,0f,0.0,0.0,0.0,"SIN SEÑAL",false,0.0))
    }

    // --- POS Algorithm ---

    private fun computePOS(r: Double, g: Double, b: Double): Double {
        posRWindow.add(r)
        posGWindow.add(g)
        posBWindow.add(b)
        
        if (posRWindow.size < POS_WIN) {
            return 0.0
        }
        
        if (!isPosReady && posRWindow.size == POS_WIN) {
            isPosReady = true
        }

        if (posRWindow.size > POS_WIN) {
            posRWindow.removeAt(0)
            posGWindow.removeAt(0)
            posBWindow.removeAt(0)
        }

        val mR = posRWindow.average()
        val mG = posGWindow.average()
        val mB = posBWindow.average()
        if (mR < 1e-10 || mG < 1e-10 || mB < 1e-10) return 0.0

        val nR = posRWindow.last() / mR
        val nG = posGWindow.last() / mG
        val nB = posBWindow.last() / mB

        val xs = nG - nB
        val ys = nG + nB - 2.0 * nR

        val xArr = DoubleArray(POS_WIN) { posGWindow[it] / mG - posBWindow[it] / mB }
        val yArr = DoubleArray(POS_WIN) { posGWindow[it] / mG + posBWindow[it] / mB - 2.0 * posRWindow[it] / mR }
        val stdX = stdDev(xArr)
        val stdY = stdDev(yArr)
        val alpha = if (stdY > 1e-10) stdX / stdY else 1.0

        return xs + alpha * ys
    }

    // --- Detección Adaptativa ---

    private fun detectPeak(value: Double): Boolean {
        sampleIdx++
        adaptiveThr = adaptiveThr * (1 - thrAlpha) + abs(value) * thrAlpha

        val threshold = adaptiveThr * 1.5
        val sinceLastPeak = sampleIdx - lastPeakIdx

        if (value > threshold && sinceLastPeak > REFRACTORY_SAMPLES) {
            // Confirmar que es un pico local comparando con puntos recientes es manejado por el filtro smooth
            // Simplificado a superar umbral y refractario
            lastPeakIdx = sampleIdx.toInt()
            val now = System.currentTimeMillis()
            if (lastPeakTimeMs > 0) {
                val interval = (now - lastPeakTimeMs).toDouble()
                if (interval in 300.0..1500.0) { // 40-200 BPM bounds
                    nnIntervals.add(interval)
                    ibiHistory.add(interval)
                    if (nnIntervals.size > MAX_NN) nnIntervals.removeAt(0)
                    if (ibiHistory.size > MAX_NN) ibiHistory.removeAt(0)
                }
            }
            peakAmplitudes.add(value)
            if (peakAmplitudes.size > MAX_NN) peakAmplitudes.removeAt(0)
            lastPeakTimeMs = now
            return true
        }
        return false
    }

    // --- Procesamiento de Vitales ---

    private fun processVitals() {
        val reds = redBuf.toArray()
        val lumas = lumaBuf.toArray()
        val signal = posBuffer.toArray()

        if (signal.size < WINDOW / 2) return

        val bpm = calculateBPM(signal)
        val spo2 = calculateSpO2(reds, lumas)
        val respRate = calculateRespiratoryRate()
        val bp = calculateBloodPressure(signal, bpm)
        val hrv = calculateHRV()
        val sqi = calculateSQI(signal)
        val pi = calculatePerfusionIndex(reds)
        val arrhythmia = detectArrhythmia(hrv, sqi)

        onVitalsUpdate(VitalsResult(
            bpm = bpm, spo2 = spo2, respiratoryRate = respRate,
            systolic = bp.first, diastolic = bp.second,
            sqi = sqi, sdnn = hrv.sdnn, rmssd = hrv.rmssd,
            lfhfRatio = hrv.lfhf, arrhythmiaStatus = arrhythmia,
            fingerDetected = fingerDetected, perfusionIndex = pi
        ))
    }

    // --- BPM (FFT 1024) ---

    private fun calculateBPM(signal: DoubleArray): Int {
        val n = 1024 // Zero-padding para mejor resolucion
        val padded = DoubleArray(n)
        val windowed = applyHamming(signal)
        System.arraycopy(windowed, 0, padded, 0, windowed.size.coerceAtMost(n))

        val fft = FastFourierTransformer(DftNormalization.STANDARD)
        val complex = fft.transform(padded, TransformType.FORWARD)

        var maxMag = -1.0; var peakFreq = 0.0
        for (i in 1 until n / 2) {
            val freq = i * FS / n
            if (freq in 0.7..3.5) { // 42 a 210 BPM
                val mag = sqrt(complex[i].real.pow(2) + complex[i].imaginary.pow(2))
                if (mag > maxMag) { maxMag = mag; peakFreq = freq }
            }
        }
        return (peakFreq * 60).roundToInt().coerceIn(0, 220)
    }

    // --- SpO2 Mejorado (Red vs Luma como proxy IR) ---

    private fun calculateSpO2(reds: DoubleArray, lumas: DoubleArray): Int {
        if (dcRed < 1e-10 || dcLuma < 1e-10) return 0

        val acRed = rms(applyBandpass(reds))
        val acLuma = rms(applyBandpass(lumas))

        val dR = reds.average()
        val dL = lumas.average()
        if (dR < 1e-10 || dL < 1e-10) return 0

        val ratioR = (acRed / dR) / ((acLuma / dL) + 1e-10)
        
        // Calibracion empirica ajustada
        val spo2 = (110.0 - 25.0 * ratioR).roundToInt()
        return spo2.coerceIn(80, 100)
    }

    private fun applyBandpass(data: DoubleArray): DoubleArray {
        val filter = CascadedFilter.butterworthBandpass(0.5, 4.0, FS)
        return DoubleArray(data.size) { filter.process(data[it]) }
    }

    // --- Frecuencia Respiratoria ---

    private fun calculateRespiratoryRate(): Int {
        if (ibiHistory.size < 10 || peakAmplitudes.size < 10) return 0
        val rates = mutableListOf<Double>()
        
        val ibiSignal = padToPow2(ibiHistory.toDoubleArray())
        val fsIbi = 1000.0 / (ibiHistory.average().coerceAtLeast(1.0))
        val freqRate = extractDominantFreq(ibiSignal, fsIbi, 0.1, 0.6)
        if (freqRate > 0) rates.add(freqRate * 60.0)

        val ampSignal = padToPow2(peakAmplitudes.toDoubleArray())
        val ampRate = extractDominantFreq(ampSignal, fsIbi, 0.1, 0.6)
        if (ampRate > 0) rates.add(ampRate * 60.0)

        return if (rates.isNotEmpty()) rates.average().roundToInt().coerceIn(10, 35) else 0
    }

    private fun extractDominantFreq(data: DoubleArray, fs: Double, fLow: Double, fHigh: Double): Double {
        if (data.size < 8) return 0.0
        val fft = FastFourierTransformer(DftNormalization.STANDARD)
        val complex = fft.transform(applyHamming(data), TransformType.FORWARD)
        var maxMag = -1.0; var peakFreq = 0.0
        for (i in 1 until complex.size / 2) {
            val freq = i * fs / complex.size
            if (freq in fLow..fHigh) {
                val mag = sqrt(complex[i].real.pow(2) + complex[i].imaginary.pow(2))
                if (mag > maxMag) { maxMag = mag; peakFreq = freq }
            }
        }
        return peakFreq
    }

    // --- Presión Arterial (Consistente basada en SDPPG) ---

    private fun calculateBloodPressure(signal: DoubleArray, currentBpm: Int): Pair<Int, Int> {
        if (currentBpm < 40) return Pair(0,0)

        val d1 = derivative(signal)
        val d2 = derivative(d1)
        val smoothed = movingAverage(d2, 5)

        val aWave = smoothed.maxOrNull() ?: 1.0
        var bWave = 0.0
        val aIdx = smoothed.indices.maxByOrNull { smoothed[it] } ?: 0

        for (i in aIdx + 1 until smoothed.size - 1) {
            if (smoothed[i] < smoothed[i - 1] && smoothed[i] < smoothed[i + 1]) {
                bWave = smoothed[i]; break
            }
        }

        val baIndex = if (abs(aWave) > 1e-10) abs(bWave / aWave).coerceIn(0.1, 2.0) else 0.5
        
        // Base normal 115/75, ajustada por rigidez (b/a) y BPM
        val rawSys = 100.0 + (baIndex * 35.0) + ((currentBpm - 70) * 0.4)
        val rawDia = 65.0 + (baIndex * 20.0) + ((currentBpm - 70) * 0.25)
        
        bpSysHistory.add(rawSys)
        bpDiaHistory.add(rawDia)
        if (bpSysHistory.size > 10) bpSysHistory.removeAt(0)
        if (bpDiaHistory.size > 10) bpDiaHistory.removeAt(0)

        val sys = bpSysHistory.average().toInt().coerceIn(90, 180)
        val dia = bpDiaHistory.average().toInt().coerceIn(60, 110)

        return Pair(sys, dia)
    }

    // --- HRV Completo ---

    data class HRVResults(val sdnn: Double, val rmssd: Double, val lfhf: Double)

    private fun calculateHRV(): HRVResults {
        if (nnIntervals.size < 10) return HRVResults(0.0, 0.0, 0.0)

        val stats = DescriptiveStatistics()
        nnIntervals.forEach { stats.addValue(it) }
        val sdnn = stats.standardDeviation

        var sumDiffSq = 0.0
        for (i in 1 until nnIntervals.size) {
            val diff = nnIntervals[i] - nnIntervals[i - 1]
            sumDiffSq += diff * diff
        }
        val rmssd = sqrt(sumDiffSq / (nnIntervals.size - 1))

        val lfhf = calculateLFHF()

        return HRVResults(sdnn, rmssd, lfhf)
    }

    private fun calculateLFHF(): Double {
        if (nnIntervals.size < 16) return 0.0

        val nnArray = padToPow2(nnIntervals.toDoubleArray())
        val meanNN = nnIntervals.average()
        val fsNN = 1000.0 / meanNN

        val fft = FastFourierTransformer(DftNormalization.STANDARD)
        val complex = fft.transform(applyHamming(nnArray), TransformType.FORWARD)

        var lfPower = 0.0; var hfPower = 0.0
        for (i in 1 until complex.size / 2) {
            val freq = i * fsNN / complex.size
            val power = complex[i].real.pow(2) + complex[i].imaginary.pow(2)
            if (freq in 0.04..0.15) lfPower += power
            if (freq in 0.15..0.4) hfPower += power
        }

        return if (hfPower > 1e-10) lfPower / hfPower else 0.0
    }

    // --- SQI y Arritmias ---

    private fun detectArrhythmia(hrv: HRVResults, sqi: Float): String {
        if (sqi < 0.3f) return "ANALIZANDO SEÑAL..."
        if (nnIntervals.size < 20) return "ADQUIRIENDO..."

        val meanNN = nnIntervals.average()
        val cv = if (meanNN > 0) hrv.sdnn / meanNN * 100.0 else 0.0

        var nn50 = 0
        for (i in 1 until nnIntervals.size) {
            if (abs(nnIntervals[i] - nnIntervals[i - 1]) > 50.0) nn50++
        }
        val pnn50 = nn50.toDouble() / (nnIntervals.size - 1) * 100.0

        return when {
            cv > 20.0 && pnn50 > 30.0 -> "ALTA IRREGULARIDAD"
            hrv.rmssd > 120.0 -> "POSIBLE ARRITMIA"
            else -> "RITMO SINUSAL NORMAL"
        }
    }

    private fun calculateSQI(signal: DoubleArray): Float {
        if (signal.isEmpty()) return 0f

        val snr = calculateSpectralSNR(signal)
        val snrScore = (snr / 15.0).coerceIn(0.0, 1.0)

        val pi = calculatePerfusionIndex(redBuf.toArray())
        val piScore = (pi * 15.0).coerceIn(0.0, 1.0)

        val regScore = if (nnIntervals.size > 5) {
            val cv = stdDev(nnIntervals.toDoubleArray()) / (nnIntervals.average() + 1e-10)
            (1.0 - cv).coerceIn(0.0, 1.0)
        } else 0.5

        return (snrScore * 0.5 + piScore * 0.2 + regScore * 0.3).toFloat()
    }

    private fun calculateSpectralSNR(signal: DoubleArray): Double {
        val padded = padToPow2(signal)
        val fft = FastFourierTransformer(DftNormalization.STANDARD)
        val complex = fft.transform(applyHamming(padded), TransformType.FORWARD)

        var peakPower = 0.0; var totalPower = 0.0
        for (i in 1 until complex.size / 2) {
            val freq = i * FS / complex.size
            val power = complex[i].real.pow(2) + complex[i].imaginary.pow(2)
            totalPower += power
            if (freq in 0.7..3.5 && power > peakPower) peakPower = power
        }
        return if (totalPower > 1e-10) 10 * log10(peakPower / (totalPower - peakPower + 1e-10)) else 0.0
    }

    private fun calculatePerfusionIndex(reds: DoubleArray): Double {
        if (reds.isEmpty()) return 0.0
        val ac = rms(applyBandpass(reds))
        val dc = reds.average()
        return if (dc > 1e-10) (ac / dc) * 100.0 else 0.0
    }

    // --- Utilidades ---

    private fun rms(data: DoubleArray): Double {
        var sum = 0.0; for (v in data) sum += v * v
        return sqrt(sum / data.size.coerceAtLeast(1))
    }

    private fun stdDev(data: DoubleArray): Double {
        val mean = data.average(); var sum = 0.0
        for (v in data) sum += (v - mean).pow(2.0)
        return sqrt(sum / data.size.coerceAtLeast(1))
    }

    private fun derivative(data: DoubleArray): DoubleArray {
        return DoubleArray(data.size) { i ->
            if (i > 0) (data[i] - data[i - 1]) * FS else 0.0
        }
    }

    private fun movingAverage(data: DoubleArray, window: Int): DoubleArray {
        return DoubleArray(data.size) { i ->
            val start = (i - window / 2).coerceAtLeast(0)
            val end = (i + window / 2).coerceAtMost(data.size - 1)
            var sum = 0.0; for (j in start..end) sum += data[j]
            sum / (end - start + 1)
        }
    }

    private fun applyHamming(input: DoubleArray): DoubleArray {
        return DoubleArray(input.size) { i ->
            input[i] * (0.54 - 0.46 * cos(2 * PI * i / (input.size - 1).coerceAtLeast(1)))
        }
    }

    private fun padToPow2(data: DoubleArray): DoubleArray {
        var n = 1; while (n < data.size) n *= 2
        val padded = DoubleArray(n)
        System.arraycopy(data, 0, padded, 0, data.size)
        return padded
    }

    private fun ByteBuffer.toByteArray(): ByteArray {
        val bytes = ByteArray(remaining()); get(bytes); return bytes
    }
}
