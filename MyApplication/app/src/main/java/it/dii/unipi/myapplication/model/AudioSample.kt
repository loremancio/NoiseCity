package it.dii.unipi.myapplication.model

/**
 * Data class representing an audio sample.
 */
data class AudioSample(
    val samples: FloatArray
) {
    // cached average
    private var avg: Double = 0.0

    fun getAvg(): Double {
        if (avg == 0.0) {
            avg = samples.map { it.toDouble() }.average()
        }
        return avg
    }
    // override equals to do content-based comparison on the FloatArray
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioSample) return false
        return samples.contentEquals(other.samples)
    }

    // override hashCode to match equals
    override fun hashCode(): Int {
        return samples.contentHashCode()
    }
}
