package it.dii.unipi.myapplication.controller

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import it.dii.unipi.myapplication.model.AudioRecorder
import it.dii.unipi.myapplication.model.AudioSample
import it.dii.unipi.myapplication.utils.PermissionUtils

/**
 * Controller class that manages the audio recording functionality
 * and coordinates between the model and view
 */
class SoundController(private val context: Context) {

    companion object {
        private const val TAG = "SoundController"
    }

    private val audioRecorder = AudioRecorder()
    private var audioSampleListener: ((AudioSample) -> Unit)? = null
    
    init {
        audioRecorder.setOnSampleAvailableListener { sample ->
            audioSampleListener?.invoke(sample)
        }
    }
    
    fun setOnAudioSampleListener(listener: (AudioSample) -> Unit) {
        audioSampleListener = listener
    }
    
    fun startRecording() {
        Log.d(TAG, "Starting recording")
        
        // Check permission first
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) 
            == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            if (!audioRecorder.startRecording()) {
                Toast.makeText(context, "Failed to start recording", Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.d(TAG, "Recording permission not granted")
            Toast.makeText(context, "Microphone permission required or position permission required", Toast.LENGTH_SHORT).show()
            // The fragment/activity should handle requesting permissions
        }
    }
    
    fun stopRecording() {
        Log.d(TAG, "Stopping recording")
        audioRecorder.stopRecording(context)
    }
    
    fun isRecording(): Boolean {
        return audioRecorder.isRecording()
    }
}