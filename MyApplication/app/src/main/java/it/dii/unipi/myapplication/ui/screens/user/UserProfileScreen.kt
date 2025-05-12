package it.dii.unipi.myapplication.ui.screens.user

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
        private const val BASE_URL = "http://192.168.64.250:5000/user_summary" // <-- Sostituisci con l'indirizzo corretto
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_user_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "UserProfileScreen: onViewCreated")
        fetchUserSummary(view)
    }

    private fun fetchUserSummary(view: View) {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url(BASE_URL)
            .build()

        lifecycleScope.launch {
            try {
                val (username, recordingsCount) = withContext(Dispatchers.IO) {
                    val response = client.newCall(request).execute()
                    if (!response.isSuccessful) throw Exception("Errore HTTP: ${response.code}")
                    val body = response.body?.string() ?: throw Exception("Corpo della risposta vuoto")
                    val json = JSONObject(body)
                    val username = json.getString("username")
                    val count = json.getInt("recordings_count")
                    Log.d(TAG, "Username: $username, Recordings Count: $count")
                    username to count
                }

                // Solo aggiornamento UI nel Main Thread
                view.findViewById<TextView>(R.id.tvUserName).text = username
                view.findViewById<TextView>(R.id.countTextViewRegistered).text = recordingsCount.toString()

            } catch (e: Exception) {
                Log.e(TAG, "Errore nella richiesta OkHttp", e)
            }
        }
    }

}
