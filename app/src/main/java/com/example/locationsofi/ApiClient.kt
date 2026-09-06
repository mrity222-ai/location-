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

    private var _vpsServerUrl = "https://location.avedatechnologies.com"

    var vpsServerUrl: String
        get() = _vpsServerUrl
        set(value) {
            var clean = value.trim()
            if (clean.contains("127.0.0.13000") || clean.contains("localhost:127.0.0.1") || clean.contains("localhost127")) {
                clean = "http://127.0.0.1:3000"
            } else if (clean == "http://localhost:3000" || clean == "http://localhost") {
                clean = "http://127.0.0.1:3000"
            }
            if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
                clean = "http://$clean"
            }
            if (clean.endsWith("/")) {
                clean = clean.substring(0, clean.length - 1)
            }
            _vpsServerUrl = clean
        }

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

    data class AuthResponse(
        val success: Boolean,
        val message: String,
        val token: String? = null,
        val name: String? = null,
        val email: String? = null,
        val otp: String? = null,
        val error: String? = null
    )

    fun registerUser(context: Context, name: String, email: String, pass: String, callback: (AuthResponse) -> Unit) {
        val hwid = DeviceInfo.getHardwareId(context)
        thread {
            postJson("/api/user/register", JSONObject().apply {
                put("name", name)
                put("email", email)
                put("password", pass)
                put("hwid", hwid)
            }, callback)
        }
    }

    fun loginWithPassword(context: Context, email: String, pass: String, callback: (AuthResponse) -> Unit) {
        val hwid = DeviceInfo.getHardwareId(context)
        thread {
            postJson("/api/user/login-password", JSONObject().apply {
                put("email", email)
                put("password", pass)
                put("hwid", hwid)
            }, callback)
        }
    }

    fun sendOtp(context: Context, email: String, callback: (AuthResponse) -> Unit) {
        thread {
            postJson("/api/user/send-otp", JSONObject().apply {
                put("email", email)
            }, callback)
        }
    }

    fun loginWithOtp(context: Context, email: String, otp: String, callback: (AuthResponse) -> Unit) {
        val hwid = DeviceInfo.getHardwareId(context)
        thread {
            postJson("/api/user/login-otp", JSONObject().apply {
                put("email", email)
                put("otp", otp)
                put("hwid", hwid)
            }, callback)
        }
    }

    fun changePassword(context: Context, email: String, currentPass: String, newPass: String, callback: (AuthResponse) -> Unit) {
        thread {
            postJson("/api/user/change-password", JSONObject().apply {
                put("email", email)
                put("current_password", currentPass)
                put("new_password", newPass)
            }, callback)
        }
    }

    private fun postJson(endpoint: String, bodyJson: JSONObject, callback: (AuthResponse) -> Unit) {
        val candidates = listOf(
            vpsServerUrl,
            "http://127.0.0.1:3000",
            "http://10.140.65.248:3000",
            "http://10.29.29.248:3000",
            "http://10.0.2.2:3000",
            "http://localhost:3000"
        )
        val urlsToTry = candidates.distinct()

        var lastError = "Connection failed"
        for (baseUrl in urlsToTry) {
            try {
                val cleanBaseUrl = baseUrl.trimEnd('/')
                val url = URL("$cleanBaseUrl$endpoint")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.connectTimeout = 4000
                conn.readTimeout = 4000
                conn.doOutput = true

                OutputStreamWriter(conn.outputStream).use { writer ->
                    writer.write(bodyJson.toString())
                    writer.flush()
                }

                val code = conn.responseCode
                val isSuccess = code in 200..299
                val stream = if (isSuccess) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
                val json = if (text.isNotEmpty()) JSONObject(text) else JSONObject()

                if (isSuccess) {
                    vpsServerUrl = cleanBaseUrl // Update active URL on success
                    val userObj = json.optJSONObject("user")
                    val userName = userObj?.optString("name") ?: json.optString("name", "")
                    val userEmail = userObj?.optString("email") ?: json.optString("email", "")
                    callback(AuthResponse(
                        success = true,
                        message = json.optString("message", "Success"),
                        token = if (json.has("token")) json.optString("token") else null,
                        name = userName,
                        email = userEmail,
                        otp = if (json.has("otp")) json.optString("otp") else null
                    ))
                    return
                } else {
                    val errMessage = json.optString("error", "Request failed with HTTP $code")
                    callback(AuthResponse(success = false, message = errMessage, error = errMessage))
                    return
                }
            } catch (e: Exception) {
                lastError = e.message ?: "Connection error"
            }
        }
        callback(AuthResponse(success = false, message = "Unable to connect to server. Please check server or network connection.", error = lastError))
    }

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

    fun isLoggedIn(context: Context): Boolean {
        val prefs = context.getSharedPreferences("user_session", Context.MODE_PRIVATE)
        val token = prefs.getString("auth_token", null)
        return !token.isNullOrEmpty()
    }
}