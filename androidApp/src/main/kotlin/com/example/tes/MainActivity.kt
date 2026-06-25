package com.example.tes

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.tes.databinding.ActivityMainBinding
import com.example.tes.viewmodel.RegistrationViewModel

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: RegistrationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge styling
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        setupTextWatchers()
        setupObservers()
    }

    private fun setupListeners() {
        binding.btnRegister.setOnClickListener {
            validateAndRegister()
        }

        binding.btnBackToWelcome.setOnClickListener {
            finish()
        }
    }

    private fun setupTextWatchers() {
        binding.etFullName.addTextChangedListener(createWatcher(
            validator = { s -> if (s.isNullOrEmpty()) "Nama tidak boleh kosong" else null },
            onError = { binding.etFullName.error = it }
        ))

        binding.etPhone.addTextChangedListener(createWatcher(
            validator = { s -> if (s != null && s.isNotEmpty() && s.length < 8) "Min. 8 karakter" else null },
            onError = { binding.etPhone.error = it }
        ))

        binding.etPassword.addTextChangedListener(createWatcher(
            validator = { s -> if (s != null && s.isNotEmpty() && s.length < 6) "Password minimal 6 karakter" else null },
            onError = { binding.etPassword.error = it }
        ))

        binding.etConfirmPassword.addTextChangedListener(createWatcher(
            validator = { s -> 
                val pass = binding.etPassword.text.toString()
                if (s != null && s.isNotEmpty() && s.toString() != pass) "Password tidak cocok" else null 
            },
            onError = { binding.etConfirmPassword.error = it }
        ))
    }

    private fun createWatcher(validator: (Editable?) -> String?, onError: (String?) -> Unit): TextWatcher {
        return object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { onError(validator(s)) }
        }
    }

    private fun validateAndRegister() {
        val name = binding.etFullName.text.toString().trim()
        val rawPhone = binding.etPhone.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        val confirmPassword = binding.etConfirmPassword.text.toString().trim()

        val phone = formatPhoneNumber(rawPhone)

        val errorList = mutableListOf<String>()

        binding.etFullName.error = null
        binding.etPhone.error = null
        binding.etPassword.error = null
        binding.etConfirmPassword.error = null

        if (name.isEmpty()) { 
            errorList.add("Nama Lengkap")
            binding.etFullName.error = "Nama kosong" 
        }
        if (rawPhone.isEmpty()) {
            errorList.add("Nomor HP")
            binding.etPhone.error = "Nomor HP tidak boleh kosong"
        } else if (phone.length < 8 || phone.length > 15) { 
            errorList.add("Nomor HP (8-15 karakter termasuk kode negara)")
            binding.etPhone.error = "Nomor HP harus 8-15 karakter termasuk +62" 
        }
        if (password.length < 6) { 
            errorList.add("Password (Min. 6 karakter)")
            binding.etPassword.error = "Password minimal 6 karakter" 
        }
        if (confirmPassword != password) {
            errorList.add("Konfirmasi Password tidak cocok")
            binding.etConfirmPassword.error = "Password tidak cocok"
        }

        if (errorList.isNotEmpty()) {
            showErrorDialog(errorList.joinToString("\n") { "• $it" })
        } else {
            showLoading(true)
            // Call API via ViewModel (optional fields left blank/default)
            viewModel.register(
                nama = name,
                nik = "",
                phone = phone,
                province = "",
                regency = "",
                district = "",
                village = "",
                zipCode = "",
                address = "",
                password = password
            )
        }
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnRegister.isEnabled = !isLoading
    }

    private fun setupObservers() {
        viewModel.isLoading.observe(this) { 
            showLoading(it)
        }

        viewModel.registrationSuccess.observe(this) { success ->
            if (success) {
                Toast.makeText(this, "Registrasi Berhasil! Silakan Login.", Toast.LENGTH_LONG).show()
                val intent = Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                finish()
            }
        }

        viewModel.errorMessage.observe(this) { error ->
            error?.let { 
                showErrorDialog(it)
                viewModel.clearErrorMessage()
            }
        }
    }

    private fun showErrorDialog(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Pendaftaran Gagal")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
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
