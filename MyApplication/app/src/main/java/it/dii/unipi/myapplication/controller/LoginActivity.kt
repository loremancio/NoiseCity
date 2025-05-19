package it.dii.unipi.myapplication.controller

import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import it.dii.unipi.myapplication.R
import it.dii.unipi.myapplication.model.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import it.dii.unipi.myapplication.MainActivity
import it.dii.unipi.myapplication.model.LoginResult
import it.dii.unipi.myapplication.model.SessionManager

/**
 * Activity that handles user login
 */
class LoginActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "LoginActivity"
    }

    private val authRepository = AuthRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // check if the user is already logged in, in this case redirect to MainActivity
        val sessionManager = SessionManager(this@LoginActivity)
        if (sessionManager.isLoggedIn()) {
            val intent = Intent(this@LoginActivity, MainActivity::class.java)
            startActivity(intent)
            finish()
        }

        setContentView(R.layout.activity_login)

        val usernameField = findViewById<EditText>(R.id.etUsername)
        val passwordField = findViewById<EditText>(R.id.etPassword)
        val loginButton = findViewById<Button>(R.id.btnLogin)
        val registerTextView = findViewById<TextView>(R.id.tvToRegister)

        loginButton.setOnClickListener {
            val username = usernameField.text.toString()
            val password = passwordField.text.toString()

            if (username.isNotEmpty() && password.isNotEmpty()) {
                performLogin(username, password)
            } else {
                MaterialAlertDialogBuilder(this@LoginActivity)
                    .setTitle("Login Error")
                    .setMessage("Please, insert username and password")
                    .show()
            }
        }

        registerTextView.setOnClickListener {
            val intent = Intent(this, RegistrationActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            currentFocus?.let {
                imm.hideSoftInputFromWindow(it.windowToken, 0)
            }
        }
        return super.onTouchEvent(event)
    }

    private fun performLogin(username: String, password: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val result = authRepository.login(username, password)
            runOnUiThread {
                when (result) {
                    is LoginResult.Success -> {
                        val sessionManager = SessionManager(this@LoginActivity)
                        sessionManager.saveUsernameToSession(username)
                        sessionManager.saveCookieToSession(result.cookie)

                        val intent = Intent(this@LoginActivity, MainActivity::class.java)
                        startActivity(intent)
                        finish()
                    }
                    is LoginResult.Error -> {
                        MaterialAlertDialogBuilder(this@LoginActivity)
                            .setTitle("Login Error")
                            .setMessage(result.message)
                            .show()
                    }
                }
            }
        }
    }
}