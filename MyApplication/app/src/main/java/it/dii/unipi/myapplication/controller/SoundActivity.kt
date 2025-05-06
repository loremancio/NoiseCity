package it.dii.unipi.myapplication.controller

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import it.dii.unipi.myapplication.R
import it.dii.unipi.myapplication.ui.screens.sound.SoundWaveformScreen

/**
 * Activity that hosts the sound waveform screen
 */
class SoundActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SoundActivity"
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                Log.d(TAG, "Microphone permission granted")
                // Permission granted, reload fragment to start recording
                loadSoundWaveformScreen()
            } else {
                Log.d(TAG, "Microphone permission denied")
                Toast.makeText(this, "Microphone permission is required", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sound)
        
        Log.d(TAG, "onCreate: Activity created")

        // Check if we have microphone permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "Requesting microphone permission")
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            // Permission already granted, load the fragment
            loadSoundWaveformScreen()
        }
    }
    
    private fun loadSoundWaveformScreen() {
        Log.d(TAG, "Loading SoundWaveformScreen")
        
        // Only add the fragment if it's not already there
        if (supportFragmentManager.findFragmentByTag("sound_waveform") == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, SoundWaveformScreen.newInstance(), "sound_waveform")
                .commit()
        }
    }
}