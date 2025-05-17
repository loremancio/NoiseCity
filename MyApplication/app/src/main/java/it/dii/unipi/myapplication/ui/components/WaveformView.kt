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
    private val frequencyMarkers = listOf(0, 1000, 2000, 3000, 4000, 5000, 10000, 20000) // Hz

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

        val samples = audioSample?.samples ?: return
        if (samples.isEmpty()) return

        val centerY = topMargin + drawingHeight / 2f
        
        // Determine how many samples map to one horizontal pixel
        val step = samples.size.toFloat() / drawingWidth
        
        // Draw the waveform
        var x = leftMargin
        for (i in 0 until drawingWidth.toInt()) {
            val sampleIndex = (i * step).toInt().coerceIn(samples.indices)
            val amplitude = samples[sampleIndex]
            // samples are already normalized [-1f, 1f]
            val yOffset = amplitude * (drawingHeight / 2f)
            canvas.drawLine(
                x,
                centerY - yOffset,
                x,
                centerY + yOffset,
                paint
            )
            x += 1f
        }
    }
    
    private fun drawGridAndAxes(canvas: Canvas, leftMargin: Float, topMargin: Float, width: Float, height: Float) {
        // Draw grid
        val samplesCount = audioSample?.samples?.size ?: 0
        val durationMs = if (samplesCount > 0 && currentDataType == DataType.TIME_DOMAIN) (samplesCount * 1000f) / sampleRate else 0f
        // For frequency domain, X-axis represents frequency up to Nyquist / 2 (sampleRate / 2)
        // The actual max frequency displayed will depend on the FFT output size.
        // Let's assume the FFT output also has 'samplesCount' points for now, mapping to sampleRate / 2 Hz.
        val maxFrequencyHz = if (samplesCount > 0 && currentDataType == DataType.FREQUENCY_DOMAIN) sampleRate / 2f else 0f

        // Draw horizontal grid lines (amplitude/magnitude) - draw these first as background
        val amplitudeMarkers = listOf(-1.0f, -0.75f, -0.5f, -0.25f, 0.0f, 0.25f, 0.5f, 0.75f, 1.0f)  // More gridlines
        for (marker in amplitudeMarkers) {
            val y = topMargin + height / 2 - (marker * height / 2)
            
            // Draw grid line
            canvas.drawLine(
                leftMargin, 
                y, 
                leftMargin + width, 
                y, 
                gridPaint
            )
        }
        
        if (samplesCount > 0) {
            // Draw vertical grid lines (time or frequency)
            val markers = if (currentDataType == DataType.TIME_DOMAIN) timeMarkers else frequencyMarkers
            val maxXValue = if (currentDataType == DataType.TIME_DOMAIN) durationMs else maxFrequencyHz

            if (maxXValue > 0) { // Ensure maxXValue is positive to avoid division by zero or incorrect rendering
                for (marker in markers) {
                    if (marker <= maxXValue) {
                        val x = leftMargin + (marker / maxXValue) * width
                        
                        // Draw grid line
                        val gridLinePaint = Paint(gridPaint)
                        // Optional: make some grid lines more visible
                        if (currentDataType == DataType.TIME_DOMAIN && marker % 500 == 0) {
                            gridLinePaint.strokeWidth = 1.8f
                            gridLinePaint.alpha = 220
                        } else if (currentDataType == DataType.FREQUENCY_DOMAIN && marker % 1000 == 0) { // Example for frequency
                            gridLinePaint.strokeWidth = 1.8f
                            gridLinePaint.alpha = 220
                        }
                        
                        canvas.drawLine(
                            x, 
                            topMargin, 
                            x, 
                            topMargin + height, 
                            gridLinePaint
                        )
                    }
                }
            }
        }
        
        // Draw X-axis (time or frequency)
        canvas.drawLine(
            leftMargin, 
            topMargin + height, 
            leftMargin + width, 
            topMargin + height, 
            axisPaint
        )
        
        // Draw Y-axis (amplitude)
        canvas.drawLine(
            leftMargin, 
            topMargin, 
            leftMargin, 
            topMargin + height, 
            axisPaint
        )
        
        // Draw time or frequency markers
        if (samplesCount > 0) {
            val markers = if (currentDataType == DataType.TIME_DOMAIN) timeMarkers else frequencyMarkers
            val maxXValue = if (currentDataType == DataType.TIME_DOMAIN) durationMs else maxFrequencyHz

            if (maxXValue > 0) { // Ensure maxXValue is positive
                for (markerValue in markers) {
                    if (markerValue <= maxXValue) {
                        val x = leftMargin + (markerValue / maxXValue) * width
                        // Draw tick mark
                        canvas.drawLine(x, topMargin + height, x, topMargin + height + tickLength, axisPaint)
                        
                        // Draw label
                        val labelText = if (currentDataType == DataType.TIME_DOMAIN) {
                            "${markerValue}ms"
                        } else {
                            // Format frequency labels (e.g., "1kHz", "2kHz")
                            if (markerValue >= 1000) "${markerValue / 1000}kHz" else "${markerValue}Hz"
                        }
                        val textWidth = textPaint.measureText(labelText)
                        val textX = x - textWidth / 2
                        val textY = topMargin + height + tickLength + labelMargin + textPaint.textSize / 2
                        
                        // Draw text
                        canvas.drawText(labelText, textX, textY, textPaint)
                    }
                }
            }
        }
        
        // Draw amplitude markers
        for (marker in amplitudeMarkers.filter { it % 0.5f == 0f }) {  // Only draw labels for main markers
            val y = topMargin + height / 2 - (marker * height / 2)
            // Draw tick mark
            canvas.drawLine(leftMargin - tickLength, y, leftMargin, y, axisPaint)
            
            // Draw amplitude label
            if (marker == 0f) {
                val labelText = "0"  // Simplified label for zero
                val textWidth = textPaint.measureText(labelText)
                val textX = leftMargin - textWidth - 10f
                val textY = y + textPaint.textSize / 3
                
                // Draw text
                canvas.drawText(labelText, textX, textY, textPaint)
            } else {
                val labelText = String.format("%.1f", marker)
                val textWidth = textPaint.measureText(labelText)
                val textX = leftMargin - textWidth - 10f
                val textY = y + textPaint.textSize / 3
                
                // Draw text
                canvas.drawText(labelText, textX, textY, textPaint)
            }
        }
        
        // Draw X-axis title
        val xLabelText = if (currentDataType == DataType.TIME_DOMAIN) "Tempo (ms)" else "Frequenza (Hz)"
        val xLabelWidth = titlePaint.measureText(xLabelText)
        val xLabelX = leftMargin + width / 2 - xLabelWidth / 2
        val xLabelY = topMargin + height + tickLength + labelMargin + textPaint.textSize * 1.8f
        
        // Draw X-axis label
        canvas.drawText(xLabelText, xLabelX, xLabelY, titlePaint)
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