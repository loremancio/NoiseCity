package it.dii.unipi.myapplication.model

import android.content.Context
import android.location.Location
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.withContext
import android.util.Log
import android.widget.Toast
import it.dii.unipi.myapplication.app.Config
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
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
import kotlin.math.log10
import kotlin.math.sqrt

class DataSender(
  private val context: Context,
) {
  companion object {
    private const val TAG = "DataSender"
    private const val SAMPLE_RATE = 44_100
    private const val JSON_TYPE = "application/json; charset=utf-8"
    private const val MIN_RMS = 1e-8
  }

  private val client = OkHttpClient()
  private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
  private val username = SessionManager(context).getUsernameFromSession()
  private val cookie = SessionManager(context).getCookieFromSession()

  private var sumSquares = 0.0
  private var sampleCount = 0

  fun processBuffer(buffer: FloatArray) {
    var offset = 0
    while (offset < buffer.size) {
      val remain = SAMPLE_RATE - sampleCount
      val chunk = minOf(buffer.size - offset, remain)
      for (i in 0 until chunk) {
        val v = buffer[offset + i]
        sumSquares += v * v
        sampleCount++
      }
      offset += chunk
      if (sampleCount >= SAMPLE_RATE) {
        val rms = sqrt(sumSquares / SAMPLE_RATE)
        val db = 20 * log10(maxOf(rms, MIN_RMS))
        val durationSec = sampleCount.toDouble() / SAMPLE_RATE
        sendToServer(db.toFloat(), durationSec)
        sumSquares = 0.0
        sampleCount = 0
      }
    }
  }

  private fun sendToServer(noiseLevel: Float, durationSec: Double) {
    // Directly call LocationHelper without suspend
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
            val responseBodyString = resp.body?.string() ?: throw Exception("Corpo vuoto") // Read body once
            if (resp.isSuccessful) {
              Log.d(TAG, "sendToServer: Data sent successfully: $responseBodyString") // More detailed success log
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
                Log.d(TAG, "No achievements reached") // More detailed log
              }
            } else {
              Log.e(TAG, "Error sending data: Code ${resp.code}, Message: ${resp.message}, Response: $responseBodyString") // More detailed error
            }
          }
          /*val request2 = Request.Builder()
            .url("${Config.BASE_URL}/achievements_reached")
            .build()

          client.newCall(request2).execute().use { resp ->
                if (resp.isSuccessful) {
                  val responseBodyString = resp.body?.string() ?: throw Exception("Corpo vuoto")// Read body once
                  Log.d(TAG, "sendToServer: Achievements data received successfully: $responseBodyString") // More detailed success log
                  val json = JSONArray(responseBodyString)
                  if (json.length() > 0) {
                    val ach = json.getJSONObject(0)
                    val title = ach.getString("title")
                    val description = ach.getString("description")
                    withContext(Dispatchers.Main) {
                      Toast.makeText(context, "$title: $description", Toast.LENGTH_LONG).show()
                    }
                  } else {
                    Log.d(TAG, "No achievements reached") // More detailed log
                  }
                } else {
                    Log.d(TAG, "No receiving achievements data: Code ${resp.code}, Message: ${resp.message}") // More detailed error
                }
            }*/
        } catch (e: Exception) {
          Log.e(TAG, "sendToServer: Exception during network operation", e)
        }
      }
    }
  }
}
