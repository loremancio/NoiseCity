package it.dii.unipi.myapplication.ui.screens.sound

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import it.dii.unipi.myapplication.R
import it.dii.unipi.myapplication.controller.SoundController
import it.dii.unipi.myapplication.database.CompensationDatabaseHelper
import it.dii.unipi.myapplication.model.AudioSample
import it.dii.unipi.myapplication.ui.components.WaveformView

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
        // now context is non-null so we can initialize the db
        dbHelper = CompensationDatabaseHelper(context)
    }

    // We have two waveforms: one in the time domain and the other in the frequency domain
    private lateinit var waveformView: WaveformView
    private lateinit var frequencyWaveformView: WaveformView
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

        val compensationFactor = dbHelper.getCompensationValue()
        if (compensationFactor == null) {
            showCompensationDialog()
        }

        try {
            waveformView = view.findViewById(R.id.waveformView)
            frequencyWaveformView = view.findViewById(R.id.frequencyWaveformView)
            frequencyWaveformView.setMaxDisplayFrequency(10_000f) // Limit FFT graph to 10 kHz
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
            waveformView.setDataType(WaveformView.DataType.TIME_DOMAIN)

            val fftData = sample.calculateFFT()
            val frequencySample = AudioSample(fftData)
            frequencyWaveformView.setAudioSample(frequencySample)
            frequencyWaveformView.setDataType(WaveformView.DataType.FREQUENCY_DOMAIN)
            frequencyWaveformView.setFixedYLimits(0f, 300f)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating waveform", e)
        }
    }

    private fun showCompensationDialog() {
            // 1. Inflate
            val dialogView = layoutInflater.inflate(R.layout.dialog_compensation, null)
            val editText   = dialogView.findViewById<EditText>(R.id.etCompensation)
            val btnCancel  = dialogView.findViewById<MaterialButton>(R.id.btnCancel)
            val btnOk      = dialogView.findViewById<MaterialButton>(R.id.btnOkCustom)

            // 2. Build the AlertDialog
            val dialog = AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setCancelable(false)
                .create()
        
            // 3. Wiring of the listener
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
                    editText.error = "Value not valid"
                }
            }
        
            // 4. Show
            dialog.show()
        }
}