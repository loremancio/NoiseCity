package it.dii.unipi.myapplication.model

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Handles audio recording and periodic sending of noise measurements.
 */
class AudioRecorder (
    private val context: Context,
){
    companion object {
        private const val TAG = "AudioRecorder"
        private const val SAMPLE_RATE = 44_100
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private val audioSender = DataSender(context)
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

    @SuppressLint("MissingPermission")
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
                            val sample = AudioSample(floatBuffer)
                           
                            withContext(Dispatchers.Main) {
                                sampleCallback?.invoke(sample)
                            }
                            launch(Dispatchers.Default) {
                                audioSender.sendToServer(sample)
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

    fun stopRecording() {
        recordingJob?.cancel()
        audioRecord?.apply {
            stop()
            release()
        }
        audioRecord = null
    }
   
    fun isRecording(): Boolean =
        audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING
}

