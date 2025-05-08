package it.dii.unipi.myapplication.model

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

    // Function to send audio sample data to the server
    fun sendAudioData(
        username: String,
        audioData: ByteArray,
        latitude: Double,
        longitude: Double,
        duration: Int
    ) {
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

        try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                println("Data sent successfully: ${response.body?.string()}")
            } else {
                println("Failed to send data: ${response.code}")
            }
        } catch (e: IOException) {
            println("Network Error: ${e.message}")
        }
    }
}