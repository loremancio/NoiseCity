package it.dii.unipi.myapplication.model

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat

/**
 * Model class that handles audio recording functionality
 */
class AudioRecorder {
    companion object {
        private const val TAG = "AudioRecorder"
        private const val SAMPLE_RATE = 44_100
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val bufferSize: Int by lazy {
        AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
    }
    private var avg: Double = 0.0
    private var audioSamples: FloatArray? = null
    
    private var sampleCallback: ((AudioSample) -> Unit)? = null
    
    fun setOnSampleAvailableListener(callback: (AudioSample) -> Unit) {
        sampleCallback = callback
    }

    @SuppressLint("MissingPermission") // Permission should be checked by caller
    fun startRecording(): Boolean {
        if (audioRecord != null) {
            Log.d(TAG, "Recording already started")
            return false
        }

        Log.d(TAG, "Starting recording with bufferSize=$bufferSize")

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            ).apply { startRecording() }
            
            recordingJob = CoroutineScope(Dispatchers.IO).launch {
                Log.d(TAG, "Recording coroutine started")
                val shortBuffer = ShortArray(bufferSize)
                
                while (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    try {
                        val read = audioRecord!!.read(shortBuffer, 0, shortBuffer.size)
                        if (read > 0) {
                            // convert to normalized float samples in [-1f,1f]
                            val floatBuffer = FloatArray(read) { i ->
                                shortBuffer[i] / Short.MAX_VALUE.toFloat()
                            }
                            audioSamples = floatBuffer
                            val sample = AudioSample(floatBuffer)
                            avg = sample.getAvg()

                            withContext(Dispatchers.Main) {
                                sampleCallback?.invoke(sample)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error reading audio", e)
                        break
                    }
                }
            }
            return true
        } catch (iae: IllegalArgumentException) {
            Log.e(TAG, "Error creating AudioRecord", iae)
            return false
        } catch (se: SecurityException) {
            Log.e(TAG, "Permission denied for AudioRecord", se)
            return false
        }
    }

    fun stopRecording(context: Context) {
        Log.d(TAG, "Stopping recording")

        if (audioRecord == null) {
            Log.d(TAG, "AudioRecord is null, nothing to stop")
            return
        }

        // read the audio data from audioRecord
        val locationHelper = LocationHelper(context)
        val sessionManager = SessionManager(context)
        val username = sessionManager.getUsernameFromSession()
        val currentTimestamp = System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        val formattedDate = dateFormat.format(currentTimestamp)
        var duration = 0.0
        audioSamples?.let { samples ->
            duration = calculateAudioDuration(samples)
        } ?: run {
            Log.e(TAG, "stopRecording: No audio samples available")
            return
        }

        locationHelper.getCurrentLocation { location ->
            if (location != null) {
                Log.d(TAG, "stopRecording: Current location: $location")
                val latitude = location.latitude
                val longitude = location.longitude

                val json = JSONObject()
                    .put("latitude", latitude)
                    .put("longitude", longitude)
                    .put("username", username)
                    .put("audio", avg)
                    .put("timestamp", currentTimestamp)
                    .put("formattedDate", formattedDate)
                    .put("duration", duration)
                    .toString()

                val requestBody = json.toRequestBody("application/json".toMediaType())
                val request = okhttp3.Request.Builder()
                    .url("http://192.168.117.250:5000/upload")
                    .post(requestBody)
                    .addHeader("Content-Type", "application/json")
                    .build()

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val response = okhttp3.OkHttpClient().newCall(request).execute()
                        if (response.isSuccessful) {
                            Log.d(TAG, "Request successful: ${response.body?.string()}")
                        } else {
                            Log.e(TAG, "Request failed: ${response.code}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Network error", e)
                    }
                }
            } else {
                Log.e(TAG, "stopRecording: Unable to get current location")
            }
        }

        recordingJob?.cancel()
        audioRecord?.let {
            try {
                it.stop()
                it.release()
                Log.d(TAG, "AudioRecord stopped and released")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping recording", e)
            }
        }
        audioRecord = null
    }
    
    fun isRecording(): Boolean {
        return audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING
    }

    fun calculateAudioDuration(audioData: FloatArray, sampleRate: Int = SAMPLE_RATE): Double {
        return audioData.size.toDouble() / sampleRate
    }
}