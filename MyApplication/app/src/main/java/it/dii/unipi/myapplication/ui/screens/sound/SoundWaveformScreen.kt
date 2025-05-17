package it.dii.unipi.myapplication.ui.screens.sound

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import it.dii.unipi.myapplication.R
import it.dii.unipi.myapplication.controller.SoundController
import it.dii.unipi.myapplication.database.CompensationDatabaseHelper
import it.dii.unipi.myapplication.model.AudioSample
import it.dii.unipi.myapplication.ui.components.WaveformView
import org.jtransforms.fft.DoubleFFT_1D // Added import
import kotlin.math.sqrt // Added import

/**
 * Fragment that represents the sound waveform visualization screen
 */
class SoundWaveformScreen : Fragment() {

    companion object {
        private const val TAG = "SoundWaveformScreen"
        
        fun newInstance() = SoundWaveformScreen()
    }
    private lateinit var dbHelper: CompensationDatabaseHelper

    override fun onAttach(context: Context) {
        super.onAttach(context)
        // now context is non-null
        dbHelper = CompensationDatabaseHelper(context)
    }

    private lateinit var waveformView: WaveformView
    private lateinit var frequencyWaveformView: WaveformView // Added for frequency domain
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    
    private lateinit var soundController: SoundController
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d(TAG, "onCreateView: Initializing view")
        return inflater.inflate(R.layout.fragment_sound_waveform, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated: Setting up UI components")
        // delete only debug
        val compensationFactor = dbHelper.getCompensationValue()
        if (compensationFactor == null) {
            showCompensationDialog()
        }

        try {
            waveformView = view.findViewById(R.id.waveformView)
            frequencyWaveformView = view.findViewById(R.id.frequencyWaveformView) // Initialize new view
            btnStart = view.findViewById(R.id.btnStart)
            btnStop = view.findViewById(R.id.btnStop)
            
            soundController = SoundController(requireContext())
            
            // Set up the audio sample listener
            soundController.setOnAudioSampleListener { sample ->
                updateWaveform(sample)
            }
            
            btnStart.setOnClickListener {
                Log.d(TAG, "btnStart clicked")
                soundController.startRecording()
            }

            btnStop.setOnClickListener {
                Log.d(TAG, "btnStop clicked")
                soundController.stopRecording()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing UI components", e)
        }
    }
    
    override fun onPause() {
        super.onPause()
        soundController.stopRecording()
    }
    
    private fun updateWaveform(sample: AudioSample) {
        try {
            waveformView.setAudioSample(sample)
            waveformView.setDataType(WaveformView.DataType.TIME_DOMAIN) // Explicitly set for clarity

            // Calculate FFT and update frequencyWaveformView
            val fftData = calculateFFT(sample.samples)
            val frequencySample = AudioSample(fftData)
            frequencyWaveformView.setAudioSample(frequencySample)
            frequencyWaveformView.setDataType(WaveformView.DataType.FREQUENCY_DOMAIN) // Set data type for frequency view

        } catch (e: Exception) {
            Log.e(TAG, "Error updating waveform", e)
        }
    }

    /**
     * Calcola la FFT dei campioni audio (solo magnitudine della prima metà dello spettro).
     * @param audioData FloatArray di dimensione N.
     * @return FloatArray di lunghezz\\\
     * N/2 con la magnitudine dello spettro.
     */
    private fun calculateFFT(audioData: FloatArray): FloatArray {
        var n = audioData.size
        if (n == 0) return FloatArray(0)

        var effectiveN = n
        if (Integer.highestOneBit(effectiveN) != effectiveN && effectiveN > 1) { // if not a power of 2 and n > 1
            effectiveN = Integer.highestOneBit(effectiveN) shl 1 // next power of 2
        }
        
        val dataToTransform: DoubleArray
        if (effectiveN != n || n == 1) { // Pad if n was not power of two, or if n was 1 (needs at least 2 for FFT)
             if (n == 1 && effectiveN < 2) effectiveN = 2 // Ensure at least size 2 for n=1 case
            dataToTransform = DoubleArray(effectiveN)
            for (i in audioData.indices) {
                if (i < effectiveN) {
                    dataToTransform[i] = audioData[i].toDouble()
                }
            }
            // The rest of dataToTransform is already 0.0 (default for DoubleArray)
            n = effectiveN // Update n to the new padded size
        } else {
            dataToTransform = DoubleArray(n) { i -> audioData[i].toDouble() }
        }

        DoubleFFT_1D(n.toLong()).realForward(dataToTransform)
        val magnitudes = FloatArray(n / 2)

        magnitudes[0] = kotlin.math.abs(dataToTransform[0]).toFloat() // DC component (R[0])

        for (i in 1 until n / 2) {
            val re = dataToTransform[2 * i]
            val im = dataToTransform[2 * i + 1]
            magnitudes[i] = sqrt(re * re + im * im).toFloat()
        }
        
        return magnitudes
    }

    private fun showCompensationDialog() {
            // 1. Inflate
            val dialogView = layoutInflater.inflate(R.layout.dialog_compensation, null)
            val editText = dialogView.findViewById<EditText>(R.id.etCompensation)
            val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnCancel)
            val btnOk    = dialogView.findViewById<MaterialButton>(R.id.btnOkCustom)

            // 2. Build the AlertDialog senza setPositive/Negative predefiniti
            val dialog = AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setCancelable(false)
                .create()
        
            // 3. Wiring dei listener
            btnCancel.setOnClickListener {
                Log.d(TAG, "Compensation dialog cancelled")
                dialog.dismiss()
            }
        
            btnOk.setOnClickListener {
                val input = editText.text.toString()
                try {
                    val value = input.toFloat()
                    dbHelper.saveCompensationValue(value)
                    Log.d(TAG, "Compensation value set to: $value")
                    dialog.dismiss()
                } catch (e: NumberFormatException) {
                    editText.error = "Valore non valido"
                }
            }
        
            // 4. Mostra
            dialog.show()
        }
        
  
}