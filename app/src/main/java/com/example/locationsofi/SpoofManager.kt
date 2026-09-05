package com.example.locationsofi

import android.content.Context
import android.location.Location
import android.location.LocationManager
import kotlin.random.Random

object SpoofManager {
    var currentLat = 28.6139   // Default Delhi
    var currentLng = 77.2090
    var isSpoofing = false
    private var preferences: android.content.SharedPreferences? = null

    // Ornstein-Uhlenbeck Process for Natural Jitter
    private var velocityLat = 0.0
    private var velocityLng = 0.0
    private val dt = 1.0
    private val theta = 0.15
    private val sigma = 0.0001

    fun init(context: Context) {
        preferences = context.getSharedPreferences("spoof_prefs", Context.MODE_PRIVATE)
        currentLat = preferences?.getFloat("last_lat", 28.6139f)?.toDouble() ?: 28.6139
        currentLng = preferences?.getFloat("last_lng", 77.2090f)?.toDouble() ?: 77.2090
        isSpoofing = preferences?.getBoolean("is_spoofing", false) ?: false
    }

    fun setLocation(lat: Double, lng: Double) {
        currentLat = lat
        currentLng = lng
        preferences?.edit()?.apply {
            putFloat("last_lat", lat.toFloat())
            putFloat("last_lng", lng.toFloat())
            apply()
        }
    }

    fun getFakeLocation(): Location {
        if (!isSpoofing) {
            return generateFakeLocation()
        }
        return generateJitteredLocation()
    }

    private fun generateFakeLocation(): Location {
        return Location(LocationManager.GPS_PROVIDER).apply {
            latitude = currentLat
            longitude = currentLng
            accuracy = 10.0f
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = android.os.SystemClock.elapsedRealtimeNanos()
        }
    }

    private fun generateJitteredLocation(): Location {
        // Ornstein-Uhlenbeck Process for GPS Jitter
        velocityLat = velocityLat - theta * velocityLat * dt + sigma * Random.nextDouble(-1.0, 1.0)
        velocityLng = velocityLng - theta * velocityLng * dt + sigma * Random.nextDouble(-1.0, 1.0)

        currentLat += velocityLat
        currentLng += velocityLng

        return Location(LocationManager.GPS_PROVIDER).apply {
            latitude = currentLat
            longitude = currentLng
            accuracy = 5.0f + Random.nextFloat() * 5.0f
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = android.os.SystemClock.elapsedRealtimeNanos()
        }
    }

    fun enableSpoofing() {
        isSpoofing = true
        preferences?.edit()?.putBoolean("is_spoofing", true)?.apply()
    }

    fun disableSpoofing() {
        isSpoofing = false
        preferences?.edit()?.putBoolean("is_spoofing", false)?.apply()
    }
}
