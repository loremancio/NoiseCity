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
     * @return FloatArray di lunghezza N/2 con la magnitudine dello spettro.
     */
    private fun calculateFFT(audioData: FloatArray): FloatArray {
        var n = audioData.size
        if (n == 0) return FloatArray(0)

        // JTransforms' realForwardFull expects the input array to have a size that is a power of two
        // if n is not a power of two, we need to pad it.
        // However, realForwardFull actually handles non-power-of-two sizes by internally padding.
        // For simplicity and to match the user's provided logic structure,
        // we'll keep a similar padding check, though it might be redundant for some JTransforms methods.
        // Let's ensure n is appropriate for FFT or adjust.
        // The provided code snippet for pow2 calculation:
        // val pow2 = 1 shl (32 - Integer.numberOfLeadingZeros(n - 1))
        // This calculates the next power of two if n is not already one.
        // If n is already a power of two, `Integer.numberOfLeadingZeros(n-1)` might behave unexpectedly for n=1.
        // A more robust way to get the next power of two or keep n if it is:
        var effectiveN = n
        if (Integer.highestOneBit(effectiveN) != effectiveN && effectiveN > 1) { // if not a power of 2 and n > 1
            effectiveN = Integer.highestOneBit(effectiveN) shl 1 // next power of 2
        }
        // If n=1, highestOneBit(1) is 1. (1 shl 1) is 2. So for n=1, effectiveN becomes 2.
        // If n=0, this block is skipped.

        val dataToTransform: DoubleArray
        if (effectiveN != n || n == 1) { // Pad if n was not power of two, or if n was 1 (needs at least 2 for FFT)
             if (n==1 && effectiveN < 2) effectiveN = 2 // Ensure at least size 2 for n=1 case
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


        // JTransforms realForwardFull stores the result in a specific format.
        // The input array `dataToTransform` is used for output.
        // It should have size n for n real inputs.
        // The output is packed:
        // data[0] = R[0]
        // data[1] = R[n/2]
        // data[2*k] = R[k] for k=1..n/2-1
        // data[2*k+1] = I[k] for k=1..n/2-1
        DoubleFFT_1D(n.toLong()).realForward(dataToTransform)

        // Calculate magnitudes for the first n/2 points
        // The output array size for magnitudes will be n/2 + 1 (for DC and up to Nyquist)
        // However, the user's original code returned n/2. Let's stick to that for now.
        // If n is the (potentially padded) size, then we want n/2 magnitudes.
        val magnitudes = FloatArray(n / 2)

        magnitudes[0] = kotlin.math.abs(dataToTransform[0]).toFloat() // DC component (R[0])

        // For k = 1 to n/2 - 1
        // R[k] is at dataToTransform[2*k]
        // I[k] is at dataToTransform[2*k+1]
        // The Nyquist frequency component R[n/2] is at dataToTransform[1]
        // The loop should go up to n/2 -1 for the complex pairs
        for (i in 1 until n / 2) {
            val re = dataToTransform[2 * i]
            val im = dataToTransform[2 * i + 1]
            magnitudes[i] = sqrt(re * re + im * im).toFloat()
        }
        
        // If n is even, the Nyquist frequency (R[n/2]) is stored in dataToTransform[1].
        // The original code's loop `0 until n/2` implies it wants n/2 values.
        // If we include Nyquist, it would be (n/2)+1 values if we consider DC as index 0.
        // Let's assume the last element of magnitudes (index n/2 -1) should represent the highest freq before Nyquist or Nyquist itself.
        // If n/2 is the size, the last index is n/2 - 1.
        // If n is large enough (e.g. n >= 2), dataToTransform[1] contains R[n/2]
        // The loop for `magnitudes` goes from `i = 0` to `n/2 - 1`.
        // `magnitudes[0]` is DC.
        // `magnitudes[1]` to `magnitudes[n/2 - 1]` are from complex pairs.
        // If the user wants exactly n/2 magnitudes, and the last one should be meaningful:
        // For an even N, FFT produces N/2+1 complex values (DC, (N/2-1) positive freq, Nyquist).
        // The provided code `FloatArray(n/2)` means it expects n/2 values.
        // If n=2 (after padding for n=1), n/2 = 1. magnitudes has size 1. magnitudes[0] = abs(data[0]). Correct.
        // If n=4, n/2 = 2. magnitudes has size 2.
        //    magnitudes[0] = abs(data[0]) (DC)
        //    magnitudes[1] = sqrt(re(data[2])^2 + im(data[3])^2) (First harmonic)
        //    R[n/2] = R[2] is in data[1]. This is currently missed if loop is `1 until n/2`.
        // To include Nyquist as the last component if n/2 is the target size:
        if (n >= 2 && n / 2 > 0) { // Ensure there's space for Nyquist if n/2 is the last index
             if (n/2 -1 < magnitudes.size && n/2 -1 >=0 ) { // if magnitudes has space for it
                // This depends on how WaveformView expects the data.
                // If it expects n/2 points, and the last point should be highest freq:
                // For now, the loop `1 until n/2` correctly fills up to `magnitudes[n/2 - 1]` if n/2-1 is the last index for complex pairs.
                // The Nyquist frequency R[n/2] is at dataToTransform[1].
                // If n/2 is the length, the last index is (n/2)-1.
                // If n=4, n/2=2. magnitudes indices 0, 1.
                // magnitudes[0] = DC
                // magnitudes[1] = from re=data[2*1], im=data[2*1+1]
                // R[4/2]=R[2] is in data[1].
                // The current loop `1 until n/2` is correct for filling the `magnitudes` array of size `n/2`.
                // No special handling for Nyquist needed if the output array is strictly `n/2`.
             }
        }


        // Normalize magnitudes? Optional, but often done.
        // For now, returning raw magnitudes as per user's code.
        return magnitudes
    }
    private fun showCompensationDialog() {
        var compensationValue: Float? = null
        val editText = EditText(context)
        editText.hint = "Inserisci il valore di compensazione (dB)"

        AlertDialog.Builder(requireContext())
            .setTitle("Valore di Compensazione")
            .setMessage("Inserisci il valore di compensazione per il campionamento:")
            .setView(editText)
            .setPositiveButton("OK") { _, _ ->
                val input = editText.text.toString()
                try {
                    val compensationValue = input.toFloat()
                    Log.d("ciaooooooo", "Compensation value set to: $compensationValue")

                    dbHelper?.saveCompensationValue(compensationValue)
                } catch (e: NumberFormatException) {
                    Log.e("cioaooo", "Invalid compensation value entered", e)
                    compensationValue = null
                }
            }
            .setNegativeButton("Annulla") { _, _ ->
                Log.d("cioa", "Compensation dialog cancelled")
            }
            .setCancelable(false)
            .show()
    }
  
}