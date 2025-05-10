package it.dii.unipi.myapplication.model

import it.dii.unipi.myapplication.app.Config
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.heatmaps.WeightedLatLng
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.IOException

class HeatmapRepository {
    private val client = OkHttpClient()

    /**
     * Download the heatmap points.
     *
     * @param latitude center of the area
     * @param longitude center of the area
     * @param radiusKm radius in km
     */
    @Throws(IOException::class)
    suspend fun fetchHeatmap(
        latitude: Double,
        longitude: Double,
        radiusKm: Int = 5
    ): List<WeightedLatLng> {
        val baseUrl = "${Config.BASE_URL}/measurements"
        val httpUrl = baseUrl
            .toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("latitude", latitude.toString())
            ?.addQueryParameter("longitude", longitude.toString())
            ?.addQueryParameter("radius_km", radiusKm.toString())
            ?.build()
            ?: throw IOException("Invalid URL: $baseUrl")

        val request = Request.Builder()
            .url(httpUrl)
            .get()
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("Unexpected code ${response.code}")
        }

        val body = response.body?.string()
            ?: throw IOException("Empty response body")

        val arr = JSONArray(body)
        val list = mutableListOf<WeightedLatLng>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val lat = obj.getDouble("lat")
            val lon = obj.getDouble("lon")
            val intensity = obj.getDouble("intensity")
            list += WeightedLatLng(LatLng(lat, lon), intensity)
        }
        return list
    }
}
