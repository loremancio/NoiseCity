package it.dii.unipi.myapplication.controller

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import it.dii.unipi.myapplication.R
import it.dii.unipi.myapplication.ui.components.WaveformView
import it.dii.unipi.myapplication.model.AudioSample
import it.dii.unipi.myapplication.model.DataSender
import it.dii.unipi.myapplication.model.LocationHelper
import it.dii.unipi.myapplication.model.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request


class SoundSamplingActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SoundSamplingActivity"
    }

    private lateinit var waveformView: WaveformView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var audioSample: FloatArray? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startRecording()
            } else {
                Toast.makeText(this, "Microphone permission is required", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate: Sound Sampling Activity Initialization")
        try {
            super.onCreate(savedInstanceState)
            Log.d(TAG, "onCreate: super.onCreate done")
            
            setContentView(R.layout.activity_sound_sampling)
            Log.d(TAG, "onCreate: setContentView done")

            try {
                waveformView = findViewById(R.id.waveformView)
                Log.d(TAG, "onCreate: findViewById waveformView done")
            } catch (e: Exception) {
                Log.e(TAG, "Error during getting waveformView", e)
            }

            try {
                btnStart = findViewById(R.id.btnStart)
                Log.d(TAG, "onCreate: findViewById btnStart done")
                btnStop = findViewById(R.id.btnStop)
                Log.d(TAG, "onCreate: findViewById btnStop done")
            } catch (e: Exception) {
                Log.e(TAG, "Errore durante il recupero dei pulsanti", e)
            }

            btnStart.setOnClickListener {
                Log.d(TAG, "onClick: btnStart premuto")
                // Double‐check permission at runtime
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    startRecording()
                } else {
                    Log.d(TAG, "onClick: richiesta permesso audio")
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }

            btnStop.setOnClickListener {
                Log.d(TAG, "onClick: btnStop premuto")
                stopRecording()
            }
            Log.d(TAG, "onCreate: Inizializzazione completata con successo")
        } catch (e: Exception) {
            Log.e(TAG, "Errore durante onCreate", e)
        }
    }

    @SuppressLint("MissingPermission") // since we've already checked it above
    private fun startRecording() {
        Log.d(TAG, "startRecording: Avvio registrazione")
        // ensure we don't start twice
        if (audioRecord != null) {
            Log.d(TAG, "startRecording: Registrazione già avviata")
            return
        }

        val sampleRate = 44_100
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        Log.d(TAG, "startRecording: bufferSize=$bufferSize")

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            ).apply { startRecording() }
            Log.d(TAG, "startRecording: AudioRecord inizializzato e avviato")
        } catch (iae: IllegalArgumentException) {
            Log.e(TAG, "Errore durante la creazione di AudioRecord", iae)
            Toast.makeText(this, "Cannot start audio record", Toast.LENGTH_SHORT).show()
            return
        } catch (se: SecurityException) {
            Log.e(TAG, "Permesso negato per AudioRecord", se)
            Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
            return
        }

        recordingJob = CoroutineScope(Dispatchers.IO).launch {
            Log.d(TAG, "startRecording: Coroutine avviata")
            val shortBuffer = ShortArray(bufferSize)
            while (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                try {
                    val read = audioRecord!!.read(shortBuffer, 0, shortBuffer.size)
                    if (read > 0) {
                        // convert to normalized float samples in [-1f,1f]
                        val floatBuffer = FloatArray(read) { i ->
                            shortBuffer[i] / Short.MAX_VALUE.toFloat()
                        }
                        val sample = AudioSample(floatBuffer)
                        audioSample = sample.samples
                        withContext(Dispatchers.Main) {
                            try {
                                waveformView.setAudioSample(sample)
                                Log.d(TAG, "startRecording: Aggiornato campione audio")
                            } catch (e: Exception) {
                                Log.e(TAG, "Errore durante l'aggiornamento della vista", e)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Errore durante la lettura dell'audio", e)
                    break
                }
            }
        }
    }

    private fun stopRecording() {
        /*val locationHelper = LocationHelper(this)
        locationHelper.getCurrentLocation { location ->
            if (location != null) {
                Log.d(TAG, "stopRecording: Current location: $location")
                val latitude = location.latitude
                val longitude = location.longitude

                val audioData = audioSample?:run {
                    Log.e(TAG, "stopRecording: Audio data is null")
                    return@getCurrentLocation
                }

                val duration = calculateAudioDuration(audioData)
                val sessionHelper = SessionManager(this)
                val username = sessionHelper.getUsernameFromSession()

                val dataSender = DataSender()
                dataSender.sendAudioData(
                    username = username,
                    audioData = audioData,
                    latitude = latitude,
                    longitude = longitude,
                    duration = duration
                )
            } else {
                Log.e(TAG, "stopRecording: Unable to get current location")
                Toast.makeText(this, "Unable to get current location", Toast.LENGTH_SHORT).show()
            }
        }*/

        Log.d(TAG, "stopRecording: Arresto registrazione")
        recordingJob?.cancel()
        audioRecord?.let {
            try {
                it.stop()
                it.release()
                Log.d(TAG, "stopRecording: AudioRecord fermato e rilasciato")
            } catch (e: Exception) {
                Log.e(TAG, "Errore durante l'arresto della registrazione", e)
            }
        }
        audioRecord = null

        // Invia il JSON al server
        val json = JSONObject().apply {
            put("message", "ciao")
        }
        val requestBody = json.toString().toRequestBody("application/json".toMediaType())

        val client = OkHttpClient()
        val request = Request.Builder()
            .url("http://192.168.117.250:5000/upload") // Sostituisci <server-url> con l'URL del tuo server
            .post(requestBody)
            .build()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "stopRecording: Invio richiesta al server")
                val response = client.newCall(request).execute()
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        Log.d(TAG, "stopRecording: JSON inviato con successo")
                        Toast.makeText(this@SoundSamplingActivity, "Dati inviati con successo", Toast.LENGTH_SHORT).show()
                    } else {
                        Log.e(TAG, "stopRecording: Errore durante l'invio dei dati - ${response.code}")
                        Toast.makeText(this@SoundSamplingActivity, "Errore durante l'invio dei dati", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "stopRecording: Eccezione durante l'invio dei dati", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SoundSamplingActivity, "Errore di connessione", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy: Sound Sampling Activity destroyed")
        stopRecording()
        super.onDestroy()
    }

    fun calculateAudioDuration(audioData: FloatArray, sampleRate: Int = 44100, channelCount: Int = 1): Int {
        val bytesPerSample = 2 // PCM 16-bit
        val totalSamples = audioData.size / (bytesPerSample * channelCount)
        return totalSamples / sampleRate // Durata in secondi
    }
}
