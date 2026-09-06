package com.example.locationsofi

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationUtils
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val logoContainer: LinearLayout = findViewById(R.id.logoContainer)
        val scaleAnim = AnimationUtils.loadAnimation(this, R.anim.scale_in)
        logoContainer.startAnimation(scaleAnim)

        Handler(Looper.getMainLooper()).postDelayed({
            checkNavigation()
        }, 2000)
    }

    private fun checkNavigation() {
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val isOnboardingCompleted = prefs.getBoolean("key_onboarding_completed", false)

        val targetIntent = when {
            !isOnboardingCompleted -> Intent(this, OnboardingActivity::class.java)
            ApiClient.isLoggedIn(this) -> Intent(this, MainActivity::class.java)
            else -> Intent(this, LoginActivity::class.java)
        }

        startActivity(targetIntent)
        finish()
    }
}
