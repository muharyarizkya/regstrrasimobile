package com.example.tes

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.tes.databinding.ActivityMainBinding
import com.example.tes.viewmodel.RegistrationViewModel
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: RegistrationViewModel by viewModels()
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var selectionMarker: Marker? = null
    private var currentLatitude: Double? = null
    private var currentLongitude: Double? = null
    private var boundaryOverlay: org.osmdroid.views.overlay.FolderOverlay? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            getLastLocation()
        } else {
            showErrorDialog("Izin lokasi ditolak. Koordinat tidak akan terdeteksi secara otomatis.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Konfigurasi OSMDroid
        val basePath = java.io.File(cacheDir.absolutePath, "osmdroid")
        Configuration.getInstance().osmdroidBasePath = basePath
        Configuration.getInstance().osmdroidTileCache = java.io.File(basePath, "tiles")
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 " + packageName
        
        // Mengatur tampilan edge-to-edge
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initMap()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        checkLocationPermission()

        setupObservers()
        viewModel.fetchProvinces()
        viewModel.fetchRegistrants()

        binding.btnFullscreenMap.setOnClickListener {
            showFullscreenMapDialog()
        }

        binding.btnMyLocation.setOnClickListener {
            checkLocationPermission() // This will call getLastLocation()
        }

        setupListeners()
        setupTextWatchers()
    }

    private fun initMap() {
        binding.map.setTileSource(TileSourceFactory.MAPNIK)
        binding.map.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        binding.map.setMultiTouchControls(true)
        
        val mapController = binding.map.controller
        mapController.setZoom(5.0)
        val startPoint = GeoPoint(-2.5489, 118.0149) // Center Indonesia
        mapController.setCenter(startPoint)

        // Mencegah ScrollView mencuri event sentuhan saat pengguna menggeser peta
        binding.map.setOnTouchListener { v, event ->
            v.parent.requestDisallowInterceptTouchEvent(true)
            false
        }

        // Overlay untuk klik di peta
        val receive = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                updateLocationOnMap(p)
                return true
            }
            override fun longPressHelper(p: GeoPoint): Boolean = false
        }
        binding.map.overlays.add(MapEventsOverlay(receive))
    }

    private fun updateLocationOnMap(geoPoint: GeoPoint) {
        updateLocationOnMapUIOnly(geoPoint)
        // Trigger reverse geocoding to auto-fill address
        viewModel.reverseGeocode(geoPoint.latitude, geoPoint.longitude)
    }

    private fun updateLocationOnMapUIOnly(geoPoint: GeoPoint) {
        currentLatitude = geoPoint.latitude
        currentLongitude = geoPoint.longitude
        
        binding.tvLocationCoords.text = "Koordinat: ${geoPoint.latitude}, ${geoPoint.longitude}"
        
        if (selectionMarker == null) {
            selectionMarker = Marker(binding.map)
            selectionMarker?.title = "Lokasi Pendaftaran"
            selectionMarker?.icon = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_location_pin)
            binding.map.overlays.add(selectionMarker)
        }
        
        selectionMarker?.position = geoPoint
        selectionMarker?.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        binding.map.invalidate()
        binding.map.controller.animateTo(geoPoint)
    }

    private fun fillAddressFromNominatim(addr: com.example.tes.model.NominatimAddress) {
        val provinceName = addr.state ?: addr.province ?: ""
        val cityName = addr.city ?: addr.town ?: ""
        val districtName = addr.district ?: ""
        val villageName = addr.village ?: addr.suburb ?: ""
        val zipCode = addr.postcode ?: ""

        // Try to match with existing lists and select them
        viewModel.provinces.value?.find { it.nama.contains(provinceName, true) || provinceName.contains(it.nama, true) }?.let { prov ->
            binding.spinnerProvinsi.setText(prov.nama, false)
            binding.tilKabupaten.visibility = View.VISIBLE
            viewModel.fetchRegencies(prov.id)
            
            // Wait for regencies to load before matching
            viewModel.regencies.observe(this, object : androidx.lifecycle.Observer<List<com.example.tes.model.Kabupaten>> {
                override fun onChanged(regencies: List<com.example.tes.model.Kabupaten>) {
                    regencies.find { it.nama.contains(cityName, true) || cityName.contains(it.nama, true) }?.let { kab ->
                        binding.spinnerKabupaten.setText(kab.nama, false)
                        binding.tilKecamatan.visibility = View.VISIBLE
                        viewModel.fetchDistricts(kab.id)
                        
                        viewModel.districts.observe(this@MainActivity, object : androidx.lifecycle.Observer<List<com.example.tes.model.Kecamatan>> {
                            override fun onChanged(districts: List<com.example.tes.model.Kecamatan>) {
                                districts.find { it.nama.contains(districtName, true) || districtName.contains(it.nama, true) }?.let { kec ->
                                    binding.spinnerKecamatan.setText(kec.nama, false)
                                    binding.tilKelurahan.visibility = View.VISIBLE
                                    viewModel.fetchVillages(kec.id)
                                    
                                    viewModel.villages.observe(this@MainActivity, object : androidx.lifecycle.Observer<List<com.example.tes.model.Kelurahan>> {
                                        override fun onChanged(villages: List<com.example.tes.model.Kelurahan>) {
                                            villages.find { it.nama.contains(villageName, true) || villageName.contains(it.nama, true) }?.let { kel ->
                                                binding.spinnerKelurahan.setText(kel.nama, false)
                                                if (zipCode.isNotEmpty()) binding.etZipCode.setText(zipCode)
                                                else binding.etZipCode.setText(kel.kodePos)
                                            }
                                            viewModel.villages.removeObserver(this)
                                        }
                                    })
                                }
                                viewModel.districts.removeObserver(this)
                            }
                        })
                    }
                    viewModel.regencies.removeObserver(this)
                }
            })
        }
    }


    private fun drawBoundaryPolygon(geoJsonElement: com.google.gson.JsonElement?, map: org.osmdroid.views.MapView) {
        if (geoJsonElement == null || !geoJsonElement.isJsonObject) return

        // Hapus overlay batas lama jika ada
        boundaryOverlay?.let { map.overlays.remove(it) }
        boundaryOverlay = org.osmdroid.views.overlay.FolderOverlay()

        try {
            val jsonObject = geoJsonElement.asJsonObject
            val type = jsonObject.get("type")?.asString ?: return
            val coordinates = jsonObject.get("coordinates") ?: return

            val strokeColor = android.graphics.Color.parseColor("#2196F3") // Warna garis biru
            val fillColor = android.graphics.Color.parseColor("#152196F3") // Biru transparan
            val strokeWidth = 5.0f

            if (type == "Polygon" && coordinates.isJsonArray) {
                val ringsArray = coordinates.asJsonArray
                for (i in 0 until ringsArray.size()) {
                    val ring = ringsArray.get(i).asJsonArray
                    val points = ArrayList<GeoPoint>()
                    for (j in 0 until ring.size()) {
                        val coord = ring.get(j).asJsonArray
                        val lon = coord.get(0).asDouble
                        val lat = coord.get(1).asDouble
                        points.add(GeoPoint(lat, lon))
                    }
                    if (points.isNotEmpty()) {
                        val polygonOverlay = org.osmdroid.views.overlay.Polygon(map)
                        polygonOverlay.points = points
                        polygonOverlay.outlinePaint.color = strokeColor
                        polygonOverlay.outlinePaint.strokeWidth = strokeWidth
                        polygonOverlay.fillPaint.color = fillColor
                        boundaryOverlay?.add(polygonOverlay)
                    }
                }
            } else if (type == "MultiPolygon" && coordinates.isJsonArray) {
                val polygonsArray = coordinates.asJsonArray
                for (p in 0 until polygonsArray.size()) {
                    val ringsArray = polygonsArray.get(p).asJsonArray
                    for (i in 0 until ringsArray.size()) {
                        val ring = ringsArray.get(i).asJsonArray
                        val points = ArrayList<GeoPoint>()
                        for (j in 0 until ring.size()) {
                            val coord = ring.get(j).asJsonArray
                            val lon = coord.get(0).asDouble
                            val lat = coord.get(1).asDouble
                            points.add(GeoPoint(lat, lon))
                        }
                        if (points.isNotEmpty()) {
                            val polygonOverlay = org.osmdroid.views.overlay.Polygon(map)
                            polygonOverlay.points = points
                            polygonOverlay.outlinePaint.color = strokeColor
                            polygonOverlay.outlinePaint.strokeWidth = strokeWidth
                            polygonOverlay.fillPaint.color = fillColor
                            boundaryOverlay?.add(polygonOverlay)
                        }
                    }
                }
            } else if (type == "LineString" && coordinates.isJsonArray) {
                val pointsArray = coordinates.asJsonArray
                val points = ArrayList<GeoPoint>()
                for (i in 0 until pointsArray.size()) {
                    val coord = pointsArray.get(i).asJsonArray
                    val lon = coord.get(0).asDouble
                    val lat = coord.get(1).asDouble
                    points.add(GeoPoint(lat, lon))
                }
                if (points.isNotEmpty()) {
                    val polylineOverlay = org.osmdroid.views.overlay.Polyline(map)
                    polylineOverlay.setPoints(points)
                    polylineOverlay.outlinePaint.color = strokeColor
                    polylineOverlay.outlinePaint.strokeWidth = strokeWidth
                    boundaryOverlay?.add(polylineOverlay)
                }
            } else if (type == "MultiLineString" && coordinates.isJsonArray) {
                val linesArray = coordinates.asJsonArray
                for (l in 0 until linesArray.size()) {
                    val pointsArray = linesArray.get(l).asJsonArray
                    val points = ArrayList<GeoPoint>()
                    for (i in 0 until pointsArray.size()) {
                        val coord = pointsArray.get(i).asJsonArray
                        val lon = coord.get(0).asDouble
                        val lat = coord.get(1).asDouble
                        points.add(GeoPoint(lat, lon))
                    }
                    if (points.isNotEmpty()) {
                        val polylineOverlay = org.osmdroid.views.overlay.Polyline(map)
                        polylineOverlay.setPoints(points)
                        polylineOverlay.outlinePaint.color = strokeColor
                        polylineOverlay.outlinePaint.strokeWidth = strokeWidth
                        boundaryOverlay?.add(polylineOverlay)
                    }
                }
            }

            boundaryOverlay?.let {
                map.overlays.add(0, it) // Tambahkan di index 0 agar berada di bawah pin marker
                map.invalidate()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showFullscreenMapDialog() {
        val dialog = android.app.Dialog(this, android.R.style.Theme_Material_Light_NoActionBar_Fullscreen)
        val dialogBinding = com.example.tes.databinding.DialogFullscreenMapBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        // Setup the MapView inside the dialog
        dialogBinding.mapDialog.setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
        dialogBinding.mapDialog.zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
        dialogBinding.mapDialog.setMultiTouchControls(true)

        val mapController = dialogBinding.mapDialog.controller
        mapController.setZoom(binding.map.zoomLevelDouble)
        val currentCenter = binding.map.mapCenter as GeoPoint
        mapController.setCenter(currentCenter)

        // Synchronize initial spinner text values and visibility
        val provVal = binding.spinnerProvinsi.text.toString()
        val kabVal = binding.spinnerKabupaten.text.toString()
        val kecVal = binding.spinnerKecamatan.text.toString()
        val kelVal = binding.spinnerKelurahan.text.toString()

        dialogBinding.spinnerDialogProvinsi.setText(provVal, false)
        if (provVal.isNotEmpty()) {
            dialogBinding.tilDialogKabupaten.visibility = View.VISIBLE
            dialogBinding.spinnerDialogKabupaten.setText(kabVal, false)
        }
        if (kabVal.isNotEmpty()) {
            dialogBinding.tilDialogKecamatan.visibility = View.VISIBLE
            dialogBinding.spinnerDialogKecamatan.setText(kecVal, false)
        }
        if (kecVal.isNotEmpty()) {
            dialogBinding.tilDialogKelurahan.visibility = View.VISIBLE
            dialogBinding.spinnerDialogKelurahan.setText(kelVal, false)
        }

        // Setup spinner adapters updates
        val provincesObserver = androidx.lifecycle.Observer<List<com.example.tes.model.Provinsi>> { list ->
            dialogBinding.spinnerDialogProvinsi.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, list.map { it.nama }))
        }
        val regenciesObserver = androidx.lifecycle.Observer<List<com.example.tes.model.Kabupaten>> { list ->
            dialogBinding.spinnerDialogKabupaten.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, list.map { it.nama }))
        }
        val districtsObserver = androidx.lifecycle.Observer<List<com.example.tes.model.Kecamatan>> { list ->
            dialogBinding.spinnerDialogKecamatan.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, list.map { it.nama }))
        }
        val villagesObserver = androidx.lifecycle.Observer<List<com.example.tes.model.Kelurahan>> { list ->
            dialogBinding.spinnerDialogKelurahan.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, list.map { it.nama }))
        }

        // Set initial adapters
        viewModel.provinces.value?.let { provincesObserver.onChanged(it) }
        viewModel.regencies.value?.let { regenciesObserver.onChanged(it) }
        viewModel.districts.value?.let { districtsObserver.onChanged(it) }
        viewModel.villages.value?.let { villagesObserver.onChanged(it) }

        // Register observers
        viewModel.provinces.observe(this, provincesObserver)
        viewModel.regencies.observe(this, regenciesObserver)
        viewModel.districts.observe(this, districtsObserver)
        viewModel.villages.observe(this, villagesObserver)

        // Geocoding trigger from inside the dialog
        fun triggerDialogGeocoding() {
            val province = dialogBinding.spinnerDialogProvinsi.text.toString()
            val regency = dialogBinding.spinnerDialogKabupaten.text.toString()
            val district = dialogBinding.spinnerDialogKecamatan.text.toString()
            val village = dialogBinding.spinnerDialogKelurahan.text.toString()

            val query = listOf(village, district, regency, province, "Indonesia")
                .filter { it.isNotEmpty() }
                .joinToString(", ")

            viewModel.searchLocation(query)
        }

        // Dialog spinner click listeners
        dialogBinding.spinnerDialogProvinsi.onItemClickListener = AdapterView.OnItemClickListener { parent, _, position, _ ->
            val selectedName = parent.getItemAtPosition(position) as String
            val selectedProvince = viewModel.provinces.value?.find { it.nama == selectedName }
            selectedProvince?.let {
                dialogBinding.tilDialogKabupaten.visibility = View.VISIBLE
                dialogBinding.tilDialogKecamatan.visibility = View.GONE
                dialogBinding.tilDialogKelurahan.visibility = View.GONE

                dialogBinding.spinnerDialogKabupaten.setText("", false)
                dialogBinding.spinnerDialogKecamatan.setText("", false)
                dialogBinding.spinnerDialogKelurahan.setText("", false)
                viewModel.fetchRegencies(it.id)
                triggerDialogGeocoding()
            }
        }

        dialogBinding.spinnerDialogKabupaten.onItemClickListener = AdapterView.OnItemClickListener { parent, _, position, _ ->
            val selectedName = parent.getItemAtPosition(position) as String
            val selectedRegency = viewModel.regencies.value?.find { it.nama == selectedName }
            selectedRegency?.let {
                dialogBinding.tilDialogKecamatan.visibility = View.VISIBLE
                dialogBinding.tilDialogKelurahan.visibility = View.GONE

                dialogBinding.spinnerDialogKecamatan.setText("", false)
                dialogBinding.spinnerDialogKelurahan.setText("", false)
                viewModel.fetchDistricts(it.id)
                triggerDialogGeocoding()
            }
        }

        dialogBinding.spinnerDialogKecamatan.onItemClickListener = AdapterView.OnItemClickListener { parent, _, position, _ ->
            val selectedName = parent.getItemAtPosition(position) as String
            val selectedDistrict = viewModel.districts.value?.find { it.nama == selectedName }
            selectedDistrict?.let {
                dialogBinding.tilDialogKelurahan.visibility = View.VISIBLE

                dialogBinding.spinnerDialogKelurahan.setText("", false)
                viewModel.fetchVillages(it.id)
                triggerDialogGeocoding()
            }
        }

        var selectedZipCode = ""
        dialogBinding.spinnerDialogKelurahan.onItemClickListener = AdapterView.OnItemClickListener { parent, _, position, _ ->
            val selectedName = parent.getItemAtPosition(position) as String
            val selectedVillage = viewModel.villages.value?.find { it.nama == selectedName }
            selectedVillage?.let {
                selectedZipCode = it.kodePos
                triggerDialogGeocoding()
            }
        }

        // Temporary dialog marker
        var dialogMarker: Marker? = null
        if (currentLatitude != null && currentLongitude != null) {
            dialogMarker = Marker(dialogBinding.mapDialog)
            dialogMarker.position = GeoPoint(currentLatitude!!, currentLongitude!!)
            dialogMarker.icon = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_location_pin)
            dialogBinding.mapDialog.overlays.add(dialogMarker)
            dialogBinding.tvDialogCoords.text = "Koordinat: $currentLatitude, $currentLongitude"
        }

        // Temporary move boundary overlay to dialog map (so we don't have to clone coordinates)
        val hasBoundary = boundaryOverlay != null
        if (hasBoundary) {
            binding.map.overlays.remove(boundaryOverlay)
            dialogBinding.mapDialog.overlays.add(0, boundaryOverlay)
            dialogBinding.mapDialog.invalidate()
        }

        var selectedGeoPoint: GeoPoint? = if (currentLatitude != null && currentLongitude != null) GeoPoint(currentLatitude!!, currentLongitude!!) else null

        // Dialog click listener to select coordinates
        val receive = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                selectedGeoPoint = p
                dialogBinding.tvDialogCoords.text = "Koordinat: ${p.latitude}, ${p.longitude}"
                if (dialogMarker == null) {
                    dialogMarker = Marker(dialogBinding.mapDialog)
                    dialogMarker?.icon = androidx.core.content.ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_location_pin)
                    dialogBinding.mapDialog.overlays.add(dialogMarker)
                }
                dialogMarker?.position = p
                dialogMarker?.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                dialogBinding.mapDialog.invalidate()
                return true
            }
            override fun longPressHelper(p: GeoPoint): Boolean = false
        }
        dialogBinding.mapDialog.overlays.add(org.osmdroid.views.overlay.MapEventsOverlay(receive))

        // Location results observer for dialog
        val dialogLocationObserver = androidx.lifecycle.Observer<com.example.tes.model.NominatimResponse?> { result ->
            result?.let {
                val gp = GeoPoint(it.lat.toDouble(), it.lon.toDouble())
                selectedGeoPoint = gp
                dialogBinding.tvDialogCoords.text = "Koordinat: ${gp.latitude}, ${gp.longitude}"
                if (dialogMarker == null) {
                    dialogMarker = Marker(dialogBinding.mapDialog)
                    dialogMarker?.icon = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_location_pin)
                    dialogBinding.mapDialog.overlays.add(dialogMarker)
                }
                dialogMarker?.position = gp
                dialogMarker?.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                drawBoundaryPolygon(it.geojson, dialogBinding.mapDialog)
                dialogBinding.mapDialog.invalidate()
                dialogBinding.mapDialog.controller.animateTo(gp)
            }
        }
        viewModel.searchLocationResult.observe(this, dialogLocationObserver)

        // Close button
        dialogBinding.btnCloseDialog.setOnClickListener {
            dialog.dismiss()
        }

        // My Location button
        dialogBinding.btnDialogMyLocation.setOnClickListener {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                    location?.let {
                        val gp = GeoPoint(it.latitude, it.longitude)
                        dialogBinding.mapDialog.controller.animateTo(gp)
                        dialogBinding.mapDialog.controller.setZoom(17.0)
                        
                        // Optional: also update marker and coords
                        selectedGeoPoint = gp
                        dialogBinding.tvDialogCoords.text = "Koordinat: ${gp.latitude}, ${gp.longitude}"
                        if (dialogMarker == null) {
                            dialogMarker = Marker(dialogBinding.mapDialog)
                            dialogMarker?.icon = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_location_pin)
                            dialogBinding.mapDialog.overlays.add(dialogMarker)
                        }
                        dialogMarker?.position = gp
                        dialogMarker?.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        dialogBinding.mapDialog.invalidate()
                    }
                }
            } else {
                checkLocationPermission()
            }
        }

        // Select Location button
        dialogBinding.btnConfirmLocation.setOnClickListener {
            selectedGeoPoint?.let { gp ->
                updateLocationOnMap(gp)
            }
            val prov = dialogBinding.spinnerDialogProvinsi.text.toString()
            val kab = dialogBinding.spinnerDialogKabupaten.text.toString()
            val kec = dialogBinding.spinnerDialogKecamatan.text.toString()
            val kel = dialogBinding.spinnerDialogKelurahan.text.toString()

            binding.spinnerProvinsi.setText(prov, false)
            if (prov.isNotEmpty()) {
                binding.tilKabupaten.visibility = View.VISIBLE
                binding.spinnerKabupaten.setText(kab, false)
            }
            if (kab.isNotEmpty()) {
                binding.tilKecamatan.visibility = View.VISIBLE
                binding.spinnerKecamatan.setText(kec, false)
            }
            if (kec.isNotEmpty()) {
                binding.tilKelurahan.visibility = View.VISIBLE
                binding.spinnerKelurahan.setText(kel, false)
            }

            if (selectedZipCode.isNotEmpty()) {
                binding.etZipCode.setText(selectedZipCode)
            }
            dialog.dismiss()
        }

        // Clean up observers and return boundary overlay back to main map on dismiss
        dialog.setOnDismissListener {
            viewModel.provinces.removeObserver(provincesObserver)
            viewModel.regencies.removeObserver(regenciesObserver)
            viewModel.districts.removeObserver(districtsObserver)
            viewModel.villages.removeObserver(villagesObserver)
            viewModel.searchLocationResult.removeObserver(dialogLocationObserver)

            dialogBinding.mapDialog.overlays.clear()

            if (hasBoundary) {
                binding.map.overlays.add(0, boundaryOverlay)
                binding.map.invalidate()
            }
            // Draw latest boundary back to main map if present
            viewModel.searchLocationResult.value?.let { res ->
                drawBoundaryPolygon(res.geojson, binding.map)
            }
        }

        dialog.show()
    }

    private fun checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        } else {
            getLastLocation()
        }
    }

    private fun getLastLocation() {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                if (location != null && currentLatitude == null) {
                    val geoPoint = GeoPoint(location.latitude, location.longitude)
                    updateLocationOnMap(geoPoint)
                    binding.map.controller.setZoom(15.0)
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun setupListeners() {
        binding.spinnerProvinsi.onItemClickListener = AdapterView.OnItemClickListener { parent, _, position, _ ->
            binding.spinnerProvinsi.error = null
            val selectedName = parent.getItemAtPosition(position) as String
            val selectedProvince = viewModel.provinces.value?.find { it.nama == selectedName }
            
            selectedProvince?.let {
                binding.tilKabupaten.visibility = View.VISIBLE
                binding.tilKecamatan.visibility = View.GONE
                binding.tilKelurahan.visibility = View.GONE

                binding.spinnerKabupaten.setText("", false)
                binding.spinnerKecamatan.setText("", false)
                binding.spinnerKelurahan.setText("", false)
                viewModel.fetchRegencies(it.id)
                triggerGeocoding()
            }
        }

        binding.spinnerKabupaten.onItemClickListener = AdapterView.OnItemClickListener { parent, _, position, _ ->
            binding.spinnerKabupaten.error = null
            val selectedName = parent.getItemAtPosition(position) as String
            val selectedRegency = viewModel.regencies.value?.find { it.nama == selectedName }
            
            selectedRegency?.let {
                binding.tilKecamatan.visibility = View.VISIBLE
                binding.tilKelurahan.visibility = View.GONE

                binding.spinnerKecamatan.setText("", false)
                binding.spinnerKelurahan.setText("", false)
                binding.etZipCode.setText("")
                viewModel.fetchDistricts(it.id)
                triggerGeocoding()
            }
        }

        binding.spinnerKecamatan.onItemClickListener = AdapterView.OnItemClickListener { parent, _, position, _ ->
            binding.spinnerKecamatan.error = null
            val selectedName = parent.getItemAtPosition(position) as String
            val selectedDistrict = viewModel.districts.value?.find { it.nama == selectedName }

            selectedDistrict?.let {
                binding.tilKelurahan.visibility = View.VISIBLE

                binding.spinnerKelurahan.setText("", false)
                binding.etZipCode.setText("")
                viewModel.fetchVillages(it.id)
                triggerGeocoding()
            }
        }

        binding.spinnerKelurahan.onItemClickListener = AdapterView.OnItemClickListener { parent, _, position, _ ->
            binding.spinnerKelurahan.error = null
            val selectedName = parent.getItemAtPosition(position) as String
            val selectedVillage = viewModel.villages.value?.find { it.nama == selectedName }

            selectedVillage?.let {
                binding.etZipCode.setText(it.kodePos)
                triggerGeocoding()
            }
        }

        binding.btnRegister.setOnClickListener {
            validateAndRegister()
        }
    }

    private fun triggerGeocoding() {
        val province = binding.spinnerProvinsi.text.toString()
        val regency = binding.spinnerKabupaten.text.toString()
        val district = binding.spinnerKecamatan.text.toString()
        val village = binding.spinnerKelurahan.text.toString()

        val query = listOf(village, district, regency, province, "Indonesia")
            .filter { it.isNotEmpty() }
            .joinToString(", ")

        viewModel.searchLocation(query)
    }

    private fun setupTextWatchers() {
        binding.etNik.addTextChangedListener(createWatcher(
            validator = { s -> if (s != null && s.isNotEmpty() && s.length != 16) "NIK harus 16 digit (Saat ini: ${s.length})" else null },
            onError = { binding.etNik.error = it }
        ))

        binding.etPhone.addTextChangedListener(createWatcher(
            validator = { s -> if (s != null && s.isNotEmpty() && s.length < 8) "Min. 8 karakter" else null },
            onError = { binding.etPhone.error = it }
        ))
        
        binding.etFullName.addTextChangedListener(createWatcher(
            validator = { s -> if (s.isNullOrEmpty()) "Nama tidak boleh kosong" else null },
            onError = { binding.etFullName.error = it }
        ))

        binding.etAddress.addTextChangedListener(createWatcher(
            validator = { s -> if (s != null && s.isNotEmpty() && s.length < 10) "Alamat terlalu pendek (Min. 10 karakter)" else null },
            onError = { binding.etAddress.error = it }
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
        val nik = binding.etNik.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()
        val province = binding.spinnerProvinsi.text.toString().trim()
        val regency = binding.spinnerKabupaten.text.toString().trim()
        val district = binding.spinnerKecamatan.text.toString().trim()
        val village = binding.spinnerKelurahan.text.toString().trim()
        val zipCode = binding.etZipCode.text.toString().trim()
        val addressDetail = binding.etAddress.text.toString().trim()

        val errorList = mutableListOf<String>()

        binding.etFullName.error = null
        binding.etNik.error = null
        binding.etPhone.error = null
        binding.etAddress.error = null

        if (name.isEmpty()) { errorList.add("Nama Lengkap"); binding.etFullName.error = "Nama kosong" }
        if (nik.length != 16) { errorList.add("NIK (Harus 16 digit)"); binding.etNik.error = "Cek NIK" }
        if (phone.length < 8) { errorList.add("Nomor WhatsApp (Min. 8 karakter)"); binding.etPhone.error = "Cek Nomor" }
        if (province.isEmpty() || regency.isEmpty() || district.isEmpty() || village.isEmpty()) {
            errorList.add("Pilihan Wilayah")
            if (province.isEmpty()) binding.spinnerProvinsi.error = "Pilih"
            if (regency.isEmpty()) binding.spinnerKabupaten.error = "Pilih"
            if (district.isEmpty()) binding.spinnerKecamatan.error = "Pilih"
            if (village.isEmpty()) binding.spinnerKelurahan.error = "Pilih"
        }
        if (addressDetail.length < 10) { errorList.add("Detail Alamat (Min. 10 karakter)"); binding.etAddress.error = "Terlalu pendek" }

        if (errorList.isNotEmpty()) {
            showErrorDialog(errorList.joinToString("\n") { "• $it" })
        } else {
            showConfirmationDialog(name, nik, phone, province, regency, district, village, zipCode, addressDetail)
        }
    }

    private fun showConfirmationDialog(
        name: String, nik: String, phone: String, 
        prov: String, kab: String, kec: String, kel: String, 
        zip: String, addr: String
    ) {
        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .create()
        
        val dialogBinding = com.example.tes.databinding.DialogConfirmationBinding.inflate(layoutInflater)
        dialog.setView(dialogBinding.root)

        dialogBinding.tvConfirmName.text = name
        dialogBinding.tvConfirmNik.text = "NIK: $nik"
        dialogBinding.tvConfirmPhone.text = "+62 $phone"
        dialogBinding.tvConfirmRegion.text = "$kel, $kec, $kab, $prov"
        dialogBinding.tvConfirmAddress.text = addr
        dialogBinding.tvConfirmCoords.text = if (currentLatitude != null) "GPS: $currentLatitude, $currentLongitude" else "GPS: Tidak terdeteksi"

        dialogBinding.btnConfirmYes.setOnClickListener {
            viewModel.register(name, nik, phone, prov, kab, kec, kel, zip, addr, currentLatitude, currentLongitude)
            dialog.dismiss()
        }

        dialogBinding.btnConfirmNo.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showErrorDialog(message: String) {
        AlertDialog.Builder(this).setTitle("Lengkapi Data").setMessage(message).setPositiveButton("OK", null).show()
    }

    private fun setupObservers() {
        viewModel.isLoading.observe(this) { binding.progressBar.visibility = if (it) View.VISIBLE else View.GONE }
        viewModel.provinces.observe(this) { list -> 
            binding.spinnerProvinsi.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, list.map { it.nama })) 
        }
        viewModel.regencies.observe(this) { list -> 
            binding.spinnerKabupaten.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, list.map { it.nama })) 
        }
        viewModel.districts.observe(this) { list -> 
            binding.spinnerKecamatan.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, list.map { it.nama })) 
        }
        viewModel.villages.observe(this) { list -> 
            binding.spinnerKelurahan.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, list.map { it.nama })) 
        }

        viewModel.registrants.observe(this) { list ->
            // Clear existing markers to prevent duplication
            binding.map.overlays.removeAll { it is Marker && it != selectionMarker }
            list.forEach { item ->
                if (item.latitude != null && item.longitude != null) {
                    val marker = Marker(binding.map)
                    marker.position = GeoPoint(item.latitude, item.longitude)
                    marker.title = item.namaLengkap
                    marker.snippet = item.alamatLengkap
                    marker.icon = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_location_pin)
                    binding.map.overlays.add(marker)
                }
            }
            binding.map.invalidate()
        }

        viewModel.searchLocationResult.observe(this) { result ->
            result?.let { 
                updateLocationOnMapUIOnly(GeoPoint(it.lat.toDouble(), it.lon.toDouble()))
                drawBoundaryPolygon(it.geojson, binding.map)
                
                // If result has address (from reverse geocoding), fill the spinners
                it.address?.let { addr ->
                    fillAddressFromNominatim(addr)
                }
            }
        }

        viewModel.registrationSuccess.observe(this) { if (it) { navigateToDetail(); clearFields() } }
        viewModel.errorMessage.observe(this) { it?.let { showErrorDialog(it); viewModel.clearErrorMessage() } }
    }

    private fun navigateToDetail() {
        val name = binding.etFullName.text.toString()
        val nik = binding.etNik.text.toString()
        val phone = binding.etPhone.text.toString()
        val fullAddress = "${binding.etAddress.text}, ${binding.spinnerKelurahan.text}, ${binding.spinnerKecamatan.text}, ${binding.spinnerKabupaten.text}, ${binding.spinnerProvinsi.text}"
        
        val intent = Intent(this, UserDetailActivity::class.java).apply {
            putExtra("EXTRA_NAME", name)
            putExtra("EXTRA_NIK", nik)
            putExtra("EXTRA_PHONE", phone)
            putExtra("EXTRA_ADDRESS", fullAddress)
            putExtra("EXTRA_LAT", currentLatitude ?: 0.0)
            putExtra("EXTRA_LON", currentLongitude ?: 0.0)
        }
        startActivity(intent)
    }

    override fun onResume() { super.onResume(); binding.map.onResume() }
    override fun onPause() { super.onPause(); binding.map.onPause() }

    private fun clearFields() {
        binding.etFullName.text?.clear()
        binding.etNik.text?.clear()
        binding.etPhone.text?.clear()
        binding.spinnerProvinsi.setText("", false)
        binding.spinnerKabupaten.setText("", false)
        binding.spinnerKecamatan.setText("", false)
        binding.spinnerKelurahan.setText("", false)
        binding.tilKabupaten.visibility = View.GONE
        binding.tilKecamatan.visibility = View.GONE
        binding.tilKelurahan.visibility = View.GONE
        binding.etZipCode.setText("")
        binding.etAddress.text?.clear()
    }
}
