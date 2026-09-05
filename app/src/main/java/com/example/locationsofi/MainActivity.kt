package com.example.locationsofi

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial

class MainActivity : AppCompatActivity() {

    private lateinit var etLatitude: EditText
    private lateinit var etLongitude: EditText
    private lateinit var btnSaveLocation: Button
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var btnSaveQuick: Button
    private lateinit var btnRefresh: Button
    private lateinit var btnHistory: Button
    private lateinit var btnAbout: Button
    private lateinit var switchSpoofing: SwitchMaterial
    private lateinit var tvStatus: TextView
    private lateinit var tvLicenseBadge: TextView
    private lateinit var tvHwid: TextView

    private var isLicenseApproved = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        SpoofManager.init(applicationContext)

        // Internal VPS Server URL setup (Hidden from UI)
        val prefs = getSharedPreferences("vps_prefs", Context.MODE_PRIVATE)
        val savedVpsUrl = prefs.getString("vps_url", "http://localhost:3000") ?: "http://localhost:3000"
        ApiClient.vpsServerUrl = savedVpsUrl

        etLatitude = findViewById(R.id.etLatitude)
        etLongitude = findViewById(R.id.etLongitude)
        btnSaveLocation = findViewById(R.id.btnSaveLocation)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        btnSaveQuick = findViewById(R.id.btnSaveQuick)
        btnRefresh = findViewById(R.id.btnRefresh)
        btnHistory = findViewById(R.id.btnHistory)
        btnAbout = findViewById(R.id.btnAbout)
        switchSpoofing = findViewById(R.id.switchSpoofing)
        tvStatus = findViewById(R.id.tvStatus)
        tvLicenseBadge = findViewById(R.id.tvLicenseBadge)
        tvHwid = findViewById(R.id.tvHwid)

        etLatitude.setText(SpoofManager.currentLat.toString())
        etLongitude.setText(SpoofManager.currentLng.toString())

        val hwid = DeviceInfo.getHardwareId(this)
        tvHwid.text = "DEV ID: $hwid 📋"

        tvHwid.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("HWID", hwid)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Hardware ID copied to clipboard!", Toast.LENGTH_SHORT).show()
        }

        updateUI()

        btnSaveLocation.setOnClickListener { saveCoordinates() }
        btnSaveQuick.setOnClickListener { saveCoordinates() }

        btnStart.setOnClickListener { startSpoofingService() }
        btnStop.setOnClickListener { stopSpoofingService() }

        switchSpoofing.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !SpoofManager.isSpoofing) {
                startSpoofingService()
            } else if (!isChecked && SpoofManager.isSpoofing) {
                stopSpoofingService()
            }
        }

        btnRefresh.setOnClickListener {
            verifyLicense()
            Toast.makeText(this, "Status Refreshed", Toast.LENGTH_SHORT).show()
        }

        btnHistory.setOnClickListener {
            Toast.makeText(this, "Location History: Default (Delhi)", Toast.LENGTH_SHORT).show()
        }

        btnAbout.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("LocationSpoofer v2.4.1")
                .setMessage("High-Security Root + LSPosed Virtual Location & Hardware Anti-Detection Engine.\n\nVPS Server Status: Protected & Encrypted")
                .setPositiveButton("OK", null)
                .show()
        }

        tvLicenseBadge.setOnClickListener {
            showVpsUrlDialog()
        }

        verifyLicense()
        setupRealtimeWebSocket()
    }

    private fun showVpsUrlDialog() {
        val input = EditText(this)
        input.setText(ApiClient.vpsServerUrl)
        input.setPadding(40, 30, 40, 30)

        AlertDialog.Builder(this)
            .setTitle("🌐 Set VPS Server URL")
            .setMessage("Enter your Server IP/Domain (e.g. http://192.168.1.10:3000 or https://api.yourdomain.com):")
            .setView(input)
            .setPositiveButton("Save & Reconnect") { dialog, _ ->
                val newUrl = input.text.toString().trim()
                if (newUrl.isNotEmpty()) {
                    ApiClient.vpsServerUrl = newUrl
                    val prefs = getSharedPreferences("vps_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putString("vps_url", newUrl).apply()
                    Toast.makeText(this, "VPS URL Updated: $newUrl", Toast.LENGTH_SHORT).show()
                    ApiClient.disconnectWebSocket()
                    setupRealtimeWebSocket()
                    verifyLicense()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun setupRealtimeWebSocket() {
        ApiClient.connectWebSocket(this) { json ->
            runOnUiThread {
                val type = json.optString("type")
                if (type == "LOCATION_OVERRIDE") {
                    val payload = json.optJSONObject("payload")
                    if (payload != null) {
                        val lat = payload.optDouble("remote_lat")
                        val lng = payload.optDouble("remote_lng")
                        SpoofManager.setLocation(lat, lng)
                        etLatitude.setText(lat.toString())
                        etLongitude.setText(lng.toString())
                        Toast.makeText(this, "⚡ Instant Real-Time Location Override: $lat, $lng", Toast.LENGTH_LONG).show()
                    }
                } else if (type == "STATUS_UPDATE") {
                    verifyLicense()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ApiClient.disconnectWebSocket()
    }

    private fun saveCoordinates() {
        if (!isLicenseApproved) {
            showAccessDeniedDialog("BLOCKED / UNAPPROVED", DeviceInfo.getHardwareId(this))
            return
        }
        val lat = etLatitude.text.toString().toDoubleOrNull()
        val lng = etLongitude.text.toString().toDoubleOrNull()

        if (lat != null && lng != null) {
            SpoofManager.setLocation(lat, lng)
            Toast.makeText(this, "Location Saved: $lat, $lng", Toast.LENGTH_SHORT).show()
            updateUI()
        } else {
            Toast.makeText(this, "Please enter valid coordinates", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startSpoofingService() {
        if (!isLicenseApproved) {
            showAccessDeniedDialog("BLOCKED / UNAPPROVED", DeviceInfo.getHardwareId(this))
            switchSpoofing.isChecked = false
            return
        }

        val intent = Intent(this, SpoofService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        SpoofManager.enableSpoofing()
        Toast.makeText(this, "Location Spoofing Started 🟢", Toast.LENGTH_SHORT).show()
        updateUI()
    }

    private fun stopSpoofingService() {
        val intent = Intent(this, SpoofService::class.java)
        stopService(intent)
        SpoofManager.disableSpoofing()
        Toast.makeText(this, "Location Spoofing Stopped 🔴", Toast.LENGTH_SHORT).show()
        updateUI()
    }

    private fun verifyLicense() {
        tvLicenseBadge.text = "⏳ CHECKING"
        ApiClient.checkStatus(this) { response ->
            runOnUiThread {
                when (response.status) {
                    "APPROVED" -> {
                        isLicenseApproved = true
                        tvLicenseBadge.text = "🟢 CONNECTED"
                        btnStart.isEnabled = true
                        btnSaveLocation.isEnabled = true
                        btnSaveQuick.isEnabled = true

                        if (response.remoteLat != null && response.remoteLng != null) {
                            SpoofManager.setLocation(response.remoteLat, response.remoteLng)
                            etLatitude.setText(response.remoteLat.toString())
                            etLongitude.setText(response.remoteLng.toString())
                            Toast.makeText(this, "Remote Location Synced from Admin!", Toast.LENGTH_SHORT).show()
                        }
                    }
                    "PENDING" -> {
                        isLicenseApproved = false
                        tvLicenseBadge.text = "⏳ PENDING"
                        btnStart.isEnabled = false
                        btnSaveLocation.isEnabled = false
                        btnSaveQuick.isEnabled = false
                        showAccessDeniedDialog("PENDING APPROVAL", response.hwid)
                    }
                    "EXPIRED" -> {
                        isLicenseApproved = false
                        tvLicenseBadge.text = "⚠️ EXPIRED"
                        btnStart.isEnabled = false
                        btnSaveLocation.isEnabled = false
                        btnSaveQuick.isEnabled = false
                        showAccessDeniedDialog("LICENSE EXPIRED", response.hwid)
                    }
                    else -> { // BLOCKED
                        isLicenseApproved = false
                        tvLicenseBadge.text = "🔴 BLOCKED"
                        btnStart.isEnabled = false
                        btnSaveLocation.isEnabled = false
                        btnSaveQuick.isEnabled = false
                        showAccessDeniedDialog("BLOCKED BY ADMIN", response.hwid)
                    }
                }
                updateUI()
            }
        }
    }

    private fun showAccessDeniedDialog(statusReason: String, hwid: String) {
        AlertDialog.Builder(this)
            .setTitle("🛑 Access Restricted")
            .setMessage("Your device license status is: $statusReason\n\nHardware ID:\n$hwid\n\nPlease contact the VPS Admin to approve or extend your access license.")
            .setPositiveButton("Retry License Check") { dialog, _ ->
                dialog.dismiss()
                verifyLicense()
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun updateUI() {
        if (SpoofManager.isSpoofing) {
            tvStatus.text = "🟢 ACTIVE"
            tvStatus.setTextColor(resources.getColor(R.color.connected_text, theme))
            switchSpoofing.isChecked = true
        } else {
            tvStatus.text = "🔴 INACTIVE"
            tvStatus.setTextColor(resources.getColor(R.color.red_stop, theme))
            switchSpoofing.isChecked = false
        }
    }
}