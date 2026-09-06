package com.example.locationsofi

import android.content.Context
import android.location.Location
import android.location.LocationManager
import kotlin.random.Random

object SpoofManager {
    var targetLat = 28.6139   // Default target location
    var targetLng = 77.2090
    var currentLat = 28.6139
    var currentLng = 77.2090
    var isSpoofing = false
    private var preferences: android.content.SharedPreferences? = null

    // Ornstein-Uhlenbeck Process for Natural Jitter
    private var velocityLat = 0.0
    private var velocityLng = 0.0
    private val dt = 1.0
    private val theta = 0.15
    private val sigma = 0.00003

    fun init(context: Context) {
        preferences = context.getSharedPreferences("spoof_prefs", Context.MODE_PRIVATE)
        val latStr = preferences?.getString("last_lat_str", null)
        val lngStr = preferences?.getString("last_lng_str", null)

        if (latStr != null && lngStr != null) {
            targetLat = latStr.toDoubleOrNull() ?: 28.6139
            targetLng = lngStr.toDoubleOrNull() ?: 77.2090
        } else {
            val oldLat = preferences?.getFloat("last_lat", 28.6139f)?.toDouble() ?: 28.6139
            val oldLng = preferences?.getFloat("last_lng", 77.2090f)?.toDouble() ?: 77.2090
            targetLat = oldLat
            targetLng = oldLng
        }

        currentLat = targetLat
        currentLng = targetLng
        isSpoofing = preferences?.getBoolean("is_spoofing", false) ?: false
    }

    fun setLocation(lat: Double, lng: Double) {
        targetLat = lat
        targetLng = lng
        currentLat = lat
        currentLng = lng
        velocityLat = 0.0
        velocityLng = 0.0
        preferences?.edit()?.apply {
            putString("last_lat_str", lat.toString())
            putString("last_lng_str", lng.toString())
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
            latitude = targetLat
            longitude = targetLng
            accuracy = 10.0f
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = android.os.SystemClock.elapsedRealtimeNanos()
        }
    }

    private fun generateJitteredLocation(): Location {
        velocityLat = velocityLat - theta * velocityLat * dt + sigma * Random.nextDouble(-1.0, 1.0)
        velocityLng = velocityLng - theta * velocityLng * dt + sigma * Random.nextDouble(-1.0, 1.0)

        currentLat = targetLat + velocityLat
        currentLng = targetLng + velocityLng

        return Location(LocationManager.GPS_PROVIDER).apply {
            latitude = currentLat
            longitude = currentLng
            accuracy = 5.0f + Random.nextFloat() * 3.0f
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
