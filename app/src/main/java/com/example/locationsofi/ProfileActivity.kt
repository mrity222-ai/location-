package com.example.locationsofi

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog

class ProfileActivity : AppCompatActivity() {

    private lateinit var btnBackProfile: ImageView
    private lateinit var btnNotificationProfile: ImageView
    private lateinit var tvAvatarInitials: TextView
    private lateinit var tvProfileName: TextView
    private lateinit var tvProfileEmail: TextView
    private lateinit var tvProfileMaskedHwid: TextView
    private lateinit var btnCopyHwid: ImageView
    private lateinit var tvDeviceNameProfile: TextView
    private lateinit var btnOpenChangePassword: Button
    private lateinit var btnProfileLogout: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        btnBackProfile = findViewById(R.id.btnBackProfile)
        btnNotificationProfile = findViewById(R.id.btnNotificationProfile)
        tvAvatarInitials = findViewById(R.id.tvAvatarInitials)
        tvProfileName = findViewById(R.id.tvProfileName)
        tvProfileEmail = findViewById(R.id.tvProfileEmail)
        tvProfileMaskedHwid = findViewById(R.id.tvProfileMaskedHwid)
        btnCopyHwid = findViewById(R.id.btnCopyHwid)
        tvDeviceNameProfile = findViewById(R.id.tvDeviceNameProfile)
        btnOpenChangePassword = findViewById(R.id.btnOpenChangePassword)
        btnProfileLogout = findViewById(R.id.btnProfileLogout)

        btnBackProfile.setOnClickListener { finish() }
        btnNotificationProfile.setOnClickListener { Toast.makeText(this, "No new notifications", Toast.LENGTH_SHORT).show() }

        val userPrefs = getSharedPreferences("user_session", Context.MODE_PRIVATE)
        val name = userPrefs.getString("user_name", "MS Dhoni") ?: "MS Dhoni"
        val email = userPrefs.getString("user_email", "dhoniy43@gmail.com") ?: "dhoniy43@gmail.com"

        tvProfileName.text = name
        tvProfileEmail.text = email

        val initials = name.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("").uppercase()
        tvAvatarInitials.text = if (initials.isNotEmpty()) initials else "MS"

        val rawHwid = DeviceInfo.getHardwareId(this)
        val maskedHwid = if (rawHwid.length > 8) "DEV- . . . ${rawHwid.takeLast(4)}" else rawHwid
        tvProfileMaskedHwid.text = maskedHwid
        tvDeviceNameProfile.text = Build.MODEL ?: "SM-G965F"

        btnCopyHwid.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("HWID", rawHwid)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Hardware ID copied!", Toast.LENGTH_SHORT).show()
        }

        btnOpenChangePassword.setOnClickListener {
            showChangePasswordBottomSheet(email)
        }

        btnProfileLogout.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Logout Session 🚪")
                .setMessage("Are you sure you want to logout? Active Developer Test Mode will be stopped.")
                .setPositiveButton("Logout Session") { _, _ ->
                    // Stop service
                    val intent = Intent(this, SpoofService::class.java)
                    stopService(intent)
                    SpoofManager.disableSpoofing()

                    userPrefs.edit().clear().apply()
                    Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()

                    val loginIntent = Intent(this, LoginActivity::class.java)
                    loginIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(loginIntent)
                    finish()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun showChangePasswordBottomSheet(email: String) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.layout_change_password_sheet, null)
        dialog.setContentView(view)

        val etCurrentPasswordSheet: EditText = view.findViewById(R.id.etCurrentPasswordSheet)
        val etNewPasswordSheet: EditText = view.findViewById(R.id.etNewPasswordSheet)
        val etConfirmNewPasswordSheet: EditText = view.findViewById(R.id.etConfirmNewPasswordSheet)
        val btnUpdatePasswordSheet: Button = view.findViewById(R.id.btnUpdatePasswordSheet)

        btnUpdatePasswordSheet.setOnClickListener {
            val currentPass = etCurrentPasswordSheet.text.toString().trim()
            val newPass = etNewPasswordSheet.text.toString().trim()
            val confirmPass = etConfirmNewPasswordSheet.text.toString().trim()

            if (currentPass.isEmpty() || newPass.isEmpty() || confirmPass.isEmpty()) {
                Toast.makeText(this, "Please fill in all password fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (newPass.length < 8) {
                Toast.makeText(this, "New password must be at least 8 characters long", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (newPass != confirmPass) {
                Toast.makeText(this, "New passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnUpdatePasswordSheet.isEnabled = false
            btnUpdatePasswordSheet.text = "Updating Password..."

            ApiClient.changePassword(this, email, currentPass, newPass) { response ->
                runOnUiThread {
                    btnUpdatePasswordSheet.isEnabled = true
                    btnUpdatePasswordSheet.text = "Update Password 🔑"

                    if (response.success) {
                        Toast.makeText(this, "✅ Password Updated Successfully!", Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                    } else {
                        Toast.makeText(this, "Failed to Update Password: ${response.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        dialog.show()
    }
}
