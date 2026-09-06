package com.example.locationsofi

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Criteria
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

class SpoofService : Service() {

    private val CHANNEL_ID = "location_spoofer_channel"
    private var locationManager: LocationManager? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false

    private val providers = arrayOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
        "fused"
    )

    private val injectionRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            injectMockLocations()
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        SpoofManager.init(applicationContext)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        setupTestProviders()
    }

    private fun setupTestProviders() {
        val lm = locationManager ?: return
        for (provider in providers) {
            try {
                lm.addTestProvider(
                    provider,
                    false, false, false, false, true, true, true,
                    Criteria.POWER_LOW,
                    Criteria.ACCURACY_FINE
                )
                lm.setTestProviderEnabled(provider, true)
            } catch (e: Exception) {
                // Provider may already be added or mock location not granted
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LocationSpoofer Active")
            .setContentText("Virtual Location: ${SpoofManager.currentLat}, ${SpoofManager.currentLng}")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        startForeground(1, notification)
        SpoofManager.enableSpoofing()

        if (!isRunning) {
            isRunning = true
            handler.post(injectionRunnable)
        }

        return START_STICKY
    }

    private fun injectMockLocations() {
        val lm = locationManager ?: return
        val fakeLoc = SpoofManager.getFakeLocation()

        for (provider in providers) {
            try {
                val mockLoc = android.location.Location(provider).apply {
                    latitude = fakeLoc.latitude
                    longitude = fakeLoc.longitude
                    altitude = fakeLoc.altitude
                    accuracy = fakeLoc.accuracy
                    bearing = fakeLoc.bearing
                    speed = fakeLoc.speed
                    time = System.currentTimeMillis()
                    elapsedRealtimeNanos = android.os.SystemClock.elapsedRealtimeNanos()
                }
                lm.setTestProviderLocation(provider, mockLoc)
            } catch (e: Exception) {
                // Ignore failure if provider setTestProviderLocation fails temporarily
            }
        }
    }

    private fun removeTestProviders() {
        val lm = locationManager ?: return
        for (provider in providers) {
            try {
                lm.setTestProviderEnabled(provider, false)
                lm.removeTestProvider(provider)
            } catch (e: Exception) {
                // Ignore cleanup error
            }
        }
    }

    override fun onDestroy() {
        isRunning = false
        handler.removeCallbacks(injectionRunnable)
        removeTestProviders()
        SpoofManager.disableSpoofing()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Location Spoofer Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}
