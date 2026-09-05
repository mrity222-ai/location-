package com.example.locationsofi

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings

object DeviceInfo {

    @SuppressLint("HardwareIds")
    fun getHardwareId(context: Context): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        return if (!androidId.isNullOrEmpty()) {
            "DEV-${androidId.uppercase()}"
        } else {
            "DEV-UNKNOWN-${android.os.Build.MODEL.replace(" ", "_")}"
        }
    }
}