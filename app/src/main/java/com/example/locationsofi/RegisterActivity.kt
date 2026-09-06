package com.example.locationsofi

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog

class RegisterActivity : AppCompatActivity() {

    private lateinit var btnBack: View
    private lateinit var etRegName: EditText
    private lateinit var etRegEmail: EditText
    private lateinit var etRegPassword: EditText
    private lateinit var btnToggleRegPassword: ImageView
    private lateinit var etRegConfirmPassword: EditText
    private lateinit var strengthSegment1: View
    private lateinit var strengthSegment2: View
    private lateinit var strengthSegment3: View
    private lateinit var tvStrengthLabel: TextView
    private lateinit var cbTerms: CheckBox
    private lateinit var btnRegister: Button
    private lateinit var tvGoLogin: TextView

    private var isPasswordVisible = false
    private var rawHwid = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        btnBack = findViewById(R.id.btnBack)
        etRegName = findViewById(R.id.etRegName)
        etRegEmail = findViewById(R.id.etRegEmail)
        etRegPassword = findViewById(R.id.etRegPassword)
        btnToggleRegPassword = findViewById(R.id.btnToggleRegPassword)
        etRegConfirmPassword = findViewById(R.id.etRegConfirmPassword)
        strengthSegment1 = findViewById(R.id.strengthSegment1)
        strengthSegment2 = findViewById(R.id.strengthSegment2)
        strengthSegment3 = findViewById(R.id.strengthSegment3)
        tvStrengthLabel = findViewById(R.id.tvStrengthLabel)
        cbTerms = findViewById(R.id.cbTerms)
        btnRegister = findViewById(R.id.btnRegister)
        tvGoLogin = findViewById(R.id.tvGoLogin)

        btnBack.setOnClickListener { finish() }
        tvGoLogin.setOnClickListener { finish() }

        // Fetch HWID & Mask
        rawHwid = DeviceInfo.getHardwareId(this)
        val masked = maskHardwareId(rawHwid)

        // Toggle Password Visibility
        btnToggleRegPassword.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            if (isPasswordVisible) {
                etRegPassword.inputType = InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                btnToggleRegPassword.setColorFilter(Color.parseColor("#2563EB"))
            } else {
                etRegPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                btnToggleRegPassword.setColorFilter(Color.parseColor("#94A3B8"))
            }
            etRegPassword.setSelection(etRegPassword.text.length)
        }

        // Password Strength Watcher
        etRegPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                evaluatePasswordStrength(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnRegister.setOnClickListener {
            val name = etRegName.text.toString().trim()
            val email = etRegEmail.text.toString().trim()
            val pass = etRegPassword.text.toString().trim()
            val confirmPass = etRegConfirmPassword.text.toString().trim()

            if (name.isEmpty() || email.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Please fill in all required fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!validatePasswordRequirements(pass)) {
                Toast.makeText(this, "Password must be at least 8 characters long and contain uppercase, lowercase, number, and special character", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            if (pass != confirmPass) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!cbTerms.isChecked) {
                Toast.makeText(this, "Please agree to the Terms of Service & Privacy Policy", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnRegister.isEnabled = false
            btnRegister.text = "Creating Account..."

            ApiClient.registerUser(this, name, email, pass) { response ->
                runOnUiThread {
                    btnRegister.isEnabled = true
                    btnRegister.text = "Create Account"

                    if (response.success) {
                        showSuccessBottomSheet(name, email, masked)
                    } else {
                        Toast.makeText(this, "Registration Failed: ${response.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun maskHardwareId(hwid: String): String {
        return if (hwid.length > 8) {
            "${hwid.take(4)}...${hwid.takeLast(4)}"
        } else {
            hwid
        }
    }

    private fun validatePasswordRequirements(pass: String): Boolean {
        if (pass.length < 8) return false
        val hasUpper = pass.any { it.isUpperCase() }
        val hasLower = pass.any { it.isLowerCase() }
        val hasDigit = pass.any { it.isDigit() }
        val hasSpecial = pass.any { !it.isLetterOrDigit() }
        return hasUpper && hasLower && hasDigit && hasSpecial
    }

    private fun evaluatePasswordStrength(pass: String) {
        val score = when {
            pass.isEmpty() -> 0
            validatePasswordRequirements(pass) -> 3
            pass.length >= 8 && (pass.any { it.isDigit() } || pass.any { it.isUpperCase() }) -> 2
            else -> 1
        }

        when (score) {
            0 -> {
                strengthSegment1.setBackgroundColor(Color.parseColor("#E2E8F0"))
                strengthSegment2.setBackgroundColor(Color.parseColor("#E2E8F0"))
                strengthSegment3.setBackgroundColor(Color.parseColor("#E2E8F0"))
                tvStrengthLabel.text = "Weak"
                tvStrengthLabel.setTextColor(Color.parseColor("#EF4444"))
            }
            1 -> {
                strengthSegment1.setBackgroundColor(Color.parseColor("#EF4444"))
                strengthSegment2.setBackgroundColor(Color.parseColor("#E2E8F0"))
                strengthSegment3.setBackgroundColor(Color.parseColor("#E2E8F0"))
                tvStrengthLabel.text = "Weak"
                tvStrengthLabel.setTextColor(Color.parseColor("#EF4444"))
            }
            2 -> {
                strengthSegment1.setBackgroundColor(Color.parseColor("#F59E0B"))
                strengthSegment2.setBackgroundColor(Color.parseColor("#F59E0B"))
                strengthSegment3.setBackgroundColor(Color.parseColor("#E2E8F0"))
                tvStrengthLabel.text = "Medium"
                tvStrengthLabel.setTextColor(Color.parseColor("#F59E0B"))
            }
            else -> {
                strengthSegment1.setBackgroundColor(Color.parseColor("#10B981"))
                strengthSegment2.setBackgroundColor(Color.parseColor("#10B981"))
                strengthSegment3.setBackgroundColor(Color.parseColor("#10B981"))
                tvStrengthLabel.text = "Strong"
                tvStrengthLabel.setTextColor(Color.parseColor("#10B981"))
            }
        }
    }

    private fun showSuccessBottomSheet(name: String, email: String, maskedHwid: String) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.layout_registration_success_sheet, null)
        dialog.setContentView(view)
        dialog.setCancelable(false)

        val tvMaskedHwidSheet: TextView = view.findViewById(R.id.tvMaskedHwidSheet)
        val btnGoToLogin: Button = view.findViewById(R.id.btnGoToLogin)

        tvMaskedHwidSheet.text = "Device HWID: $maskedHwid"

        btnGoToLogin.setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        dialog.show()
    }
}
