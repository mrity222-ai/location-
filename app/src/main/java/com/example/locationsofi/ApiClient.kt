package com.example.locationsofi

import android.content.Context
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

object ApiClient {

    // Default VPS Server URL (Can be updated to your VPS IP/Domain)
    var vpsServerUrl = "http://localhost:3000"

    private var activeWebSocket: WebSocket? = null
    private val client = OkHttpClient()

    data class LicenseResponse(
        val hwid: String,
        val status: String, // APPROVED, PENDING, BLOCKED, EXPIRED, OFFLINE_APPROVED
        val userTag: String? = null,
        val expiryDate: String? = null,
        val remoteLat: Double? = null,
        val remoteLng: Double? = null,
        val error: String? = null
    )

    fun checkStatus(context: Context, callback: (LicenseResponse) -> Unit) {
        val hwid = DeviceInfo.getHardwareId(context)
        val userTag = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"

        thread {
            try {
                val url = URL("$vpsServerUrl/api/device/check-status")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.doOutput = true

                val jsonBody = JSONObject().apply {
                    put("hwid", hwid)
                    put("user_tag", userTag)
                }

                OutputStreamWriter(conn.outputStream).use { writer ->
                    writer.write(jsonBody.toString())
                    writer.flush()
                }

                val responseCode = conn.responseCode
                if (responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val responseText = reader.readText()
                    reader.close()

                    val json = JSONObject(responseText)
                    val response = LicenseResponse(
                        hwid = json.optString("hwid", hwid),
                        status = json.optString("status", "PENDING"),
                        userTag = json.optString("user_tag", "Device"),
                        expiryDate = if (json.isNull("expiry_date")) null else json.optString("expiry_date"),
                        remoteLat = if (json.isNull("remote_lat")) null else json.optDouble("remote_lat"),
                        remoteLng = if (json.isNull("remote_lng")) null else json.optDouble("remote_lng")
                    )
                    callback(response)
                } else {
                    callback(LicenseResponse(hwid = hwid, status = "PENDING", error = "HTTP Error $responseCode"))
                }
            } catch (e: Exception) {
                // If VPS server is unreachable, allow offline mode fallback
                callback(LicenseResponse(hwid = hwid, status = "APPROVED", error = e.message))
            }
        }
    }

    fun connectWebSocket(context: Context, onRealtimeUpdate: (JSONObject) -> Unit) {
        val hwid = DeviceInfo.getHardwareId(context)
        val wsUrl = vpsServerUrl.replace("http://", "ws://").replace("https://", "wss://") + "/ws?hwid=$hwid"

        try {
            val request = Request.Builder().url(wsUrl).build()
            activeWebSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    android.util.Log.d("ApiClient", "WebSocket Real-Time Link Established with VPS")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        val json = JSONObject(text)
                        onRealtimeUpdate(json)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    android.util.Log.e("ApiClient", "WebSocket Error: ${t.message}")
                }
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun disconnectWebSocket() {
        activeWebSocket?.close(1000, "App closed")
        activeWebSocket = null
    }
}