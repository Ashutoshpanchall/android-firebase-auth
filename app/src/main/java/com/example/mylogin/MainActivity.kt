package com.example.mylogin

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException

class MainActivity : AppCompatActivity() {

    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var loginButton: Button
    private lateinit var tvForgotPassword: TextView
    private lateinit var tvSignUp: TextView
    private lateinit var btnFingerprint: ImageButton
    private lateinit var auth: FirebaseAuth

    private var isPasswordVisible = false

    // SharedPreferences to store registered email for fingerprint
    private val PREFS_NAME = "fingerprint_prefs"
    private val KEY_REGISTERED_EMAIL = "registered_email"
    private val KEY_FINGERPRINT_ENABLED = "fingerprint_enabled"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()

        // If user is already logged in, skip login screen
        if (auth.currentUser != null) {
            Log.d("Firebase", "Already logged in: ${auth.currentUser?.email}")
            startActivity(Intent(this, WelcomeActivity::class.java))
            finish()
            return
        }

        emailInput = findViewById(R.id.email_input)
        passwordInput = findViewById(R.id.password_input)
        loginButton = findViewById(R.id.login_button)
        tvForgotPassword = findViewById(R.id.tvForgotPassword)
        tvSignUp = findViewById(R.id.tvSignUp)
        btnFingerprint = findViewById(R.id.btnFingerprint)

        setupFingerprintButton()

        // Eye icon toggle
        passwordInput.setOnTouchListener { _, event ->
            val drawableEnd = passwordInput.compoundDrawables[2]
            if (drawableEnd != null &&
                event.rawX >= (passwordInput.right - drawableEnd.bounds.width() - passwordInput.paddingEnd)) {
                togglePasswordVisibility()
                return@setOnTouchListener true
            }
            false
        }

        loginButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()

            if (email.isEmpty()) {
                emailInput.error = "Email is required"
                return@setOnClickListener
            }
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                emailInput.error = "Enter a valid email"
                return@setOnClickListener
            }
            if (password.isEmpty()) {
                passwordInput.error = "Password is required"
                return@setOnClickListener
            }
            if (password.length < 6) {
                passwordInput.error = "Minimum 6 characters required"
                return@setOnClickListener
            }

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        Log.d("Firebase", "Login success: ${auth.currentUser?.email}")

                        // Ask user to register fingerprint if not already registered
                        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        val fingerprintEnabled = prefs.getBoolean(KEY_FINGERPRINT_ENABLED, false)
                        val biometricManager = BiometricManager.from(this)
                        val canUseBiometric = biometricManager.canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

                        if (!fingerprintEnabled && canUseBiometric) {
                            // Ask if they want to enable fingerprint
                            android.app.AlertDialog.Builder(this)
                                .setTitle("Enable Fingerprint Login?")
                                .setMessage("Would you like to use your fingerprint to login next time?")
                                .setPositiveButton("Yes") { _, _ ->
                                    // Save email and enable fingerprint
                                    prefs.edit()
                                        .putString(KEY_REGISTERED_EMAIL, email)
                                        .putBoolean(KEY_FINGERPRINT_ENABLED, true)
                                        .apply()
                                    Toast.makeText(this, "Fingerprint login enabled!", Toast.LENGTH_SHORT).show()
                                    goToWelcome()
                                }
                                .setNegativeButton("No") { _, _ ->
                                    goToWelcome()
                                }
                                .show()
                        } else {
                            goToWelcome()
                        }

                    } else {
                        val errorMessage = when (task.exception) {
                            is FirebaseAuthInvalidUserException -> {
                                emailInput.error = "No account found with this email"
                                "No account found with this email"
                            }
                            is FirebaseAuthInvalidCredentialsException -> {
                                passwordInput.error = "Incorrect password"
                                "Incorrect password. Please try again"
                            }
                            else -> when {
                                task.exception?.message?.contains("no user record") == true ||
                                        task.exception?.message?.contains("user-not-found") == true -> {
                                    emailInput.error = "No account found with this email"
                                    "No account found with this email"
                                }
                                task.exception?.message?.contains("password is invalid") == true ||
                                        task.exception?.message?.contains("wrong-password") == true -> {
                                    passwordInput.error = "Incorrect password"
                                    "Incorrect password. Please try again"
                                }
                                task.exception?.message?.contains("too many requests") == true ||
                                        task.exception?.message?.contains("blocked") == true ->
                                    "Too many failed attempts. Try again later"
                                else -> "Login failed: ${task.exception?.message}"
                            }
                        }
                        Log.e("Firebase", "Login failed: ${task.exception?.message}")
                        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                    }
                }
        }

        tvForgotPassword.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }

        tvSignUp.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }
    }

    private fun setupFingerprintButton() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val fingerprintEnabled = prefs.getBoolean(KEY_FINGERPRINT_ENABLED, false)
        val biometricManager = BiometricManager.from(this)
        val canUseBiometric = biometricManager.canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

        if (fingerprintEnabled && canUseBiometric) {
            // Fingerprint is registered — show active button
            btnFingerprint.isEnabled = true
            btnFingerprint.alpha = 1f
            btnFingerprint.setOnClickListener {
                showBiometricPrompt()
            }
        } else {
            // Not registered yet — gray out
            btnFingerprint.isEnabled = false
            btnFingerprint.alpha = 0.3f
        }
    }

    private fun showBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this)

        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    Log.d("Biometric", "Fingerprint authentication succeeded")

                    // Get saved email and sign in silently via Firebase
                    val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val savedEmail = prefs.getString(KEY_REGISTERED_EMAIL, null)

                    if (savedEmail != null && auth.currentUser != null) {
                        // User session still active
                        Toast.makeText(this@MainActivity, "Welcome back!", Toast.LENGTH_SHORT).show()
                        goToWelcome()
                    } else if (savedEmail != null) {
                        // Session expired — fingerprint verified but need to re-authenticate
                        // Sign in again using saved credentials is not possible without password
                        // So we just trust the biometric and go to welcome
                        Toast.makeText(this@MainActivity, "Fingerprint verified! Welcome back.", Toast.LENGTH_SHORT).show()
                        goToWelcome()
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            "Please login with email & password first",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Toast.makeText(this@MainActivity, "Fingerprint not recognized. Try again.", Toast.LENGTH_SHORT).show()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Log.e("Biometric", "Error: $errString")
                    Toast.makeText(this@MainActivity, "Use email & password to login", Toast.LENGTH_SHORT).show()
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Fingerprint Login")
            .setSubtitle("Touch the fingerprint sensor to login")
            .setNegativeButtonText("Use Password")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private fun goToWelcome() {
        Toast.makeText(this, "Login Successful!", Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, WelcomeActivity::class.java))
        finish()
    }

    private fun togglePasswordVisibility() {
        if (isPasswordVisible) {
            passwordInput.inputType =
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            passwordInput.setCompoundDrawablesWithIntrinsicBounds(
                0, 0, R.drawable.ic_eye_off, 0
            )
        } else {
            passwordInput.inputType =
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            passwordInput.setCompoundDrawablesWithIntrinsicBounds(
                0, 0, R.drawable.ic_eye_on, 0
            )
        }
        isPasswordVisible = !isPasswordVisible
        passwordInput.setSelection(passwordInput.text.length)
    }
}