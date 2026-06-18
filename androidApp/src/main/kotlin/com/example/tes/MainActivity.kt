package com.example.tes

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
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
            val selectedName = parent.getItemAtPosition(position) as String
            val selectedProvince = viewModel.provinces.value?.find { it.nama == selectedName }
            
            selectedProvince?.let {
                // Reset dropdow
                binding.spinnerKabupaten.setText("", false)
                binding.spinnerKecamatan.setText("", false)
                viewModel.fetchRegencies(it.id)
            }
        }

        // Klik Dropdown Kabupaten
        binding.spinnerKabupaten.onItemClickListener = AdapterView.OnItemClickListener { parent, _, position, _ ->
            val selectedName = parent.getItemAtPosition(position) as String
            val selectedRegency = viewModel.regencies.value?.find { it.nama == selectedName }
            
            selectedRegency?.let {
                // Reset dropdown di bawahnya
                binding.spinnerKecamatan.setText("", false)
                binding.spinnerKelurahan.setText("", false)
                binding.etZipCode.setText("")
                viewModel.fetchDistricts(it.id)
            }
        }

        // Klik Dropdown Kecamatan
        binding.spinnerKecamatan.onItemClickListener = AdapterView.OnItemClickListener { parent, _, position, _ ->
            val selectedName = parent.getItemAtPosition(position) as String
            val selectedDistrict = viewModel.districts.value?.find { it.nama == selectedName }

            selectedDistrict?.let {
                // Reset dropdown di bawahnya
                binding.spinnerKelurahan.setText("", false)
                binding.etZipCode.setText("")
                viewModel.fetchVillages(it.id)
            }
        }

        // Klik Dropdown Kelurahan
        binding.spinnerKelurahan.onItemClickListener = AdapterView.OnItemClickListener { parent, _, position, _ ->
            val selectedName = parent.getItemAtPosition(position) as String
            val selectedVillage = viewModel.villages.value?.find { it.nama == selectedName }

            selectedVillage?.let {
                binding.etZipCode.setText(it.kodePos)
            }
        }

        binding.btnRegister.setOnClickListener {
            validateAndRegister()
        }
    }

    private fun validateAndRegister() {
        val name = binding.etFullName.text.toString()
        val nik = binding.etNik.text.toString()
        val phone = binding.etPhone.text.toString()
        val province = binding.spinnerProvinsi.text.toString()
        val regency = binding.spinnerKabupaten.text.toString()
        val district = binding.spinnerKecamatan.text.toString()
        val village = binding.spinnerKelurahan.text.toString()
        val zipCode = binding.etZipCode.text.toString()
        val addressDetail = binding.etAddress.text.toString()

        if (name.isEmpty() || nik.isEmpty() || phone.isEmpty() || 
            province.isEmpty() || regency.isEmpty() || district.isEmpty() || 
            village.isEmpty() || zipCode.isEmpty() || addressDetail.isEmpty()) {
            Toast.makeText(this, "Harap isi semua data dengan lengkap", Toast.LENGTH_SHORT).show()
        } else {
            viewModel.register(name, nik, phone, province, regency, district, village, zipCode, addressDetail)
        }
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
                Toast.makeText(this, "Registrasi berhasil terkirim dan disimpan!", Toast.LENGTH_LONG).show()
                clearFields()
            }
        }

        // Observer Error
        viewModel.errorMessage.observe(this) { message ->
            if (message != null) {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        }
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
