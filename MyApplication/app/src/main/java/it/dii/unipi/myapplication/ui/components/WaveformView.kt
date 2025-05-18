package it.dii.unipi.myapplication.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import it.dii.unipi.myapplication.R
import it.dii.unipi.myapplication.model.AudioSample
import android.graphics.Color

class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    enum class DataType {
        TIME_DOMAIN,
        FREQUENCY_DOMAIN
    }

    private var currentDataType: DataType = DataType.TIME_DOMAIN
    private var audioSample: AudioSample? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 2f
        color = ContextCompat.getColor(context, R.color.primary_blue)
    }

    private val backgroundPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.white)
    }

    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 2f
        color = ContextCompat.getColor(context, R.color.primary_blue)
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 1.2f
        color = ContextCompat.getColor(context, R.color.primary_blue)
        alpha = 180
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.primary_blue)
        textSize = 25f
        typeface = Typeface.create("inter", Typeface.NORMAL)
    }

    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.primary_blue)
        textSize = 25f
        typeface = Typeface.create("inter", Typeface.BOLD)
    }

    // Constants for axes
    private val axisMargin = 120f
    private val tickLength = 10f
    private val labelMargin = 15f
    private val sampleRate = 44_100 // Hz
    
    // Time axis markers (milliseconds)
    private val timeMarkers = listOf(0, 250, 500, 750, 1000, 1250, 1500, 1750, 2000) // ms
    // Frequency axis markers (Hertz) - Placeholder, adjust as needed
    private val frequencyMarkers = listOf(1000, 2000, 3000, 4000, 5000, 10000) // Hz

    // Maximum magnitude for Y-axis scaling in frequency domain
    private var maxMagnitude: Float? = null

    // Fixed Y-axis limits
    private var fixedMin: Float? = null
    private var fixedMax: Float? = null

    // 3) Property to hold the user‐chosen max display freq (Hz)
    private var maxDisplayFrequency: Float? = null

    /**
     * Sets the audio sample to be displayed in the waveform view.
     */
    fun setAudioSample(sample: AudioSample) {
        this.audioSample = sample
        invalidate()
    }

    /**
     * Sets the data type for the waveform view (Time or Frequency domain).
     * This will affect how the X-axis is drawn.
     */
    fun setDataType(dataType: DataType) {
        this.currentDataType = dataType
        invalidate() // Redraw with new axis labels
    }

    /**
     * Imposta il valore massimo della magnitudo per la scala Y in dominio frequenza.
     */
    fun setMaxMagnitude(max: Float?) {
        this.maxMagnitude = max
        invalidate()
    }

    /**
     * Imposta i limiti fissi per la scala Y.
     */
    fun setFixedYLimits(min: Float, max: Float) {
        fixedMin = min
        fixedMax = max
        invalidate()
    }
    fun clearFixedYLimits() {
        fixedMin = null
        fixedMax = null
        invalidate()
    }

    /** Imposta la frequenza massima visualizzata (Hz) per il dominio frequenza */
    fun setMaxDisplayFrequency(freqHz: Float) {
        maxDisplayFrequency = freqHz
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw background (transparent, so no need to draw a rectangle)
        canvas.drawColor(Color.TRANSPARENT)
        
        // Define drawing area
        val leftMargin = axisMargin
        val topMargin = 90f
        val rightMargin = 50f
        val bottomMargin = axisMargin * 1.5f
        
        val drawingWidth = width - leftMargin - rightMargin
        val drawingHeight = height - topMargin - bottomMargin
        
        // Y-axis title position (drawn before grid and axes)
        val yAxisTitleX = 30f
        val yAxisTitleY = topMargin + drawingHeight / 2
        
        // Draw the Y-axis title first
        canvas.save()
        canvas.rotate(-90f, yAxisTitleX, yAxisTitleY)
        val yLabelText = if (currentDataType == DataType.FREQUENCY_DOMAIN) "Magnitudo" else "Ampiezza"
        val yTitleWidth = titlePaint.measureText(yLabelText)
        canvas.drawText(yLabelText, yAxisTitleX - yTitleWidth / 2, yAxisTitleY, titlePaint)
        canvas.restore()
        
        // Draw the grid and axes
        drawGridAndAxes(canvas, leftMargin, topMargin, drawingWidth, drawingHeight)

        drawWaveform(canvas, leftMargin, topMargin, drawingWidth, drawingHeight)
    }
    
    private fun drawGridAndAxes(
        canvas: Canvas,
        left: Float,
        top: Float,
        width: Float,
        height: Float
    ) {
        // 1) Compute the Nyquist frequency (half the sample rate)
        val nyquist = sampleRate / 2f

        // 2) Determine the maximum frequency we actually want to display
        val maxFreq = if (currentDataType == DataType.FREQUENCY_DOMAIN)
            maxDisplayFrequency?.coerceAtMost(nyquist) ?: nyquist
        else
            nyquist

        // 3) Draw horizontal grid lines (amplitude/magnitude)
        val useFixedY = fixedMin != null && fixedMax != null
        val yMin = if (useFixedY) fixedMin!! else -1f
        val yMax = if (useFixedY) fixedMax!! else 1f
        val amplitudeMarkers = if (useFixedY) {
            val step = (yMax - yMin) / 4f
            listOf(yMin, yMin + step, yMin + 2 * step, yMin + 3 * step, yMax)
        } else if (currentDataType == DataType.FREQUENCY_DOMAIN && maxMagnitude != null) {
            val max = maxMagnitude!!
            val step = max / 4f
            listOf(0f, step, step * 2, step * 3, max)
        } else {
            listOf(-1.0f, -0.5f, 0.0f, 0.5f, 1.0f)
        }
        amplitudeMarkers.forEach { marker ->
            val y = if (useFixedY) {
                top + height - ((marker - yMin) / (yMax - yMin)) * height
            } else if (currentDataType == DataType.FREQUENCY_DOMAIN && maxMagnitude != null) {
                top + height - (marker / maxMagnitude!!) * height
            } else {
                top + height / 2 - (marker * height / 2)
            }
            canvas.drawLine(left, y, left + width, y, gridPaint)
        }

        if (currentDataType == DataType.FREQUENCY_DOMAIN) {
            // 4) Draw vertical frequency grid lines & labels up to maxFreq
            frequencyMarkers
                .filter { it <= maxFreq }
                .forEach { markerHz ->
                    val x = left + (markerHz / maxFreq) * width
                    canvas.drawLine(x, top, x, top + height, gridPaint)
                    canvas.drawLine(x, top + height, x, top + height + tickLength, axisPaint)
                    val label = if (markerHz >= 1000) "${markerHz / 1000}kHz" else "${markerHz}Hz"
                    val textW = textPaint.measureText(label)
                    canvas.drawText(label, x - textW / 2, 
                        top + height + tickLength + labelMargin + textPaint.textSize / 2, textPaint)
                }
        } else {
            // 5) Time‐domain vertical grid exactly as before
            val durationMs = (audioSample?.samples?.size ?: 0) * 1000f / sampleRate
            timeMarkers.forEach { markerMs ->
                if (markerMs <= durationMs) {
                    val x = left + (markerMs / durationMs) * width
                    canvas.drawLine(x, top, x, top + height, gridPaint)
                    canvas.drawLine(x, top + height, x, top + height + tickLength, axisPaint)
                    val label = "${markerMs}ms"
                    val textW = textPaint.measureText(label)
                    canvas.drawText(label, x - textW / 2, 
                        top + height + tickLength + labelMargin + textPaint.textSize / 2, textPaint)
                }
            }
        }

        // 6) Always draw the two axes across the full width
        canvas.drawLine(left, top + height, left + width, top + height, axisPaint)  // X‐axis
        canvas.drawLine(left, top, left, top + height, axisPaint)                   // Y‐axis
    }

    private fun drawWaveform(
        canvas: Canvas,
        left: Float,
        top: Float,
        width: Float,
        height: Float
    ) {
        val samples = audioSample?.samples ?: return

        if (currentDataType == DataType.FREQUENCY_DOMAIN) {
            // 1) Compute Nyquist & display limit
            val nyquist = sampleRate / 2f
            val maxFreq = maxDisplayFrequency?.coerceAtMost(nyquist) ?: nyquist

            // 2) Each FFT index → this many Hz
            val freqStep = nyquist / (samples.size - 1)

            // 3) Build a path, plotting only up to maxFreq
            val path = android.graphics.Path()
            var started = false

            for (i in samples.indices) {
                val freq = i * freqStep
                if (freq > maxFreq) break

                // X = interpolate freq [0..maxFreq] → pixel [0..width]
                val x = left + (freq / maxFreq) * width

                // Y = normalized magnitude in [0..1] inverted (0 at bottom, 1 at top)
                val yMin = fixedMin ?: 0f
                val yMax = fixedMax ?: (maxMagnitude ?: samples.maxOrNull() ?: 1f)
                val yNorm = ((samples[i] - yMin) / (yMax - yMin)).coerceIn(0f,1f)
                val y = top + height * (1 - yNorm)

                if (!started) {
                    path.moveTo(x, y)
                    started = true
                } else {
                    path.lineTo(x, y)
                }
            }

            // 4) Draw the FFT magnitude curve as a line, not a filled shape
            val strokePaint = Paint(paint).apply {
                style = Paint.Style.STROKE
                strokeWidth = paint.strokeWidth
            }
            canvas.drawPath(path, strokePaint)

        } else {
            // Time‐domain drawing (full width, centered around 0)
            val yMin = fixedMin ?: samples.minOrNull() ?: -1f
            val yMax = fixedMax ?: samples.maxOrNull() ?: 1f
            val peak = maxOf(kotlin.math.abs(yMin), kotlin.math.abs(yMax)).takeIf { it>0 } ?: 1f
            val midY = top + height/2f
            val scale = (height/2f) / peak

            val step = samples.size.toFloat() / width
            var prevX = left
            var prevY = midY - samples[0]*scale

            for (i in 1 until width.toInt().coerceAtLeast(1)) {
                val v = samples[(i*step).toInt().coerceIn(samples.indices)]
                val currX = left + i
                val currY = midY - v*scale
                canvas.drawLine(prevX, prevY, currX, currY, paint)
                prevX = currX; prevY = currY
            }
        }
    }
    
    /**
     * Allows customization of the waveform color.
     */
    fun setWaveformColor(color: Int) {
        paint.color = color
        invalidate()
    }

    /**
     * Allows customization of the background color.
     * Overrides the method in the View class.
     */
    override fun setBackgroundColor(color: Int) {
        backgroundPaint.color = color
        invalidate()
    }
    
    /**
     * Set the text size for the axis labels.
     */
    fun setAxisTextSize(sizePx: Float) {
        textPaint.textSize = sizePx
        invalidate()
    }
    
    /**
     * Set the color for the axis lines and labels.
     */
    fun setAxisColor(color: Int) {
        axisPaint.color = color
        textPaint.color = color
        invalidate()
    }
    
    /**
     * Set the color and opacity for the grid lines.
     */
    fun setGridColor(color: Int, alpha: Int) {
        gridPaint.color = color
        gridPaint.alpha = alpha
        invalidate()
    }
}