package com.example.tes

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
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
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupObservers()
        viewModel.fetchProvinces()

        setupListeners()
    }

    private fun setupListeners() {
        // KlikDropdown Provinsi
        binding.spinnerProvinsi.onItemClickListener = AdapterView.OnItemClickListener { parent, _, position, _ ->
            binding.spinnerProvinsi.error = null
            val selectedName = parent.getItemAtPosition(position) as String
            val selectedProvince = viewModel.provinces.value?.find { it.nama == selectedName }
            
            selectedProvince?.let {
                // Reset dropdow
                binding.spinnerKabupaten.setText("", false)
                binding.spinnerKecamatan.setText("", false)
                binding.spinnerKelurahan.setText("", false)
                viewModel.fetchRegencies(it.id)
            }
        }

        // Klik ropdown Kabupaten
        binding.spinnerKabupaten.onItemClickListener = AdapterView.OnItemClickListener { parent, _, position, _ ->
            binding.spinnerKabupaten.error = null
            val selectedName = parent.getItemAtPosition(position) as String
            val selectedRegency = viewModel.regencies.value?.find { it.nama == selectedName }
            
            selectedRegency?.let {
                // Reset dropdown
                binding.spinnerKecamatan.setText("", false)
                binding.spinnerKelurahan.setText("", false)
                binding.etZipCode.setText("")
                viewModel.fetchDistricts(it.id)
            }
        }

        // Klik dropdown Kecamatan
        binding.spinnerKecamatan.onItemClickListener = AdapterView.OnItemClickListener { parent, _, position, _ ->
            binding.spinnerKecamatan.error = null
            val selectedName = parent.getItemAtPosition(position) as String
            val selectedDistrict = viewModel.districts.value?.find { it.nama == selectedName }

            selectedDistrict?.let {
                // Reset dropdown di bawahnya
                binding.spinnerKelurahan.setText("", false)
                binding.etZipCode.setText("")
                viewModel.fetchVillages(it.id)
            }
        }

        // Klik dropdown Kelurahan
        binding.spinnerKelurahan.onItemClickListener = AdapterView.OnItemClickListener { parent, _, position, _ ->
            binding.spinnerKelurahan.error = null
            val selectedName = parent.getItemAtPosition(position) as String
            val selectedVillage = viewModel.villages.value?.find { it.nama == selectedName }

            selectedVillage?.let {
                binding.etZipCode.setText(it.kodePos)
            }
        }

        binding.btnRegister.setOnClickListener {
            validateAndRegister()
        }

        setupTextWatchers()
    }

    private fun setupTextWatchers() {
        // Validasi NIK Real-time
        binding.etNik.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val input = s.toString()
                if (input.isNotEmpty() && input.length != 16) {
                    binding.etNik.error = "NIK harus 16 digit (Saat ini: ${input.length})"
                } else {
                    binding.etNik.error = null
                }
            }
        })

        // Validasi Nomor HP Real-time
        binding.etPhone.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val input = s.toString()
                if (input.isNotEmpty() && input.length < 8) {
                    binding.etPhone.error = "Min. 8 karakter"
                } else {
                    binding.etPhone.error = null
                }
            }
        })
        
        // Validasi Nama (Tidak boleh kosong)
        binding.etFullName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s.toString().isEmpty()) {
                    binding.etFullName.error = "Nama tidak boleh kosong"
                } else {
                    binding.etFullName.error = null
                }
            }
        })

        // Validasi Alamat (Min 10 karakter)
        binding.etAddress.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val input = s.toString()
                if (input.isNotEmpty() && input.length < 10) {
                    binding.etAddress.error = "Alamat terlalu pendek (Min. 10 karakter)"
                } else {
                    binding.etAddress.error = null
                }
            }
        })
    }

    private fun validateAndRegister() {
        val name = binding.etFullName.text.toString().trim()
        val nik = binding.etNik.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()
        val province = binding.spinnerProvinsi.text.toString().trim()
        val regency = binding.spinnerKabupaten.text.toString().trim()
        val district = binding.spinnerKecamatan.text.toString().trim()
        val village = binding.spinnerKelurahan.text.toString().trim()
        val zipCode = binding.etZipCode.text.toString().trim()
        val addressDetail = binding.etAddress.text.toString().trim()

        val errorList = mutableListOf<String>()

        // Reset errors first
        binding.etFullName.error = null
        binding.etNik.error = null
        binding.etPhone.error = null
        binding.etAddress.error = null

        if (name.isEmpty()) {
            errorList.add("Nama Lengkap")
            binding.etFullName.error = "Nama tidak boleh kosong"
        }
        
        if (nik.isEmpty()) {
            errorList.add("NIK (Belum diisi)")
            binding.etNik.error = "NIK tidak boleh kosong"
        } else if (nik.length != 16) {
            errorList.add("NIK (Harus 16 digit)")
            binding.etNik.error = "NIK harus 16 digit"
        }
        
        if (phone.isEmpty()) {
            errorList.add("Nomor WhatsApp")
            binding.etPhone.error = "Nomor HP tidak boleh kosong"
        } else if (phone.length < 8) {
            errorList.add("Nomor WhatsApp (Min. 8 karakter)")
            binding.etPhone.error = "Min. 8 karakter"
        }

        // Menggabungkan validasi alamat wilayah menjadi satu kategori
        if (province.isEmpty() || regency.isEmpty() || district.isEmpty() || village.isEmpty()) {
            errorList.add("Pilihan Wilayah (Provinsi/Kota/Kec/Kel)")
            // Untuk AutoCompleteTextView (Dropdown), kita bisa set error di parent-nya atau di teksnya
            if (province.isEmpty()) binding.spinnerProvinsi.error = "Pilih Provinsi"
            if (regency.isEmpty()) binding.spinnerKabupaten.error = "Pilih Kabupaten"
            if (district.isEmpty()) binding.spinnerKecamatan.error = "Pilih Kecamatan"
            if (village.isEmpty()) binding.spinnerKelurahan.error = "Pilih Kelurahan"
        }
        
        if (addressDetail.isEmpty()) {
            errorList.add("Detail Alamat Lengkap (Belum diisi)")
            binding.etAddress.error = "Alamat tidak boleh kosong"
        } else if (addressDetail.length < 10) {
            errorList.add("Detail Alamat Lengkap (Min. 10 karakter)")
            binding.etAddress.error = "Min. 10 karakter"
        }

        if (errorList.isNotEmpty()) {
            val fullErrorMessage = errorList.joinToString("\n") { "• $it" }
            showErrorDialog(fullErrorMessage)
        } else {
            viewModel.register(name, nik, phone, province, regency, district, village, zipCode, addressDetail)
        }
    }

    private fun showErrorDialog(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Lengkapi Data")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun setupObservers() {
        // Observer Loading
        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        // Observer Provinsi
        viewModel.provinces.observe(this) { provinces ->
            val names = provinces.map { it.nama }
            val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, names)
            binding.spinnerProvinsi.setAdapter(adapter)
        }

        // Observer Kabupaten
        viewModel.regencies.observe(this) { regencies ->
            val names = regencies.map { it.nama }
            val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, names)
            binding.spinnerKabupaten.setAdapter(adapter)
        }

        // Observer Kecamatan
        viewModel.districts.observe(this) { districts ->
            val names = districts.map { it.nama }
            val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, names)
            binding.spinnerKecamatan.setAdapter(adapter)
        }

        // Observer Kelurahan
        viewModel.villages.observe(this) { villages ->
            val names = villages.map { it.nama }
            val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, names)
            binding.spinnerKelurahan.setAdapter(adapter)
        }

        // Observer Registrasi Berhasil
        viewModel.registrationSuccess.observe(this) { success ->
            if (success) {
                navigateToDetail()
                clearFields()
            }
        }

        // Observer Error
        viewModel.errorMessage.observe(this) { message ->
            if (message != null) {
                showErrorDialog(message)
                viewModel.clearErrorMessage()
            }
        }
    }

    private fun navigateToDetail() {
        val name = binding.etFullName.text.toString()
        val nik = binding.etNik.text.toString()
        val phone = binding.etPhone.text.toString()
        val fullAddress = "${binding.etAddress.text}, ${binding.spinnerKelurahan.text}, " +
                "${binding.spinnerKecamatan.text}, ${binding.spinnerKabupaten.text}, " +
                "${binding.spinnerProvinsi.text} (${binding.etZipCode.text})"

        val intent = Intent(this, UserDetailActivity::class.java).apply {
            putExtra("EXTRA_NAME", name)
            putExtra("EXTRA_NIK", nik)
            putExtra("EXTRA_PHONE", phone)
            putExtra("EXTRA_ADDRESS", fullAddress)
        }
        startActivity(intent)
    }

    private fun clearFields() {
        binding.etFullName.text?.clear()
        binding.etNik.text?.clear()
        binding.etPhone.text?.clear()
        binding.spinnerProvinsi.setText("", false)
        binding.spinnerKabupaten.setText("", false)
        binding.spinnerKecamatan.setText("", false)
        binding.spinnerKelurahan.setText("", false)
        binding.etZipCode.setText("")
        binding.etAddress.text?.clear()
    }
}
