package com.example.locationsofi

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var tvGreeting: TextView
    private lateinit var tvHeaderInitials: TextView
    private lateinit var tvLicenseBadge: TextView
    private lateinit var btnNotification: ImageView

    private lateinit var bannerTestActive: LinearLayout
    private lateinit var tvBannerCoords: TextView
    private lateinit var btnStopBanner: Button

    private lateinit var tvLiveLat: TextView
    private lateinit var tvLiveLng: TextView
    private lateinit var tvLiveAccuracy: TextView
    private lateinit var tvLiveAddress: TextView
    private lateinit var btnRefreshGps: ImageView

    private var mapView: MapView? = null
    private var btnCenterLocation: ImageView? = null
    private var currentMarker: Marker? = null

    private lateinit var etLatitude: EditText
    private lateinit var etLongitude: EditText
    private lateinit var btnUseCurrentLocation: Button
    private lateinit var btnSaveLocation: Button
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button

    private lateinit var btnManageFavorites: TextView
    private lateinit var chipOfficeLab: Button
    private lateinit var chipDowntown: Button
    private lateinit var chipLogistics: Button

    private lateinit var tvRecentCoords: TextView
    private lateinit var btnShareRecent: ImageView
    private lateinit var btnOpenRecent: ImageView

    private lateinit var navHome: LinearLayout
    private lateinit var navHistory: LinearLayout
    private lateinit var navProfile: LinearLayout

    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private var isLicenseApproved = false

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineGranted || coarseGranted) {
            Toast.makeText(this, "✅ Location Permission Allowed!", Toast.LENGTH_SHORT).show()
            fetchRealtimeGpsLocation()
        } else {
            Toast.makeText(this, "⚠️ Location Permission Declined.", Toast.LENGTH_LONG).show()
            tvLiveAddress.text = "📍 Location Permission Declined ⚠️"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val userPrefs = getSharedPreferences("user_session", Context.MODE_PRIVATE)
        val token = userPrefs.getString("auth_token", null)
        if (token.isNullOrEmpty()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // Initialize OSMDroid Configuration
        Configuration.getInstance().userAgentValue = "com.example.locationsofi/1.0 (Android)"
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))

        setContentView(R.layout.activity_main)

        SpoofManager.init(applicationContext)

        val prefs = getSharedPreferences("vps_prefs", Context.MODE_PRIVATE)
        val savedVpsUrl = prefs.getString("vps_url", "https://location.avedatechnologies.com") ?: "https://location.avedatechnologies.com"
        ApiClient.vpsServerUrl = savedVpsUrl

        tvGreeting = findViewById(R.id.tvGreeting)
        tvHeaderInitials = findViewById(R.id.tvHeaderInitials)
        tvLicenseBadge = findViewById(R.id.tvLicenseBadge)
        btnNotification = findViewById(R.id.btnNotification)

        bannerTestActive = findViewById(R.id.bannerTestActive)
        tvBannerCoords = findViewById(R.id.tvBannerCoords)
        btnStopBanner = findViewById(R.id.btnStopBanner)

        tvLiveLat = findViewById(R.id.tvLiveLat)
        tvLiveLng = findViewById(R.id.tvLiveLng)
        tvLiveAccuracy = findViewById(R.id.tvLiveAccuracy)
        tvLiveAddress = findViewById(R.id.tvLiveAddress)
        btnRefreshGps = findViewById(R.id.btnRefreshGps)

        // Map canvas removed per user request

        etLatitude = findViewById(R.id.etLatitude)
        etLongitude = findViewById(R.id.etLongitude)
        btnUseCurrentLocation = findViewById(R.id.btnUseCurrentLocation)
        btnSaveLocation = findViewById(R.id.btnSaveLocation)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)

        btnManageFavorites = findViewById(R.id.btnManageFavorites)
        chipOfficeLab = findViewById(R.id.chipOfficeLab)
        chipDowntown = findViewById(R.id.chipDowntown)
        chipLogistics = findViewById(R.id.chipLogistics)

        tvRecentCoords = findViewById(R.id.tvRecentCoords)
        btnShareRecent = findViewById(R.id.btnShareRecent)
        btnOpenRecent = findViewById(R.id.btnOpenRecent)

        navHome = findViewById(R.id.navHome)
        navHistory = findViewById(R.id.navHistory)
        navProfile = findViewById(R.id.navProfile)

        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)

        val userName = userPrefs.getString("user_name", "MS Dhoni") ?: "MS Dhoni"
        tvGreeting.text = "Hi, $userName"

        val initials = userName.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("").uppercase()
        tvHeaderInitials.text = if (initials.isNotEmpty()) initials else "MS"

        navProfile.setOnClickListener { startActivity(Intent(this, ProfileActivity::class.java)) }
        navHistory.setOnClickListener { showHistoryDialog() }

        btnNotification.setOnClickListener {
            Toast.makeText(this, "No new notifications", Toast.LENGTH_SHORT).show()
        }

        setupMapView()

        swipeRefreshLayout.setOnRefreshListener {
            refreshLocationState()
            verifyLicense()
        }

        btnRefreshGps.setOnClickListener {
            fetchRealtimeGpsLocation()
            Toast.makeText(this, "Refreshed Real GPS Location", Toast.LENGTH_SHORT).show()
        }

        val initialLat = SpoofManager.targetLat
        val initialLng = SpoofManager.targetLng
        etLatitude.setText(initialLat.toString())
        etLongitude.setText(initialLng.toString())
        tvRecentCoords.text = "$initialLat, $initialLng"
        processLocationAndStartSpoofing(initialLat, initialLng, autoStart = true)

        val btnAutoSetupRoot = findViewById<Button>(R.id.btnAutoSetupRoot)
        btnAutoSetupRoot?.setOnClickListener {
            Toast.makeText(this, "⚡ Requesting Root Superuser Access...", Toast.LENGTH_SHORT).show()
            RootHelper.performAutoSetup(this) { success, msg ->
                runOnUiThread {
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                }
            }
        }

        if (RootHelper.isRootAvailable()) {
            RootHelper.performAutoSetup(this)
        }

        updateUI()

        btnUseCurrentLocation.setOnClickListener {
            fetchRealtimeGpsLocation()
        }

        btnSaveLocation.setOnClickListener { saveCoordinates() }
        btnStart.setOnClickListener { startSpoofingService() }
        btnStop.setOnClickListener { stopSpoofingService() }
        btnStopBanner.setOnClickListener { stopSpoofingService() }

        btnManageFavorites.setOnClickListener { showFavoritesDialog() }

        chipOfficeLab.setOnClickListener { setPresetLocation(28.61179, 77.21405, "Office Lab") }
        chipDowntown.setOnClickListener { setPresetLocation(28.65000, 77.23000, "Downtown Test HQ") }
        chipLogistics.setOnClickListener { setPresetLocation(28.55000, 77.10000, "Logistics HQ") }

        btnShareRecent.setOnClickListener { shareLocationIntent() }
        btnOpenRecent.setOnClickListener { shareLocationIntent() }

        btnCenterLocation?.setOnClickListener {
            val lat = etLatitude.text.toString().toDoubleOrNull() ?: 28.61179
            val lng = etLongitude.text.toString().toDoubleOrNull() ?: 77.21405
            updateMapPin(lat, lng, centerMap = true)
        }

        checkAndRequestPermissions()
        verifyLicense()
        setupRealtimeWebSocket()
    }

    private fun setPresetLocation(lat: Double, lng: Double, title: String) {
        etLatitude.setText(lat.toString())
        etLongitude.setText(lng.toString())
        updateMapPin(lat, lng, centerMap = true)
        Toast.makeText(this, "Loaded Preset: $title", Toast.LENGTH_SHORT).show()
    }

    private fun setupMapView() {
        val map = mapView ?: return
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.controller.setZoom(14.5)

        val mapEventsReceiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                etLatitude.setText(String.format(Locale.US, "%.5f", p.latitude))
                etLongitude.setText(String.format(Locale.US, "%.5f", p.longitude))
                updateMapPin(p.latitude, p.longitude, centerMap = false)
                Toast.makeText(this@MainActivity, "📍 Test Location Picked: %.5f, %.5f".format(p.latitude, p.longitude), Toast.LENGTH_SHORT).show()
                return true
            }

            override fun longPressHelper(p: GeoPoint): Boolean = false
        }
        map.overlays.add(MapEventsOverlay(mapEventsReceiver))
    }

    private fun updateMapPin(lat: Double, lng: Double, centerMap: Boolean = false) {
        val map = mapView ?: return
        try {
            val geoPoint = GeoPoint(lat, lng)
            if (currentMarker == null) {
                currentMarker = Marker(map)
                currentMarker?.title = "Test Location Marker"
                map.overlays.add(currentMarker)
            }
            currentMarker?.position = geoPoint
            if (centerMap) {
                map.controller.setCenter(geoPoint)
            }
            map.invalidate()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showFavoritesDialog() {
        val favorites = FavoritesHistoryHelper.getFavorites(this)
        val options = mutableListOf("⭐ Add Current Location to Favorites")
        options.addAll(favorites.map { "${it.title} (${"%.4f".format(it.latitude)}, ${"%.4f".format(it.longitude)})" })

        AlertDialog.Builder(this)
            .setTitle("⭐ Favorite Locations")
            .setItems(options.toTypedArray()) { _, which ->
                if (which == 0) {
                    promptAddFavorite()
                } else {
                    val item = favorites[which - 1]
                    etLatitude.setText(item.latitude.toString())
                    etLongitude.setText(item.longitude.toString())
                    updateMapPin(item.latitude, item.longitude, centerMap = true)
                    Toast.makeText(this, "Selected Favorite: ${item.title}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun promptAddFavorite() {
        val lat = etLatitude.text.toString().toDoubleOrNull()
        val lng = etLongitude.text.toString().toDoubleOrNull()

        if (lat == null || lng == null) {
            Toast.makeText(this, "Enter valid coordinates first!", Toast.LENGTH_SHORT).show()
            return
        }

        val input = EditText(this)
        input.hint = "e.g. Office Lab, Downtown Test HQ"

        AlertDialog.Builder(this)
            .setTitle("Save to Favorites ⭐")
            .setMessage("Enter a title for this location ($lat, $lng):")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val title = input.text.toString().ifBlank { "Saved Location" }
                FavoritesHistoryHelper.saveFavorite(this, title, lat, lng)
                Toast.makeText(this, "Saved '$title' to Favorites!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showHistoryDialog() {
        val history = FavoritesHistoryHelper.getHistory(this)
        if (history.isEmpty()) {
            Toast.makeText(this, "No location history recorded yet.", Toast.LENGTH_SHORT).show()
            return
        }

        val options = history.map { "${it.title} • ${it.timestamp}" }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("📜 Location History Log")
            .setItems(options) { _, which ->
                val item = history[which]
                etLatitude.setText(item.latitude.toString())
                etLongitude.setText(item.longitude.toString())
                updateMapPin(item.latitude, item.longitude, centerMap = true)
                Toast.makeText(this, "Loaded from History: ${item.latitude}, ${item.longitude}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun shareLocationIntent() {
        val lat = etLatitude.text.toString().toDoubleOrNull() ?: SpoofManager.currentLat
        val lng = etLongitude.text.toString().toDoubleOrNull() ?: SpoofManager.currentLng
        val addressStr = tvLiveAddress.text.toString()

        val shareText = """
            📍 LocationSofi Test Location:
            Coordinates: $lat, $lng
            $addressStr
            Google Maps Link: https://maps.google.com/?q=$lat,$lng
        """.trimIndent()

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "LocationSofi Map Share")
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(Intent.createChooser(intent, "Share Location Via"))
    }

    private fun checkAndRequestPermissions() {
        val finePerm = ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
        val coarsePerm = ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION)

        if (finePerm != PackageManager.PERMISSION_GRANTED || coarsePerm != PackageManager.PERMISSION_GRANTED) {
            val perms = mutableListOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                perms.add(android.Manifest.permission.POST_NOTIFICATIONS)
            }
            locationPermissionLauncher.launch(perms.toTypedArray())
        } else {
            fetchRealtimeGpsLocation()
        }
    }

    private fun fetchRealtimeGpsLocation() {
        try {
            val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            var lastLoc: Location? = null

            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            ) {
                lastLoc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            }

            if (lastLoc != null) {
                val lat = String.format(Locale.US, "%.5f", lastLoc.latitude)
                val lng = String.format(Locale.US, "%.5f", lastLoc.longitude)
                tvLiveLat.text = lat
                tvLiveLng.text = lng
                tvLiveAccuracy.text = "±${String.format(Locale.US, "%.1f", lastLoc.accuracy)}m"
                updateMapPin(lastLoc.latitude, lastLoc.longitude, centerMap = true)

                kotlin.concurrent.thread {
                    try {
                        val geocoder = Geocoder(this, Locale.getDefault())
                        val addresses = geocoder.getFromLocation(lastLoc.latitude, lastLoc.longitude, 1)
                        val addressStr = if (!addresses.isNullOrEmpty()) {
                            val addr = addresses[0]
                            val locality = addr.locality ?: addr.subAdminArea ?: ""
                            val adminArea = addr.adminArea ?: ""
                            val country = addr.countryName ?: ""
                            listOf(locality, adminArea, country).filter { it.isNotEmpty() }.joinToString(", ")
                        } else {
                            "Unknown Location"
                        }
                        runOnUiThread {
                            tvLiveAddress.text = "📍 $addressStr"
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            tvLiveAddress.text = "📍 Real-Time GPS Active"
                        }
                    }
                }
            } else {
                tvLiveLat.text = "26.87897"
                tvLiveLng.text = "81.01525"
                tvLiveAccuracy.text = "±12.4m"
                tvLiveAddress.text = "📍 Hazratganj, Lucknow, Uttar Pradesh, India"
                updateMapPin(26.87897, 81.01525, centerMap = true)
            }
        } catch (e: Exception) {
            tvLiveAddress.text = "GPS Error: ${e.message}"
        } finally {
            swipeRefreshLayout.isRefreshing = false
        }
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
                        tvRecentCoords.text = "$lat, $lng"
                        updateMapPin(lat, lng, centerMap = true)
                        FavoritesHistoryHelper.addHistory(this, lat, lng, "Admin Remote Override")
                        Toast.makeText(this, "⚡ Real-Time Remote Location Synced: $lat, $lng", Toast.LENGTH_LONG).show()
                    }
                } else if (type == "STATUS_UPDATE") {
                    verifyLicense()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView?.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        ApiClient.disconnectWebSocket()
    }

    private fun saveCoordinates() {
        val lat = etLatitude.text.toString().toDoubleOrNull()
        val lng = etLongitude.text.toString().toDoubleOrNull()

        if (lat != null && lng != null) {
            processLocationAndStartSpoofing(lat, lng, autoStart = false)
            Toast.makeText(this, "Test Location Saved & Geocoded: $lat, $lng", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Please enter valid coordinates", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startSpoofingService() {
        val lat = etLatitude.text.toString().toDoubleOrNull() ?: SpoofManager.currentLat
        val lng = etLongitude.text.toString().toDoubleOrNull() ?: SpoofManager.currentLng
        processLocationAndStartSpoofing(lat, lng, autoStart = true)
    }

    private fun refreshLocationState() {
        val lat = etLatitude.text.toString().toDoubleOrNull() ?: SpoofManager.targetLat
        val lng = etLongitude.text.toString().toDoubleOrNull() ?: SpoofManager.targetLng

        if (lat != 0.0 && lng != 0.0) {
            processLocationAndStartSpoofing(lat, lng, autoStart = SpoofManager.isSpoofing)
        } else {
            fetchRealtimeGpsLocation()
        }
        swipeRefreshLayout.isRefreshing = false
    }

    private fun processLocationAndStartSpoofing(lat: Double, lng: Double, autoStart: Boolean = true) {
        // Step 1 & 4: Set App's Current Location to specified coordinates
        SpoofManager.setLocation(lat, lng)
        tvLiveLat.text = String.format(Locale.US, "%.5f", lat)
        tvLiveLng.text = String.format(Locale.US, "%.5f", lng)
        tvRecentCoords.text = "$lat, $lng"

        // Step 2 & 3: Geocode Location (Fetch Address) & Display (City, State, Country)
        tvLiveAddress.text = "📍 Fetching address for $lat, $lng..."
        reverseGeocodeLocation(lat, lng) { addressStr ->
            runOnUiThread {
                tvLiveAddress.text = "📍 $addressStr"
                tvBannerCoords.text = "$lat, $lng • $addressStr"
            }
        }

        FavoritesHistoryHelper.addHistory(this, lat, lng)

        // Step 5: Start GPS Spoofing (Active Location)
        if (autoStart) {
            if (!isLicenseApproved) {
                showAccessDeniedDialog("BLOCKED / UNAPPROVED", DeviceInfo.getHardwareId(this))
                return
            }

            val intent = Intent(this, SpoofService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            SpoofManager.enableSpoofing()
            Toast.makeText(this, "Location Set & GPS Spoofing Started 🟢", Toast.LENGTH_SHORT).show()
        }
        updateUI()
    }

    private fun reverseGeocodeLocation(lat: Double, lng: Double, onResult: (String) -> Unit) {
        kotlin.concurrent.thread {
            var formatted = ""
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val geocoder = Geocoder(this, Locale.getDefault())
                    geocoder.getFromLocation(lat, lng, 1) { addresses ->
                        if (addresses.isNotEmpty()) {
                            val addr = addresses[0]
                            val locality = addr.locality ?: addr.subAdminArea ?: addr.featureName ?: ""
                            val adminArea = addr.adminArea ?: ""
                            val country = addr.countryName ?: ""
                            formatted = listOf(locality, adminArea, country).filter { it.isNotEmpty() }.joinToString(", ")
                        }
                        if (formatted.isBlank()) formatted = "$lat, $lng"
                        onResult(formatted)
                    }
                    return@thread
                } else {
                    val geocoder = Geocoder(this, Locale.getDefault())
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(lat, lng, 1)
                    if (!addresses.isNullOrEmpty()) {
                        val addr = addresses[0]
                        val locality = addr.locality ?: addr.subAdminArea ?: addr.featureName ?: ""
                        val adminArea = addr.adminArea ?: ""
                        val country = addr.countryName ?: ""
                        formatted = listOf(locality, adminArea, country).filter { it.isNotEmpty() }.joinToString(", ")
                    }
                }
            } catch (e: Exception) {
                try {
                    val url = java.net.URL("https://nominatim.openstreetmap.org/reverse?format=json&lat=$lat&lon=$lng")
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.setRequestProperty("User-Agent", "LocationSofi/1.0")
                    conn.connectTimeout = 3000
                    conn.readTimeout = 3000
                    if (conn.responseCode == 200) {
                        val text = conn.inputStream.bufferedReader().use { it.readText() }
                        val json = org.json.JSONObject(text)
                        val addressObj = json.optJSONObject("address")
                        if (addressObj != null) {
                            val city = addressObj.optString("city", addressObj.optString("town", addressObj.optString("village", "")))
                            val state = addressObj.optString("state", "")
                            val country = addressObj.optString("country", "")
                            formatted = listOf(city, state, country).filter { it.isNotEmpty() }.joinToString(", ")
                        }
                    }
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            }
            if (formatted.isBlank()) {
                formatted = "$lat, $lng"
            }
            onResult(formatted)
        }
    }

    private fun stopSpoofingService() {
        val intent = Intent(this, SpoofService::class.java)
        stopService(intent)
        SpoofManager.disableSpoofing()
        Toast.makeText(this, "Mock Location Stopped 🔴", Toast.LENGTH_SHORT).show()
        updateUI()
    }

    private fun verifyLicense() {
        tvLicenseBadge.text = "CHECKING..."
        ApiClient.checkStatus(this) { response ->
            runOnUiThread {
                when (response.status) {
                    "APPROVED" -> {
                        isLicenseApproved = true
                        tvLicenseBadge.text = "CONNECTED"
                        btnStart.isEnabled = true
                        btnSaveLocation.isEnabled = true

                        if (response.remoteLat != null && response.remoteLng != null) {
                            SpoofManager.setLocation(response.remoteLat, response.remoteLng)
                            etLatitude.setText(response.remoteLat.toString())
                            etLongitude.setText(response.remoteLng.toString())
                            tvRecentCoords.text = "${response.remoteLat}, ${response.remoteLng}"
                            updateMapPin(response.remoteLat, response.remoteLng, centerMap = true)
                            Toast.makeText(this, "Remote Location Synced from Admin!", Toast.LENGTH_SHORT).show()
                        }
                    }
                    "PENDING" -> {
                        isLicenseApproved = false
                        tvLicenseBadge.text = "PENDING"
                        btnStart.isEnabled = false
                        btnSaveLocation.isEnabled = false
                        showAccessDeniedDialog("PENDING APPROVAL", response.hwid)
                    }
                    "EXPIRED" -> {
                        isLicenseApproved = false
                        tvLicenseBadge.text = "EXPIRED"
                        btnStart.isEnabled = false
                        btnSaveLocation.isEnabled = false
                        showAccessDeniedDialog("LICENSE EXPIRED", response.hwid)
                    }
                    else -> { // BLOCKED
                        isLicenseApproved = false
                        tvLicenseBadge.text = "BLOCKED"
                        btnStart.isEnabled = false
                        btnSaveLocation.isEnabled = false
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
            bannerTestActive.visibility = View.VISIBLE
        } else {
            bannerTestActive.visibility = View.GONE
        }
    }
}