package it.dii.unipi.myapplication.model

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * SessionManager is responsible for managing user session data.
 * The data saved are the cookie and the username.
 */
class SessionManager(context: Context) {
    private val preferences: SharedPreferences =
        context.getSharedPreferences("user_session", Context.MODE_PRIVATE)

    fun getCookieFromSession(): String {
        return preferences.getString("cookie", "Unknown") ?: "Unknown"
    }

    fun getUsernameFromSession(): String {
        return preferences.getString("username", "Unknown") ?: "Unknown"
    }

    fun saveCookieToSession(cookie: String) {
        preferences.edit { putString("cookie", cookie) }
    }

    fun saveUsernameToSession(cookie: String) {
        preferences.edit { putString("username", cookie) }
    }

    fun isLoggedIn(): Boolean {
        return preferences.getString("cookie", null) != null
    }
}