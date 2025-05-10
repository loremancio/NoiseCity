package it.dii.unipi.myapplication.model

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {
    private val preferences: SharedPreferences =
        context.getSharedPreferences("user_session", Context.MODE_PRIVATE)

    fun getUsernameFromSession(): String {
        return preferences.getString("username", "Unknown") ?: "Unknown"
    }

    fun saveUsernameToSession(username: String) {
        preferences.edit().putString("username", username).apply()
    }
}