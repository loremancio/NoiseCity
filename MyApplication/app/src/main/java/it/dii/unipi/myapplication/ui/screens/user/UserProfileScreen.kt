package it.dii.unipi.myapplication.ui.screens.user

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import it.dii.unipi.myapplication.R
import it.dii.unipi.myapplication.app.Config
import it.dii.unipi.myapplication.model.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class UserProfileScreen : Fragment() {

    companion object {
        private const val TAG = "UserProfileScreen"
        private const val BASE_URL = Config.BASE_URL+ "/profile" // Sostituisci con il tuo IP reale
    }

    data class Achievement(val title: String, val description: String)
    data class UserAchievements(val username: String, val achievements: List<Achievement>, val exposure_high: String, val exposure_low: String)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_user_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fetchUserSummary(view)
    }

    private fun fetchUserSummary(view: View) {
        val client = OkHttpClient()
        val baseUrl = BASE_URL
        val httpUrl = baseUrl
            .toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("username", SessionManager(requireContext()).getUsernameFromSession())
            ?.build()
            ?: throw Exception("URL non valido: $baseUrl")

        val request = Request.Builder()
            .url(httpUrl)
            .get()
            .addHeader("Cookie", SessionManager(requireContext()).getCookieFromSession())
            .build()

        lifecycleScope.launch {
            try {
                val userAchievements = withContext(Dispatchers.IO) {
                    val response = client.newCall(request).execute()
                    if (!response.isSuccessful) throw Exception("Errore HTTP: ${response.code}")
                    val body = response.body?.string() ?: throw Exception("Corpo vuoto")

                    val json = JSONObject(body)
                    val username = json.getString("username")
                    val achievementsJson = json.getJSONArray("achievements")
                    val exposure_high = json.getString("exposure_high")
                    val exposure_low = json.getString("exposure_low")

                    val achievements = mutableListOf<Achievement>()
                    for (i in 0 until achievementsJson.length()) {
                        val a = achievementsJson.getJSONObject(i)
                        achievements.add(Achievement(a.getString("title"), a.getString("description")))
                    }

                    UserAchievements(username, achievements, exposure_high, exposure_low)
                }

                withContext(Dispatchers.Main) {
                    view.findViewById<TextView>(R.id.tvUserName).text = userAchievements.username + "'s Profile"

                    val badgeData = listOf(
                        Triple("City Explorer", R.id.iconBadgeCities to R.id.countTextViewCities, R.id.blockBadgeCities),
                        Triple("World Traveler", R.id.iconBadgeCountries to R.id.countTextViewCountries, R.id.blockBadgeCountries),
                        Triple("Measurement Master", R.id.iconBadgeRegistrations to R.id.countTextViewRegistered, R.id.blockBadgeRegistrations)
                    )

                    for ((title, ids, layoutId) in badgeData) {
                        val (iconId, textId) = ids
                        val match = userAchievements.achievements.firstOrNull { it.title == title }

                        val blockLayout = view.findViewById<androidx.cardview.widget.CardView>(layoutId)

                        if (match != null) {
                            view.findViewById<ImageView>(iconId).setImageResource(R.drawable.ic_badge_col)
                            view.findViewById<TextView>(textId).text = match.description
                            blockLayout.visibility = View.VISIBLE
                        } else {
                            blockLayout.visibility = View.GONE
                        }
                    }

                    view.findViewById<TextView>(R.id.textExpositionHigh).text = "You have been exposed to high noise for " + userAchievements.exposure_high + " seconds"
                    view.findViewById<TextView>(R.id.textExpositionLow).text = "You have been exposed to low noise for " + userAchievements.exposure_low + " seconds"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Errore nella richiesta OkHttp", e)
            }
        }
    }
}
