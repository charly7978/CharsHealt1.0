package com.example.myapplication.analyzer

import kotlin.math.*

/**
 * Filtro Biquad IIR (Second-Order Section) — Direct Form II Transposed.
 * Base fundamental para filtros Butterworth de alta estabilidad numérica.
 */
class BiquadFilter(
    private val b0: Double, private val b1: Double, private val b2: Double,
    private val a1: Double, private val a2: Double
) {
    private var z1 = 0.0
    private var z2 = 0.0

    fun process(input: Double): Double {
        val output = b0 * input + z1
        z1 = b1 * input - a1 * output + z2
        z2 = b2 * input - a2 * output
        return output
    }

    fun reset() { z1 = 0.0; z2 = 0.0 }

    companion object {
        /**
         * Diseña un filtro Butterworth Highpass de 2do orden.
         * Usa Bilinear Transform con pre-warping.
         */
        fun butterworthHighpass(fc: Double, fs: Double): BiquadFilter {
            val c = tan(PI * fc / fs)
            val c2 = c * c
            val sqrt2c = sqrt(2.0) * c
            val d = 1.0 + sqrt2c + c2
            return BiquadFilter(
                b0 = 1.0 / d,
                b1 = -2.0 / d,
                b2 = 1.0 / d,
                a1 = 2.0 * (c2 - 1.0) / d,
                a2 = (1.0 - sqrt2c + c2) / d
            )
        }

        /**
         * Diseña un filtro Butterworth Lowpass de 2do orden.
         */
        fun butterworthLowpass(fc: Double, fs: Double): BiquadFilter {
            val c = tan(PI * fc / fs)
            val c2 = c * c
            val sqrt2c = sqrt(2.0) * c
            val d = 1.0 + sqrt2c + c2
            return BiquadFilter(
                b0 = c2 / d,
                b1 = 2.0 * c2 / d,
                b2 = c2 / d,
                a1 = 2.0 * (c2 - 1.0) / d,
                a2 = (1.0 - sqrt2c + c2) / d
            )
        }
    }
}

/**
 * Cadena de filtros biquad en cascada.
 * Permite construir filtros bandpass de orden superior.
 */
class CascadedFilter(private val sections: List<BiquadFilter>) {
    fun process(input: Double): Double {
        var x = input
        for (section in sections) x = section.process(x)
        return x
    }

    fun reset() { sections.forEach { it.reset() } }

    companion object {
        /**
         * Filtro bandpass Butterworth: cascada de highpass + lowpass.
         * Orden total = 4 (2do orden cada sección).
         */
        fun butterworthBandpass(fLow: Double, fHigh: Double, fs: Double): CascadedFilter {
            return CascadedFilter(listOf(
                BiquadFilter.butterworthHighpass(fLow, fs),
                BiquadFilter.butterworthLowpass(fHigh, fs)
            ))
        }
    }
}
