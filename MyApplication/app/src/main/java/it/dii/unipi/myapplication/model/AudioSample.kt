package it.dii.unipi.myapplication.model

import android.content.Context
import it.dii.unipi.myapplication.database.CompensationDatabaseHelper
import org.jtransforms.fft.DoubleFFT_1D
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Represents an audio sample and provides methods for signal processing.
 * Holds the raw audio data and exposes methods for FFT, normalization, and dB calculation.
 */
data class AudioSample(
    val samples: FloatArray
) {

    /**
     * Computes the magnitude spectrum (FFT) of the audio samples.
     * @return FloatArray of size N/2 (magnitude spectrum)
     */
    fun calculateFFT(): FloatArray {
        var n = samples.size
        if (n == 0) return FloatArray(0)
        var effectiveN = n
        if (Integer.highestOneBit(effectiveN) != effectiveN && effectiveN > 1) {
            effectiveN = Integer.highestOneBit(effectiveN) shl 1
        }
        val dataToTransform: DoubleArray = if (effectiveN != n || n == 1) {
            if (n == 1 && effectiveN < 2) effectiveN = 2
            DoubleArray(effectiveN).apply {
                for (i in samples.indices) if (i < effectiveN) this[i] = samples[i].toDouble()
            }
        } else {
            DoubleArray(n) { i -> samples[i].toDouble() }
        }
        DoubleFFT_1D(effectiveN.toLong()).realForward(dataToTransform)
        val magnitudes = FloatArray(effectiveN / 2)
        magnitudes[0] = kotlin.math.abs(dataToTransform[0]).toFloat()
        for (i in 1 until effectiveN / 2) {
            val re = dataToTransform[2 * i]
            val im = dataToTransform[2 * i + 1]
            magnitudes[i] = sqrt(re * re + im * im).toFloat()
        }
        return magnitudes
    }

    /**
     * Normalizes the samples to the range [-1, 1].
     */
    fun normalize(): FloatArray {
        val max = samples.maxOrNull()?.takeIf { it > 0 } ?: 1f
        return samples.map { it / max }.toFloatArray()
    }



    /**
     * Computes the average decibel value (dBFS) for the sample, including compensation from DB.
     * @param context Context for accessing the compensation database
     * @return Average dB value (float) with compensation applied
     */
    fun averageDbWithCompensation(context: Context): Float {
        // 1) guard against empty sample array
        if (samples.isEmpty()) return Float.NEGATIVE_INFINITY
    
        // 2) compute mean square of samples
        val meanSquare = samples.fold(0.0) { acc, v -> acc + (v * v).toDouble() } / samples.size
    
        // 3) rms amplitude
        val rms = sqrt(meanSquare)
    
        // 4) avoid log of zero by enforcing a small floor
        val minRms = 1e-8
        val safeRms = max(rms, minRms)
    
        // 5) convert to decibels (dBFS), assuming full-scale reference = 1.0
        val dbfs = (20.0 * log10(safeRms / 1.0)).toFloat()
    
        // 6) retrieve user calibration offset (in dB) and apply
        val compensation = CompensationDatabaseHelper(context)
            .getCompensationValue()
            ?.toFloat()
            ?: 0f
    
        return dbfs + compensation
    }

    // Content-based equality for FloatArray
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioSample) return false
        return samples.contentEquals(other.samples)
    }

    override fun hashCode(): Int {
        return samples.contentHashCode()
    }
}
