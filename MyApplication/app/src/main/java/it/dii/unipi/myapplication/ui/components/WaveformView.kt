package it.dii.unipi.myapplication.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import it.dii.unipi.myapplication.model.AudioSample
import kotlin.math.roundToInt

/**
 * Custom view to display the waveform of an audio sample.
 */
class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private var audioSample: AudioSample? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 2f
        color = Color.WHITE  // default line color
    }

    private val backgroundPaint = Paint().apply {
        color = Color.BLACK  // default background color
    }
    
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 2f  // Increased line thickness
        color = Color.LTGRAY
    }
    
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 1.2f  // Increased line thickness
        color = Color.DKGRAY
        alpha = 180  // Increased visibility
    }
    
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 40f  // Increased text size
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.CYAN  // Different color for axis titles
        textSize = 44f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    
    // Constants for axes
    private val axisMargin = 120f  // Increased margin
    private val tickLength = 10f
    private val labelMargin = 15f
    private val sampleRate = 44_100 // Hz
    
    // Time axis markers (milliseconds)
    private val timeMarkers = listOf(0, 250, 500, 750, 1000, 1250, 1500, 1750, 2000) // ms

    /**
     * Sets the audio sample to be displayed in the waveform view.
     */
    fun setAudioSample(sample: AudioSample) {
        this.audioSample = sample
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        
        // Define drawing area
        val leftMargin = axisMargin
        val topMargin = 90f  // Increased top margin for Y-axis title
        val rightMargin = 50f
        val bottomMargin = axisMargin * 1.5f
        
        val drawingWidth = width - leftMargin - rightMargin
        val drawingHeight = height - topMargin - bottomMargin
        
        // Y-axis title position (drawn before grid and axes)
        val yAxisTitleX = 30f  // Position far left
        val yAxisTitleY = topMargin + drawingHeight / 2
        
        // Draw the Y-axis title first
        canvas.save()
        canvas.rotate(-90f, yAxisTitleX, yAxisTitleY)
        val yLabelText = "Ampiezza"
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
        val durationMs = if (samplesCount > 0) (samplesCount * 1000f) / sampleRate else 0f
        
        // Draw horizontal grid lines (amplitude) - draw these first as background
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
            // Draw vertical grid lines (time)
            for (marker in timeMarkers) {
                if (marker <= durationMs) {
                    val x = leftMargin + (marker / durationMs) * width
                    
                    // Draw grid line
                    val gridLinePaint = Paint(gridPaint)
                    if (marker % 500 == 0) {  // Make lines at 0, 500, 1000, etc. more visible
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
        
        // Draw X-axis (time)
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
        
        // Draw time markers
        if (samplesCount > 0) {
            for (marker in timeMarkers) {
                if (marker <= durationMs) {
                    val x = leftMargin + (marker / durationMs) * width
                    // Draw tick mark
                    canvas.drawLine(x, topMargin + height, x, topMargin + height + tickLength, axisPaint)
                    
                    // Draw label with background for better visibility
                    val labelText = "${marker}ms"
                    val textWidth = textPaint.measureText(labelText)
                    val textX = x - textWidth / 2
                    val textY = topMargin + height + tickLength + labelMargin + textPaint.textSize / 2
                    
                    // Draw text background
                    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#88000000") // More visible semi-transparent black
                    }
                    val padding = 8f
                    canvas.drawRect(
                        textX - padding,
                        textY - textPaint.textSize,
                        textX + textWidth + padding,
                        textY + padding,
                        bgPaint
                    )
                    
                    // Draw text
                    canvas.drawText(labelText, textX, textY, textPaint)
                }
            }
        }
        
        // Draw amplitude markers
        for (marker in amplitudeMarkers.filter { it % 0.5f == 0f }) {  // Only draw labels for main markers
            val y = topMargin + height / 2 - (marker * height / 2)
            // Draw tick mark
            canvas.drawLine(leftMargin - tickLength, y, leftMargin, y, axisPaint)
            
            // Draw amplitude label with background for better visibility
            if (marker == 0f) {
                val labelText = "0"  // Simplified label for zero
                val textWidth = textPaint.measureText(labelText)
                val textX = leftMargin - textWidth - 10f
                val textY = y + textPaint.textSize / 3
                
                // Draw text background
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#88000000") // More visible semi-transparent black
                }
                val padding = 8f
                canvas.drawRect(
                    textX - padding,
                    textY - textPaint.textSize + padding,
                    textX + textWidth + padding,
                    textY + padding,
                    bgPaint
                )
                
                // Draw text
                canvas.drawText(labelText, textX, textY, textPaint)
            } else {
                val labelText = String.format("%.1f", marker)
                val textWidth = textPaint.measureText(labelText)
                val textX = leftMargin - textWidth - 10f
                val textY = y + textPaint.textSize / 3
                
                // Draw text background
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#88000000") // More visible semi-transparent black
                }
                val padding = 8f
                canvas.drawRect(
                    textX - padding,
                    textY - textPaint.textSize + padding,
                    textX + textWidth + padding,
                    textY + padding,
                    bgPaint
                )
                
                // Draw text
                canvas.drawText(labelText, textX, textY, textPaint)
            }
        }
        
        // Draw X-axis title
        val xLabelText = "Tempo (ms)"
        val xLabelWidth = titlePaint.measureText(xLabelText)
        val xLabelX = leftMargin + width / 2 - xLabelWidth / 2
        val xLabelY = topMargin + height + tickLength + labelMargin + textPaint.textSize * 1.8f
        
        // Draw text background
        val xLabelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#88000000") // More visible semi-transparent black
        }
        val xPadding = 12f
        canvas.drawRect(
            xLabelX - xPadding,
            xLabelY - titlePaint.textSize,
            xLabelX + xLabelWidth + xPadding,
            xLabelY + xPadding,
            xLabelBgPaint
        )
        
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