package it.dii.unipi.myapplication.model

import android.provider.MediaStore.Audio
import it.dii.unipi.myapplication.app.Config
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date

/**
 * Repository to handle data sending.
 */
class DataSender {
    private val client = OkHttpClient()

    fun sendAudioData(
        username: String,
        audioData: FloatArray,
        latitude: Double,
        longitude: Double,
        duration: Int
    ): AudioResult {
        val json = JSONObject()
            .put("username", username)
            .put("audio_data", audioData)
            .put("latitude", latitude)
            .put("longitude", longitude)
            .put("duration", duration)
            .put("timestamp", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).format(Date()))
            .toString()

        val requestBody = json.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("${Config.BASE_URL}/upload")
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .build()

        return try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val cookie = response.header("Set-Cookie")
                if (cookie != null) {
                    AudioResult.Success(cookie)
                } else {
                    AudioResult.Error("Cookie not found in response")
                }
            } else {
                AudioResult.Error("Error: ${response.code}")
            }
        } catch (e: IOException) {
            AudioResult.Error("Network Error: ${e.message}")
        }
    }
}

sealed class AudioResult {
    data class Success(val cookie: String) : AudioResult()
    data class Error(val message: String) : AudioResult()
}