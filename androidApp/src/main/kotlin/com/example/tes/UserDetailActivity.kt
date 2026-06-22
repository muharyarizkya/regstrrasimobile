package com.example.tes

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.tes.databinding.ActivityUserDetailBinding
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker

class UserDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUserDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Konfigurasi OSMDroid
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
        
        binding = ActivityUserDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Ambil data dari Intent
        val name = intent.getStringExtra("EXTRA_NAME")
        val nik = intent.getStringExtra("EXTRA_NIK")
        val phone = intent.getStringExtra("EXTRA_PHONE")
        val fullAddress = intent.getStringExtra("EXTRA_ADDRESS")
        val lat = intent.getDoubleExtra("EXTRA_LAT", 0.0)
        val lon = intent.getDoubleExtra("EXTRA_LON", 0.0)

        // Tampilkan data ke UI
        binding.tvValueName.text = name
        binding.tvValueNik.text = nik
        binding.tvValuePhone.text = phone
        binding.tvValueFullAddress.text = fullAddress

        // Setup Map
        if (lat != 0.0 && lon != 0.0) {
            binding.mapDetail.setTileSource(TileSourceFactory.MAPNIK)
            binding.mapDetail.setMultiTouchControls(true)
            val mapController = binding.mapDetail.controller
            mapController.setZoom(17.0)
            val startPoint = GeoPoint(lat, lon)
            mapController.setCenter(startPoint)

            val marker = Marker(binding.mapDetail)
            marker.position = startPoint
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            marker.icon = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_location_pin)
            binding.mapDetail.overlays.add(marker)
        }

        // Tombol Kembali
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.btnBackToHome.setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        binding.mapDetail.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapDetail.onPause()
    }
}
