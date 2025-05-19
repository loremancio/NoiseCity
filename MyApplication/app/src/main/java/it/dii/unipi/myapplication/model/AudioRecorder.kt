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
        if (audioRecord != null) return false

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        ).apply { startRecording() }

        recordingJob = CoroutineScope(Dispatchers.IO).launch {
            val shortBuffer = ShortArray(bufferSize)
            val floatBufferSec = FloatArray(SAMPLE_RATE)
            var pos = 0

            while (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val read = audioRecord!!.read(shortBuffer, 0, shortBuffer.size)
                if (read <= 0) continue

                val tempFloat = FloatArray(read) { i ->
                    shortBuffer[i] / Short.MAX_VALUE.toFloat()
                }

                withContext(Dispatchers.Main) {
                    sampleCallback?.invoke(AudioSample(tempFloat))
                }

                var offset = 0
                while (offset < read) {
                    val toCopy = minOf(read - offset, SAMPLE_RATE - pos)
                    System.arraycopy(tempFloat, offset, floatBufferSec, pos, toCopy)
                    pos += toCopy
                    offset += toCopy
                    // Check if we have filled one second of audio
                    if (pos == SAMPLE_RATE) {
                        val oneSecSample = AudioSample(floatBufferSec.copyOf())
                        launch(Dispatchers.Default) {
                            audioSender.sendToServer(oneSecSample)
                        }
                        pos = 0
                    }
                }
            }
        }
        return true
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

