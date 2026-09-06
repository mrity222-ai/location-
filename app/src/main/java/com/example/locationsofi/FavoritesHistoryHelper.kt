package com.example.locationsofi

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LocationItem(
    val id: String,
    val title: String,
    val latitude: Double,
    val longitude: Double,
    val address: String = "",
    val timestamp: String = ""
)

object FavoritesHistoryHelper {
    private const val PREFS_NAME = "location_saved_data"
    private const val KEY_FAVORITES = "key_favorites_list"
    private const val KEY_HISTORY = "key_history_list"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // --- FAVORITES ---
    fun getFavorites(context: Context): List<LocationItem> {
        val jsonStr = getPrefs(context).getString(KEY_FAVORITES, "[]") ?: "[]"
        val list = mutableListOf<LocationItem>()
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    LocationItem(
                        id = obj.optString("id", System.currentTimeMillis().toString()),
                        title = obj.optString("title", "Favorite"),
                        latitude = obj.optDouble("latitude", 0.0),
                        longitude = obj.optDouble("longitude", 0.0),
                        address = obj.optString("address", ""),
                        timestamp = obj.optString("timestamp", "")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun saveFavorite(context: Context, title: String, lat: Double, lng: Double, address: String = "") {
        val currentList = getFavorites(context).toMutableList()
        val timestamp = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())
        val newItem = LocationItem(
            id = System.currentTimeMillis().toString(),
            title = if (title.isBlank()) "Saved Location" else title,
            latitude = lat,
            longitude = lng,
            address = address,
            timestamp = timestamp
        )
        currentList.add(0, newItem) // Add to top

        val jsonArray = JSONArray()
        for (item in currentList) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("latitude", item.latitude)
                put("longitude", item.longitude)
                put("address", item.address)
                put("timestamp", item.timestamp)
            }
            jsonArray.put(obj)
        }

        getPrefs(context).edit().putString(KEY_FAVORITES, jsonArray.toString()).apply()
    }

    fun deleteFavorite(context: Context, id: String) {
        val updated = getFavorites(context).filter { it.id != id }
        val jsonArray = JSONArray()
        for (item in updated) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("latitude", item.latitude)
                put("longitude", item.longitude)
                put("address", item.address)
                put("timestamp", item.timestamp)
            }
            jsonArray.put(obj)
        }
        getPrefs(context).edit().putString(KEY_FAVORITES, jsonArray.toString()).apply()
    }

    // --- HISTORY ---
    fun getHistory(context: Context): List<LocationItem> {
        val jsonStr = getPrefs(context).getString(KEY_HISTORY, "[]") ?: "[]"
        val list = mutableListOf<LocationItem>()
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    LocationItem(
                        id = obj.optString("id", System.currentTimeMillis().toString()),
                        title = obj.optString("title", "History"),
                        latitude = obj.optDouble("latitude", 0.0),
                        longitude = obj.optDouble("longitude", 0.0),
                        address = obj.optString("address", ""),
                        timestamp = obj.optString("timestamp", "")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun addHistory(context: Context, lat: Double, lng: Double, address: String = "") {
        val currentList = getHistory(context).toMutableList()
        val timestamp = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date())
        val titleStr = if (address.isNotBlank()) address else "Lat: %.4f, Lng: %.4f".format(lat, lng)

        val newItem = LocationItem(
            id = System.currentTimeMillis().toString(),
            title = titleStr,
            latitude = lat,
            longitude = lng,
            address = address,
            timestamp = timestamp
        )

        // Remove duplicate if same coords
        currentList.removeAll { it.latitude == lat && it.longitude == lng }
        currentList.add(0, newItem)

        // Keep max 20 entries
        val trimmedList = if (currentList.size > 20) currentList.subList(0, 20) else currentList

        val jsonArray = JSONArray()
        for (item in trimmedList) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("latitude", item.latitude)
                put("longitude", item.longitude)
                put("address", item.address)
                put("timestamp", item.timestamp)
            }
            jsonArray.put(obj)
        }

        getPrefs(context).edit().putString(KEY_HISTORY, jsonArray.toString()).apply()
    }
}
