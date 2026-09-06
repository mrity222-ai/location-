package com.example.locationsofi

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class LoginActivity : AppCompatActivity() {

    private lateinit var btnTabPassword: Button
    private lateinit var btnTabOtp: Button
    private lateinit var layoutPasswordForm: LinearLayout
    private lateinit var layoutOtpForm: LinearLayout

    // Password Form Views
    private lateinit var etPassEmail: EditText
    private lateinit var etPassPassword: EditText
    private lateinit var btnTogglePassword: ImageView
    private lateinit var cbRememberMe: CheckBox
    private lateinit var tvForgotPassword: TextView
    private lateinit var btnLoginPassword: Button

    // OTP Form Views
    private lateinit var etOtpEmail: EditText
    private lateinit var btnSendOtp: Button
    private lateinit var etOtpCode: EditText
    private lateinit var tvOtpTimer: TextView
    private lateinit var tvResendOtp: TextView
    private lateinit var btnLoginOtp: Button

    private lateinit var tvGoRegister: TextView

    private var isPasswordVisible = false
    private var countDownTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Session check
        val userPrefs = getSharedPreferences("user_session", Context.MODE_PRIVATE)
        if (userPrefs.contains("auth_token")) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        btnTabPassword = findViewById(R.id.btnTabPassword)
        btnTabOtp = findViewById(R.id.btnTabOtp)
        layoutPasswordForm = findViewById(R.id.layoutPasswordForm)
        layoutOtpForm = findViewById(R.id.layoutOtpForm)

        etPassEmail = findViewById(R.id.etPassEmail)
        etPassPassword = findViewById(R.id.etPassPassword)
        btnTogglePassword = findViewById(R.id.btnTogglePassword)
        cbRememberMe = findViewById(R.id.cbRememberMe)
        tvForgotPassword = findViewById(R.id.tvForgotPassword)
        btnLoginPassword = findViewById(R.id.btnLoginPassword)

        etOtpEmail = findViewById(R.id.etOtpEmail)
        btnSendOtp = findViewById(R.id.btnSendOtp)
        etOtpCode = findViewById(R.id.etOtpCode)
        tvOtpTimer = findViewById(R.id.tvOtpTimer)
        tvResendOtp = findViewById(R.id.tvResendOtp)
        btnLoginOtp = findViewById(R.id.btnLoginOtp)

        tvGoRegister = findViewById(R.id.tvGoRegister)

        // Tab Switching
        btnTabPassword.setOnClickListener { switchTab(isPasswordTab = true) }
        btnTabOtp.setOnClickListener { switchTab(isPasswordTab = false) }

        // Navigation to Register
        tvGoRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        // Toggle Password Visibility
        btnTogglePassword.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            if (isPasswordVisible) {
                etPassPassword.inputType = InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                btnTogglePassword.setColorFilter(Color.parseColor("#2563EB"))
            } else {
                etPassPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                btnTogglePassword.setColorFilter(Color.parseColor("#64748B"))
            }
            etPassPassword.setSelection(etPassPassword.text.length)
        }

        // Forgot Password Link
        tvForgotPassword.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Reset Password 🔑")
                .setMessage("Please contact your LocationSofi Administrator or check your registered email for password recovery steps.")
                .setPositiveButton("OK", null)
                .show()
        }

        // Handle Password Login
        btnLoginPassword.setOnClickListener {
            val email = etPassEmail.text.toString().trim()
            val pass = etPassPassword.text.toString().trim()

            if (email.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Please enter both Email ID and Password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnLoginPassword.isEnabled = false
            btnLoginPassword.text = "Signing In..."

            ApiClient.loginWithPassword(this, email, pass) { response ->
                runOnUiThread {
                    btnLoginPassword.isEnabled = true
                    btnLoginPassword.text = "Sign In 🔐"

                    if (response.success && response.token != null) {
                        saveSessionAndContinue(response.token, response.name ?: "User", response.email ?: email)
                    } else {
                        Toast.makeText(this, "Login Failed: ${response.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        // Send OTP Action
        btnSendOtp.setOnClickListener { triggerSendOtp() }
        tvResendOtp.setOnClickListener { triggerSendOtp() }

        // Handle OTP Login Verification
        btnLoginOtp.setOnClickListener {
            val email = etOtpEmail.text.toString().trim()
            val otp = etOtpCode.text.toString().trim()

            if (email.isEmpty() || otp.isEmpty()) {
                Toast.makeText(this, "Please enter Email ID and 6-digit OTP code", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnLoginOtp.isEnabled = false
            btnLoginOtp.text = "Verifying..."

            ApiClient.loginWithOtp(this, email, otp) { response ->
                runOnUiThread {
                    btnLoginOtp.isEnabled = true
                    btnLoginOtp.text = "Verify & Sign In 🚀"

                    if (response.success && response.token != null) {
                        saveSessionAndContinue(response.token, response.name ?: "User", response.email ?: email)
                    } else {
                        Toast.makeText(this, "Verification Failed: ${response.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun triggerSendOtp() {
        val email = etOtpEmail.text.toString().trim()
        if (email.isEmpty()) {
            Toast.makeText(this, "Please enter your Email ID first", Toast.LENGTH_SHORT).show()
            return
        }

        btnSendOtp.isEnabled = false
        btnSendOtp.text = "Sending..."

        ApiClient.sendOtp(this, email) { response ->
            runOnUiThread {
                btnSendOtp.isEnabled = true
                btnSendOtp.text = "Send OTP"

                if (response.success) {
                    var msg = "OTP Sent to $email!"
                    if (response.otp != null) {
                        msg += " (Test OTP: ${response.otp})"
                        etOtpCode.setText(response.otp)
                    }
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                    startOtpTimer()
                } else {
                    Toast.makeText(this, "OTP Error: ${response.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun startOtpTimer() {
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(60000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val sec = millisUntilFinished / 1000
                tvOtpTimer.text = "OTP expires in: ${sec}s"
                tvResendOtp.isEnabled = false
            }

            override fun onFinish() {
                tvOtpTimer.text = "OTP expired"
                tvResendOtp.isEnabled = true
            }
        }.start()
    }

    private fun switchTab(isPasswordTab: Boolean) {
        if (isPasswordTab) {
            btnTabPassword.background = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.shape_tab_active_pill)
            btnTabPassword.setTextColor(Color.WHITE)
            btnTabOtp.background = null
            btnTabOtp.setTextColor(Color.parseColor("#64748B"))

            layoutPasswordForm.visibility = View.VISIBLE
            layoutOtpForm.visibility = View.GONE
        } else {
            btnTabOtp.background = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.shape_tab_active_pill)
            btnTabOtp.setTextColor(Color.WHITE)
            btnTabPassword.background = null
            btnTabPassword.setTextColor(Color.parseColor("#64748B"))

            layoutOtpForm.visibility = View.VISIBLE
            layoutPasswordForm.visibility = View.GONE
        }
    }

    private fun saveSessionAndContinue(token: String, name: String, email: String) {
        val userPrefs = getSharedPreferences("user_session", Context.MODE_PRIVATE)
        userPrefs.edit()
            .putString("auth_token", token)
            .putString("user_name", name)
            .putString("user_email", email)
            .apply()

        Toast.makeText(this, "Welcome back, $name!", Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}
