package it.dii.unipi.myapplication.model

import it.dii.unipi.myapplication.app.Config
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

/**
 * Repository to handle authentication.
 */
class AuthRepository {

    private val client = OkHttpClient()

    /**
     * Try to login with the given username and password.
     * @return LoginResult.Success(cookie) if all right, LoginResult.Error(msg) if something goes wrong.
     */
    suspend fun login(username: String, password: String): LoginResult {
        val json = JSONObject()
            .put("username", username)
            .put("password", password)
            .toString()

        val requestBody = json.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("${Config.BASE_URL}/login")
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .build()

        return try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val cookie = response.header("Set-Cookie")
                if (cookie != null) {
                    LoginResult.Success(cookie)
                } else {
                    LoginResult.Error("Cookie not found in response")
                }
            } else {
                LoginResult.Error("Wrong credentials: ${response.code}")
            }
        } catch (e: IOException) {
            LoginResult.Error("Network Error: ${e.message}")
        }
    }

    /**
     * Try to register a new user with the given username and password.
     * @return RegistrationResult.Success(msg) if registration is successful, RegistrationResult.Error(msg) if something goes wrong.
     */
    suspend fun register(username: String, password: String): RegistrationResult {
        val json = JSONObject()
            .put("username", username)
            .put("password", password)
            .toString()

        val requestBody = json.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("${Config.BASE_URL}/register")
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .build()

        return try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                RegistrationResult.Success("Registration successful. Please log in.")
            } else {
                RegistrationResult.Error("Registration failed: ${response.code}")
            }
        } catch (e: IOException) {
            RegistrationResult.Error("Network Error: ${e.message}")
        }
    }
}

/**
 * Sealed class to represent the result of a login attempt.
 */
sealed class LoginResult {
    data class Success(val cookie: String) : LoginResult()
    data class Error(val message: String) : LoginResult()
}

/**
 * Sealed class to represent the result of a registration attempt.
 */
sealed class RegistrationResult {
    data class Success(val message: String) : RegistrationResult()
    data class Error(val message: String) : RegistrationResult()
}