package com.example.tes

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.tes.databinding.ActivityLoginBinding
import com.example.tes.model.LoginRequest
import com.example.tes.network.ApiClient
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge styling
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnLoginSubmit.setOnClickListener {
            performLogin()
        }

        binding.btnBackToWelcome.setOnClickListener {
            finish()
        }
    }

    private fun performLogin() {
        val rawPhone = binding.etLoginPhone.text.toString().trim()
        val password = binding.etLoginPassword.text.toString().trim()

        if (rawPhone.isEmpty() || password.isEmpty()) {
            showError("Nomor HP dan password tidak boleh kosong")
            return
        }

        val phone = formatPhoneNumber(rawPhone)

        hideError()
        showLoading(true)

        lifecycleScope.launch {
            try {
                val request = LoginRequest(phone, password)
                val response = ApiClient.apiService.login(request)
                
                showLoading(false)
                if (response.isSuccessful) {
                    val loginRes = response.body()
                    if (loginRes != null && loginRes.status && loginRes.token != null) {
                        // Save token in SharedPreferences
                        val prefs = getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
                        prefs.edit().putString("jwt_token", loginRes.token).apply()

                        // Navigate to Dashboard
                        val intent = Intent(this@LoginActivity, DashboardActivity::class.java).apply {
                            putExtra("EXTRA_TOKEN", loginRes.token)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        startActivity(intent)
                        finish()
                    } else {
                        showError(loginRes?.message ?: "Login gagal. Silakan periksa kredensial Anda.")
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    val errorMessage = if (errorBody != null) {
                        try {
                            org.json.JSONObject(errorBody).optString("message")
                        } catch (e: Exception) { null }
                    } else null
                    showError(errorMessage ?: "Kredensial salah atau terjadi kesalahan pada server.")
                }
            } catch (e: Exception) {
                showLoading(false)
                showError("Koneksi gagal: ${e.localizedMessage}")
            }
        }
    }

    private fun showLoading(isLoading: Boolean) {
        binding.loginProgressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnLoginSubmit.isEnabled = !isLoading
    }

    private fun showError(message: String) {
        binding.tvLoginError.text = message
        binding.cardLoginError.visibility = View.VISIBLE
    }

    private fun hideError() {
        binding.cardLoginError.visibility = View.GONE
    }

    private fun formatPhoneNumber(input: String): String {
        val clean = input.trim()
        if (clean.startsWith("+62")) {
            return clean
        }
        if (clean.startsWith("62")) {
            return "+$clean"
        }
        if (clean.startsWith("0")) {
            return "+62" + clean.substring(1)
        }
        return "+62$clean"
    }
}
