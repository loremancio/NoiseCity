package it.dii.unipi.myapplication.controller

import android.os.Bundle
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import it.dii.unipi.myapplication.model.AuthRepository
import it.dii.unipi.myapplication.model.RegistrationResult
import it.dii.unipi.myapplication.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RegistrationActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "RegistrationActivity"
    }

    private val authRepository = AuthRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registration)

        val usernameField = findViewById<EditText>(R.id.etUsername)
        val passwordField = findViewById<EditText>(R.id.etPassword)
        val confirmPasswordField = findViewById<EditText>(R.id.etConfirmPassword)
        val registerButton = findViewById<Button>(R.id.btnRegister)
        val backButton = findViewById<Button>(R.id.btnBack)

        registerButton.setOnClickListener {
            val username = usernameField.text.toString()
            val password = passwordField.text.toString()
            val confirmPassword = confirmPasswordField.text.toString()

            if (username.isNotEmpty() && password.isNotEmpty() && confirmPassword.isNotEmpty() && password == confirmPassword) {
                performRegistration(username, password)
            } else {
                Toast.makeText(this, "Error in the fields", Toast.LENGTH_SHORT).show()
            }
        }
        backButton.setOnClickListener {
            finish()
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

    private fun performRegistration(username: String, password: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val result = authRepository.register(username, password)
            runOnUiThread {
                when (result) {
                    is RegistrationResult.Success -> {
                        Toast.makeText(this@RegistrationActivity, result.message, Toast.LENGTH_SHORT).show()
                        // the finish command goes back to the previous activity
                        finish()
                    }
                    is RegistrationResult.Error -> {
                        Toast.makeText(this@RegistrationActivity, result.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}