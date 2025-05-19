package it.dii.unipi.myapplication.model

import android.content.Context
import kotlinx.coroutines.withContext
import android.util.Log
import android.widget.Toast
import it.dii.unipi.myapplication.app.Config
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DataSender(
  private val context: Context,
) {
  companion object {
    private const val TAG = "DataSender"
    private const val SAMPLE_RATE = 44_100
    private const val JSON_TYPE = "application/json; charset=utf-8"
  }

  private val client = OkHttpClient()
  private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
  private val username = SessionManager(context).getUsernameFromSession()
  private val cookie = SessionManager(context).getCookieFromSession()

    fun sendToServer(sample: AudioSample) {
      val noiseLevel = sample.averageDbWithCompensation(context)
      val durationSec = sample.samples.size.toDouble() / SAMPLE_RATE
      LocationHelper(context).getCurrentLocation { location ->
      if (location == null) {
        Log.e(TAG, "sendToServer: Location is null. Cannot send data.")
        return@getCurrentLocation
      }
      // Prepare and send HTTP request asynchronously on an IO-optimized dispatcher
      CoroutineScope(Dispatchers.IO).launch { // Explicitly use Dispatchers.IO for network
        try {
          val json = JSONObject().apply {
            put("user_id", username)
            put("noise_level", noiseLevel)
            put("duration", durationSec)
            put("timestamp", dateFormat.format(Date()))
            put("location", JSONObject().apply {
              put("type", "Point")
              put("coordinates", JSONArray().apply {
                put(location.longitude)
                put(location.latitude)
              })
            })
          }
          val body = json.toString().toRequestBody(JSON_TYPE.toMediaType())
          val request = Request.Builder()
            .url(Config.BASE_URL + "/measurements")
            .post(body)
            .addHeader("Cookie", cookie)
            .build()
          client.newCall(request).execute().use { resp ->
            val responseBodyString = resp.body?.string() ?: throw Exception("Empty Body") // Read body once
            if (resp.isSuccessful) {
              Log.d(TAG, "sendToServer: Data sent successfully: $responseBodyString")
              val jsonResponse = JSONObject(responseBodyString)
              val achievements = jsonResponse.getJSONArray("achievements")

              if (achievements.length() > 0) {
               withContext(Dispatchers.Main) {
                 for (i in 0 until achievements.length()) {
                   val ach = achievements.getJSONObject(i)
                   val title = ach.getString("title")
                   val description = ach.getString("description")
                   Toast.makeText(context, "You achieved $title: $description", Toast.LENGTH_LONG).show()
                 }
               }
              } else {
                Log.d(TAG, "No achievements reached")
              }
            } else {
              Log.e(TAG, "Error sending data: Code ${resp.code}, Message: ${resp.message}, Response: $responseBodyString")
            }
          }
        } catch (e: Exception) {
          Log.e(TAG, "sendToServer: Exception during network operation", e)
        }
      }
    }
  }
}
