package com.example.mylogin

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException

class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var emailInput: EditText
    private lateinit var btnSendResetLink: Button
    private lateinit var tvBackToLogin: TextView
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)

        auth = FirebaseAuth.getInstance()

        emailInput = findViewById(R.id.email_input)
        btnSendResetLink = findViewById(R.id.btnSendResetLink)
        tvBackToLogin = findViewById(R.id.tvBackToLogin)

        btnSendResetLink.setOnClickListener {
            val email = emailInput.text.toString().trim()

            if (email.isEmpty()) {
                emailInput.error = "Please enter your email"
                return@setOnClickListener
            }
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                emailInput.error = "Enter a valid email address"
                return@setOnClickListener
            }

            // Disable button to prevent multiple clicks
            btnSendResetLink.isEnabled = false
            btnSendResetLink.text = "Sending..."

            auth.sendPasswordResetEmail(email)
                .addOnCompleteListener { task ->
                    btnSendResetLink.isEnabled = true
                    btnSendResetLink.text = "Send Reset Link"

                    if (task.isSuccessful) {
                        Toast.makeText(
                            this,
                            "Reset link sent to $email. Check your inbox.",
                            Toast.LENGTH_LONG
                        ).show()
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    } else {
                        val errorMessage = when (task.exception) {
                            is FirebaseAuthInvalidUserException -> {
                                emailInput.error = "No account found with this email"
                                "No account found with this email"
                            }
                            else -> when {
                                task.exception?.message?.contains("too many requests") == true ->
                                    "Too many attempts. Try again later"
                                else -> "Failed to send reset link: ${task.exception?.message}"
                            }
                        }
                        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                    }
                }
        }

        tvBackToLogin.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}