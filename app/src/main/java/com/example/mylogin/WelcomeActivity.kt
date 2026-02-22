package com.example.mylogin

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class WelcomeActivity : AppCompatActivity() {

    private lateinit var tvWelcome: TextView
    private lateinit var btnLogout: Button
    private lateinit var auth: FirebaseAuth

    private val PREFS_NAME = "fingerprint_prefs"
    private val KEY_REGISTERED_EMAIL = "registered_email"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)

        auth = FirebaseAuth.getInstance()

        tvWelcome = findViewById(R.id.tvWelcome)
        btnLogout = findViewById(R.id.btnLogout)

        // Get email from Firebase if logged in, otherwise from saved fingerprint prefs
        val email = auth.currentUser?.email
            ?: getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_REGISTERED_EMAIL, "User")

        tvWelcome.text = "Welcome, $email!"

        btnLogout.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}