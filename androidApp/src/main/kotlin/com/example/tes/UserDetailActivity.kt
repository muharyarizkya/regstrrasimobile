package com.example.tes

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.tes.databinding.ActivityUserDetailBinding

class UserDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUserDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Ambil data dari Intent
        val name = intent.getStringExtra("EXTRA_NAME")
        val nik = intent.getStringExtra("EXTRA_NIK")
        val phone = intent.getStringExtra("EXTRA_PHONE")
        val fullAddress = intent.getStringExtra("EXTRA_ADDRESS")

        // Tampilkan data ke UI
        binding.tvValueName.text = name
        binding.tvValueNik.text = nik
        binding.tvValuePhone.text = phone
        binding.tvValueFullAddress.text = fullAddress

        // Tombol Kembali
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.btnBackToHome.setOnClickListener { finish() }
    }
}
