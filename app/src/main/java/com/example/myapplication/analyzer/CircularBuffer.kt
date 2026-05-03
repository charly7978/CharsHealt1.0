package com.example.myapplication.analyzer

class CircularBuffer(private val size: Int) {
    private val buffer = DoubleArray(size)
    private var index = 0
    private var count = 0

    fun add(value: Double) {
        buffer[index] = value
        index = (index + 1) % size
        if (count < size) count++
    }

    fun isFull(): Boolean = count == size

    fun toArray(): DoubleArray {
        val result = DoubleArray(count)
        for (i in 0 until count) {
            result[i] = buffer[(index - count + i + size) % size]
        }
        return result
    }
}
