package com.example.myapplication.analyzer

/**
 * Buffer circular de alto rendimiento para señales biomédicas.
 * Optimizado para acceso secuencial y operaciones estadísticas sin allocación.
 */
class CircularBuffer(private val capacity: Int) {
    private val buffer = DoubleArray(capacity)
    private var writeIndex = 0
    private var count = 0

    fun add(value: Double) {
        buffer[writeIndex] = value
        writeIndex = (writeIndex + 1) % capacity
        if (count < capacity) count++
    }

    fun isFull(): Boolean = count == capacity

    fun getCount(): Int = count

    fun clear() {
        writeIndex = 0
        count = 0
    }

    /**
     * Retorna el array ordenado cronológicamente (más antiguo primero).
     */
    fun toArray(): DoubleArray {
        val result = DoubleArray(count)
        for (i in 0 until count) {
            result[i] = buffer[(writeIndex - count + i + capacity) % capacity]
        }
        return result
    }

    /**
     * Retorna las últimas N muestras, ordenadas cronológicamente.
     */
    fun getLatest(n: Int): DoubleArray {
        val take = n.coerceAtMost(count)
        val result = DoubleArray(take)
        for (i in 0 until take) {
            result[i] = buffer[(writeIndex - take + i + capacity) % capacity]
        }
        return result
    }

    /**
     * Media aritmética sin crear array temporal.
     */
    fun average(): Double {
        if (count == 0) return 0.0
        var sum = 0.0
        for (i in 0 until count) {
            sum += buffer[(writeIndex - count + i + capacity) % capacity]
        }
        return sum / count
    }

    /**
     * Acceso al último valor insertado.
     */
    fun last(): Double {
        if (count == 0) return 0.0
        return buffer[(writeIndex - 1 + capacity) % capacity]
    }
}
