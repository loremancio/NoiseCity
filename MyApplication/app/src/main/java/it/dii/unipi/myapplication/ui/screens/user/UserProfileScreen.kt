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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class UserProfileScreen : Fragment() {

    companion object {
        private const val TAG = "UserProfileScreen"
        private const val BASE_URL = "http://192.168.64.250:5000/user_summary" // Sostituisci con il tuo IP reale
    }

    data class Achievement(val title: String, val description: String)
    data class UserAchievements(val username: String, val achievements: List<Achievement>)

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
        val request = Request.Builder()
            .url(BASE_URL)
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

                    val achievements = mutableListOf<Achievement>()
                    for (i in 0 until achievementsJson.length()) {
                        val a = achievementsJson.getJSONObject(i)
                        achievements.add(Achievement(a.getString("title"), a.getString("description")))
                    }

                    UserAchievements(username, achievements)
                }

                withContext(Dispatchers.Main) {
                    view.findViewById<TextView>(R.id.tvUserName).text = userAchievements.username

                    val badgeData = listOf(
                        Triple("City Explorer", R.id.iconBadgeCities to R.id.countTextViewCities, R.id.blockBadgeCities),
                        Triple("World Traveler", R.id.iconBadgeCountries to R.id.countTextViewCountries, R.id.blockBadgeCountries),
                        Triple("Measurement Master", R.id.iconBadgeRegistrations to R.id.countTextViewRegistered, R.id.blockBadgeRegistrations)
                    )

                    for ((title, ids, layoutId) in badgeData) {
                        val (iconId, textId) = ids
                        val match = userAchievements.achievements.firstOrNull { it.title == title }

                        val blockLayout = view.findViewById<LinearLayout>(layoutId)

                        if (match != null) {
                            view.findViewById<ImageView>(iconId).setImageResource(R.drawable.ic_badge_col)
                            view.findViewById<TextView>(textId).text = match.description
                            blockLayout.visibility = View.VISIBLE
                        } else {
                            blockLayout.visibility = View.GONE
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Errore nella richiesta OkHttp", e)
            }
        }
    }
}
