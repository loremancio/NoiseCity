package it.dii.unipi.myapplication.ui.screens.sound

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import it.dii.unipi.myapplication.R
import it.dii.unipi.myapplication.controller.SoundController
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

    private lateinit var waveformView: WaveformView
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
        
        try {
            waveformView = view.findViewById(R.id.waveformView)
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
        } catch (e: Exception) {
            Log.e(TAG, "Error updating waveform", e)
        }
    }
}