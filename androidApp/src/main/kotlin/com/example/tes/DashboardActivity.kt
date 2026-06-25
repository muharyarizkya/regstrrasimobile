package com.example.tes

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.tes.databinding.ActivityDashboardBinding
import com.example.tes.model.RegistrasiRequest
import com.example.tes.network.ApiClient
import com.example.tes.viewmodel.RegistrationViewModel
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import kotlinx.coroutines.launch

class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private var token: String? = null
    private var userId: Int = -1
    private var userPhone: String = ""
    private var userName: String = ""

    // Maps variables
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLatitude: Double? = null
    private var currentLongitude: Double? = null
    private var selectionMarker: Marker? = null
    private var boundaryOverlay: FolderOverlay? = null

    private lateinit var viewModel: RegistrationViewModel

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineGranted || coarseGranted) {
            getLastLocation()
        } else {
            Toast.makeText(this, "Izin lokasi ditolak. Peta tidak dapat mendeteksi lokasi saat ini.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize OSM Map Config
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))

        // Edge-to-edge styling
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }

        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[RegistrationViewModel::class.java]

        // Retrieve token
        val prefs = getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        token = intent.getStringExtra("EXTRA_TOKEN") ?: prefs.getString("jwt_token", null)

        if (token.isNullOrEmpty()) {
            navigateToLogin()
            return
        }

        binding.btnToolbarLogout.setOnClickListener {
            performLogout()
        }

        binding.btnDashboardErrorRetry.setOnClickListener {
            navigateToLogin()
        }

        binding.btnSaveProfile.setOnClickListener {
            validateAndUpdateProfile()
        }

        // Initial state
        fetchSessionDetails()
    }

    private fun fetchSessionDetails() {
        showLoading()

        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.getMe("Bearer $token")
                if (response.isSuccessful) {
                    val meRes = response.body()
                    if (meRes != null && meRes.status) {
                        val profile = meRes.user
                        val debug = meRes.debug

                        userId = profile?.id ?: -1
                        userPhone = profile?.nomorHp ?: ""
                        userName = profile?.namaLengkap ?: ""

                        // Name header greeting
                        binding.tvDashUserName.text = profile?.namaLengkap ?: "-"

                        if (profile?.nik.isNullOrEmpty() || profile?.alamatLengkap.isNullOrEmpty()) {
                            // STATE 1: Profile is incomplete
                            setupIncompleteProfileForm()
                        } else {
                            // STATE 2: Profile is complete
                            showCompleteProfile(profile)
                        }
                    } else {
                        showError(meRes?.message ?: "Gagal memverifikasi token JWT.")
                    }
                } else {
                    val errBody = response.errorBody()?.string()
                    val errMsg = if (errBody != null) {
                        try {
                            JSONObject(errBody).optString("message")
                        } catch (e: Exception) { null }
                    } else null
                    showError(errMsg ?: "Sesi JWT tidak valid atau sudah berakhir.")
                }
            } catch (e: Exception) {
                showError("Gagal memuat profil: ${e.localizedMessage}")
            }
        }
    }

    // STATE 1: Profile Incomplete Setup
    private fun setupIncompleteProfileForm() {
        binding.layoutDashboardLoading.visibility = View.GONE
        binding.layoutDashboardError.visibility = View.GONE
        binding.layoutProfileComplete.visibility = View.GONE
        binding.layoutProfileIncomplete.visibility = View.VISIBLE
        binding.scrollDashboardContent.visibility = View.VISIBLE
        binding.btnToolbarLogout.visibility = View.VISIBLE

        initMap()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        checkLocationPermission()

        setupRegionalSpinners()
        setupObserversForForm()
        viewModel.fetchProvinces()
    }

    private fun initMap() {
        binding.dashMap.setTileSource(TileSourceFactory.MAPNIK)
        binding.dashMap.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        binding.dashMap.setMultiTouchControls(true)

        val mapController = binding.dashMap.controller
        mapController.setZoom(5.0)
        val startPoint = GeoPoint(-2.5489, 118.0149) // Center Indonesia
        mapController.setCenter(startPoint)

        binding.dashMap.setOnTouchListener { v, event ->
            v.parent.requestDisallowInterceptTouchEvent(true)
            false
        }

        val receive = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                updateLocationOnMap(p)
                return true
            }
            override fun longPressHelper(p: GeoPoint): Boolean = false
        }
        binding.dashMap.overlays.add(MapEventsOverlay(receive))

        binding.btnDashFullscreenMap.setOnClickListener {
            showFullscreenMapDialog()
        }

        binding.btnDashMyLocation.setOnClickListener {
            checkLocationPermission()
        }
    }

    private fun updateLocationOnMap(geoPoint: GeoPoint) {
        updateLocationOnMapUIOnly(geoPoint)
        viewModel.reverseGeocode(geoPoint.latitude, geoPoint.longitude)
    }

    private fun updateLocationOnMapUIOnly(geoPoint: GeoPoint) {
        currentLatitude = geoPoint.latitude
        currentLongitude = geoPoint.longitude

        binding.tvDashCoords.text = "Koordinat: ${geoPoint.latitude}, ${geoPoint.longitude}"

        if (selectionMarker == null) {
            selectionMarker = Marker(binding.dashMap)
            selectionMarker?.title = "Lokasi Saya"
            selectionMarker?.icon = ContextCompat.getDrawable(this, R.drawable.ic_location_pin)
            binding.dashMap.overlays.add(selectionMarker)
        }

        selectionMarker?.position = geoPoint
        selectionMarker?.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        binding.dashMap.invalidate()
        binding.dashMap.controller.animateTo(geoPoint)
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
                    binding.dashMap.controller.setZoom(15.0)
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun setupRegionalSpinners() {
        binding.spinnerDashProv.onItemClickListener = AdapterView.OnItemClickListener { parent, _, position, _ ->
            binding.spinnerDashProv.error = null
            val selectedName = parent.getItemAtPosition(position) as String
            val selectedProvince = viewModel.provinces.value?.find { it.nama == selectedName }

            selectedProvince?.let {
                binding.tilDashKab.visibility = View.VISIBLE
                binding.tilDashKec.visibility = View.GONE
                binding.tilDashKel.visibility = View.GONE

                binding.spinnerDashKab.setText("", false)
                binding.spinnerDashKec.setText("", false)
                binding.spinnerDashKel.setText("", false)
                viewModel.fetchRegencies(it.id)
                triggerGeocoding()
            }
        }

        binding.spinnerDashKab.onItemClickListener = AdapterView.OnItemClickListener { parent, _, position, _ ->
            binding.spinnerDashKab.error = null
            val selectedName = parent.getItemAtPosition(position) as String
            val selectedRegency = viewModel.regencies.value?.find { it.nama == selectedName }

            selectedRegency?.let {
                binding.tilDashKec.visibility = View.VISIBLE
                binding.tilDashKel.visibility = View.GONE

                binding.spinnerDashKec.setText("", false)
                binding.spinnerDashKel.setText("", false)
                binding.etDashZip.setText("")
                viewModel.fetchDistricts(it.id)
                triggerGeocoding()
            }
        }

        binding.spinnerDashKec.onItemClickListener = AdapterView.OnItemClickListener { parent, _, position, _ ->
            binding.spinnerDashKec.error = null
            val selectedName = parent.getItemAtPosition(position) as String
            val selectedDistrict = viewModel.districts.value?.find { it.nama == selectedName }

            selectedDistrict?.let {
                binding.tilDashKel.visibility = View.VISIBLE

                binding.spinnerDashKel.setText("", false)
                binding.etDashZip.setText("")
                viewModel.fetchVillages(it.id)
                triggerGeocoding()
            }
        }

        binding.spinnerDashKel.onItemClickListener = AdapterView.OnItemClickListener { parent, _, position, _ ->
            binding.spinnerDashKel.error = null
            val selectedName = parent.getItemAtPosition(position) as String
            val selectedVillage = viewModel.villages.value?.find { it.nama == selectedName }

            selectedVillage?.let {
                binding.etDashZip.setText(it.kodePos)
                triggerGeocoding()
            }
        }
    }

    private fun triggerGeocoding() {
        val province = binding.spinnerDashProv.text.toString()
        val regency = binding.spinnerDashKab.text.toString()
        val district = binding.spinnerDashKec.text.toString()
        val village = binding.spinnerDashKel.text.toString()

        val query = listOf(village, district, regency, province, "Indonesia")
            .filter { it.isNotEmpty() }
            .joinToString(", ")

        viewModel.searchLocation(query)
    }

    private fun setupObserversForForm() {
        viewModel.provinces.observe(this) { list ->
            binding.spinnerDashProv.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, list.map { it.nama }))
        }
        viewModel.regencies.observe(this) { list ->
            binding.spinnerDashKab.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, list.map { it.nama }))
        }
        viewModel.districts.observe(this) { list ->
            binding.spinnerDashKec.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, list.map { it.nama }))
        }
        viewModel.villages.observe(this) { list ->
            binding.spinnerDashKel.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, list.map { it.nama }))
        }

        viewModel.searchLocationResult.observe(this) { result ->
            result?.let {
                val gp = GeoPoint(it.lat.toDouble(), it.lon.toDouble())
                updateLocationOnMapUIOnly(gp)
                drawBoundaryPolygon(it.geojson, binding.dashMap)

                it.address?.let { addr ->
                    fillAddressFromNominatim(addr)
                }
            }
        }
    }

    private fun fillAddressFromNominatim(addr: com.example.tes.model.NominatimAddress) {
        val provinceName = addr.state ?: addr.province ?: ""
        val cityName = addr.city ?: addr.town ?: ""
        val districtName = addr.district ?: ""
        val villageName = addr.village ?: addr.suburb ?: ""
        val zipCode = addr.postcode ?: ""

        viewModel.provinces.value?.find { it.nama.contains(provinceName, true) || provinceName.contains(it.nama, true) }?.let { prov ->
            binding.spinnerDashProv.setText(prov.nama, false)
            binding.tilDashKab.visibility = View.VISIBLE
            viewModel.fetchRegencies(prov.id)

            viewModel.regencies.observe(this, object : Observer<List<com.example.tes.model.Kabupaten>> {
                override fun onChanged(regencies: List<com.example.tes.model.Kabupaten>) {
                    regencies.find { it.nama.contains(cityName, true) || cityName.contains(it.nama, true) }?.let { kab ->
                        binding.spinnerDashKab.setText(kab.nama, false)
                        binding.tilDashKec.visibility = View.VISIBLE
                        viewModel.fetchDistricts(kab.id)

                        viewModel.districts.observe(this@DashboardActivity, object : Observer<List<com.example.tes.model.Kecamatan>> {
                            override fun onChanged(districts: List<com.example.tes.model.Kecamatan>) {
                                districts.find { it.nama.contains(districtName, true) || districtName.contains(it.nama, true) }?.let { kec ->
                                    binding.spinnerDashKec.setText(kec.nama, false)
                                    binding.tilDashKel.visibility = View.VISIBLE
                                    viewModel.fetchVillages(kec.id)

                                    viewModel.villages.observe(this@DashboardActivity, object : Observer<List<com.example.tes.model.Kelurahan>> {
                                        override fun onChanged(villages: List<com.example.tes.model.Kelurahan>) {
                                            villages.find { it.nama.contains(villageName, true) || villageName.contains(it.nama, true) }?.let { kel ->
                                                binding.spinnerDashKel.setText(kel.nama, false)
                                                if (zipCode.isNotEmpty()) binding.etDashZip.setText(zipCode)
                                                else binding.etDashZip.setText(kel.kodePos)
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

        boundaryOverlay?.let { map.overlays.remove(it) }
        boundaryOverlay = FolderOverlay()

        try {
            val jsonObject = geoJsonElement.asJsonObject
            val type = jsonObject.get("type")?.asString ?: return
            val coordinates = jsonObject.get("coordinates") ?: return

            val strokeColor = android.graphics.Color.parseColor("#2196F3")
            val fillColor = android.graphics.Color.parseColor("#152196F3")
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
            }

            boundaryOverlay?.let {
                map.overlays.add(0, it)
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

        dialogBinding.mapDialog.setTileSource(TileSourceFactory.MAPNIK)
        dialogBinding.mapDialog.zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
        dialogBinding.mapDialog.setMultiTouchControls(true)

        val mapController = dialogBinding.mapDialog.controller
        mapController.setZoom(binding.dashMap.zoomLevelDouble)
        val currentCenter = binding.dashMap.mapCenter as GeoPoint
        mapController.setCenter(currentCenter)

        val provVal = binding.spinnerDashProv.text.toString()
        val kabVal = binding.spinnerDashKab.text.toString()
        val kecVal = binding.spinnerDashKec.text.toString()
        val kelVal = binding.spinnerDashKel.text.toString()

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

        val provincesObserver = Observer<List<com.example.tes.model.Provinsi>> { list ->
            dialogBinding.spinnerDialogProvinsi.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, list.map { it.nama }))
        }
        val regenciesObserver = Observer<List<com.example.tes.model.Kabupaten>> { list ->
            dialogBinding.spinnerDialogKabupaten.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, list.map { it.nama }))
        }
        val districtsObserver = Observer<List<com.example.tes.model.Kecamatan>> { list ->
            dialogBinding.spinnerDialogKecamatan.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, list.map { it.nama }))
        }
        val villagesObserver = Observer<List<com.example.tes.model.Kelurahan>> { list ->
            dialogBinding.spinnerDialogKelurahan.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, list.map { it.nama }))
        }

        viewModel.provinces.value?.let { provincesObserver.onChanged(it) }
        viewModel.regencies.value?.let { regenciesObserver.onChanged(it) }
        viewModel.districts.value?.let { districtsObserver.onChanged(it) }
        viewModel.villages.value?.let { villagesObserver.onChanged(it) }

        viewModel.provinces.observe(this, provincesObserver)
        viewModel.regencies.observe(this, regenciesObserver)
        viewModel.districts.observe(this, districtsObserver)
        viewModel.villages.observe(this, villagesObserver)

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

        var dialogMarker: Marker? = null
        if (currentLatitude != null && currentLongitude != null) {
            dialogMarker = Marker(dialogBinding.mapDialog)
            dialogMarker.position = GeoPoint(currentLatitude!!, currentLongitude!!)
            dialogMarker.icon = ContextCompat.getDrawable(this, R.drawable.ic_location_pin)
            dialogBinding.mapDialog.overlays.add(dialogMarker)
            dialogBinding.tvDialogCoords.text = "Koordinat: $currentLatitude, $currentLongitude"
        }

        val hasBoundary = boundaryOverlay != null
        if (hasBoundary) {
            binding.dashMap.overlays.remove(boundaryOverlay)
            dialogBinding.mapDialog.overlays.add(0, boundaryOverlay)
            dialogBinding.mapDialog.invalidate()
        }

        var selectedGeoPoint: GeoPoint? = if (currentLatitude != null && currentLongitude != null) GeoPoint(currentLatitude!!, currentLongitude!!) else null

        val receive = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                selectedGeoPoint = p
                dialogBinding.tvDialogCoords.text = "Koordinat: ${p.latitude}, ${p.longitude}"
                if (dialogMarker == null) {
                    dialogMarker = Marker(dialogBinding.mapDialog)
                    dialogMarker?.icon = ContextCompat.getDrawable(this@DashboardActivity, R.drawable.ic_location_pin)
                    dialogBinding.mapDialog.overlays.add(dialogMarker)
                }
                dialogMarker?.position = p
                dialogMarker?.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                dialogBinding.mapDialog.invalidate()
                return true
            }
            override fun longPressHelper(p: GeoPoint): Boolean = false
        }
        dialogBinding.mapDialog.overlays.add(MapEventsOverlay(receive))

        val dialogLocationObserver = Observer<com.example.tes.model.NominatimResponse?> { result ->
            result?.let {
                val gp = GeoPoint(it.lat.toDouble(), it.lon.toDouble())
                selectedGeoPoint = gp
                dialogBinding.tvDialogCoords.text = "Koordinat: ${gp.latitude}, ${gp.longitude}"
                if (dialogMarker == null) {
                    dialogMarker = Marker(dialogBinding.mapDialog)
                    dialogMarker?.icon = ContextCompat.getDrawable(this, R.drawable.ic_location_pin)
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

        dialogBinding.btnCloseDialog.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnDialogMyLocation.setOnClickListener {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                    location?.let {
                        val gp = GeoPoint(it.latitude, it.longitude)
                        dialogBinding.mapDialog.controller.animateTo(gp)
                        dialogBinding.mapDialog.controller.setZoom(17.0)

                        selectedGeoPoint = gp
                        dialogBinding.tvDialogCoords.text = "Koordinat: ${gp.latitude}, ${gp.longitude}"
                        if (dialogMarker == null) {
                            dialogMarker = Marker(dialogBinding.mapDialog)
                            dialogMarker?.icon = ContextCompat.getDrawable(this, R.drawable.ic_location_pin)
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

        dialogBinding.btnConfirmLocation.setOnClickListener {
            selectedGeoPoint?.let { gp ->
                updateLocationOnMap(gp)
            }
            val prov = dialogBinding.spinnerDialogProvinsi.text.toString()
            val kab = dialogBinding.spinnerDialogKabupaten.text.toString()
            val kec = dialogBinding.spinnerDialogKecamatan.text.toString()
            val kel = dialogBinding.spinnerDialogKelurahan.text.toString()

            binding.spinnerDashProv.setText(prov, false)
            if (prov.isNotEmpty()) {
                binding.tilDashKab.visibility = View.VISIBLE
                binding.spinnerDashKab.setText(kab, false)
            }
            if (kab.isNotEmpty()) {
                binding.tilDashKec.visibility = View.VISIBLE
                binding.spinnerDashKec.setText(kec, false)
            }
            if (kec.isNotEmpty()) {
                binding.tilDashKel.visibility = View.VISIBLE
                binding.spinnerDashKel.setText(kel, false)
            }

            if (selectedZipCode.isNotEmpty()) {
                binding.etDashZip.setText(selectedZipCode)
            }
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            viewModel.provinces.removeObserver(provincesObserver)
            viewModel.regencies.removeObserver(regenciesObserver)
            viewModel.districts.removeObserver(districtsObserver)
            viewModel.villages.removeObserver(villagesObserver)
            viewModel.searchLocationResult.removeObserver(dialogLocationObserver)

            dialogBinding.mapDialog.overlays.clear()

            if (hasBoundary) {
                binding.dashMap.overlays.add(0, boundaryOverlay)
                binding.dashMap.invalidate()
            }
            viewModel.searchLocationResult.value?.let { res ->
                drawBoundaryPolygon(res.geojson, binding.dashMap)
            }
        }

        dialog.show()
    }

    private fun validateAndUpdateProfile() {
        val nik = binding.etDashNikInput.text.toString().trim()
        val province = binding.spinnerDashProv.text.toString().trim()
        val regency = binding.spinnerDashKab.text.toString().trim()
        val district = binding.spinnerDashKec.text.toString().trim()
        val village = binding.spinnerDashKel.text.toString().trim()
        val zip = binding.etDashZip.text.toString().trim()
        val address = binding.etDashAddressInput.text.toString().trim()

        val errors = mutableListOf<String>()
        if (nik.length != 16) {
            errors.add("NIK (Harus 16 digit)")
            binding.etDashNikInput.error = "NIK harus 16 digit"
        }
        if (province.isEmpty() || regency.isEmpty() || district.isEmpty() || village.isEmpty()) {
            errors.add("Pilihan Wilayah Belum Lengkap")
        }
        if (address.length < 10) {
            errors.add("Alamat Lengkap (Min. 10 karakter)")
            binding.etDashAddressInput.error = "Alamat terlalu pendek"
        }

        if (errors.isNotEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Lengkapi Data")
                .setMessage(errors.joinToString("\n") { "• $it" })
                .setPositiveButton("OK", null)
                .show()
            return
        }

        showCustomConfirmationDialog(nik, province, regency, district, village, zip, address)
    }

    private fun showCustomConfirmationDialog(
        nik: String,
        province: String,
        regency: String,
        district: String,
        village: String,
        zip: String,
        address: String
    ) {
        val dialog = android.app.Dialog(this)
        val dialogBinding = com.example.tes.databinding.DialogConfirmationBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        // Set Dialog properties to be a medium-sized popup
        dialog.window?.let { window ->
            window.setBackgroundDrawableResource(android.R.color.transparent)
            val params = window.attributes
            params.width = (resources.displayMetrics.widthPixels * 0.9).toInt() // 90% of screen width
            params.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            window.attributes = params
        }

        // Populate Data
        dialogBinding.tvConfirmName.text = userName
        dialogBinding.tvConfirmNik.text = "NIK: $nik"
        dialogBinding.tvConfirmPhone.text = userPhone
        dialogBinding.tvConfirmRegion.text = "$village, $district, $regency, $province"
        dialogBinding.tvConfirmAddress.text = address
        dialogBinding.tvConfirmCoords.text = "GPS: $currentLatitude, $currentLongitude"

        dialogBinding.btnConfirmYes.setOnClickListener {
            dialog.dismiss()
            performUpdateProfile(nik, province, regency, district, village, zip, address)
        }

        dialogBinding.btnConfirmNo.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun performUpdateProfile(
        nik: String,
        province: String,
        regency: String,
        district: String,
        village: String,
        zip: String,
        address: String
    ) {
        showLoading()

        lifecycleScope.launch {
            try {
                val provinceId = viewModel.provinces.value?.find { it.nama == province }?.id
                val regencyId = viewModel.regencies.value?.find { it.nama == regency }?.id
                val districtId = viewModel.districts.value?.find { it.nama == district }?.id
                val villageId = viewModel.villages.value?.find { it.nama == village }?.id

                val request = RegistrasiRequest(
                    namaLengkap = userName,
                    nik = nik,
                    nomorHp = userPhone,
                    alamatLengkap = address,
                    provinsiId = provinceId,
                    provinsiNama = province,
                    kabupatenId = regencyId,
                    kabupatenNama = regency,
                    kecamatanId = districtId,
                    kecamatanNama = district,
                    kelurahanId = villageId,
                    kelurahanNama = village,
                    kodePos = zip,
                    password = "", // Not updated in backend
                    latitude = currentLatitude,
                    longitude = currentLongitude
                )

                val response = ApiClient.apiService.updateRegistrasi("Bearer $token", userId, request)
                if (response.isSuccessful && response.body()?.status == true) {
                    Toast.makeText(this@DashboardActivity, "Data profil berhasil dilengkapi!", Toast.LENGTH_SHORT).show()
                    fetchSessionDetails()
                } else {
                    showContentForForm()
                    val errorMsg = response.errorBody()?.string()?.let {
                        try { JSONObject(it).optString("message") } catch (e: Exception) { null }
                    } ?: response.message() ?: "Gagal memperbarui profil"
                    AlertDialog.Builder(this@DashboardActivity)
                        .setTitle("Gagal Menyimpan")
                        .setMessage(errorMsg)
                        .setPositiveButton("OK", null)
                        .show()
                }
            } catch (e: Exception) {
                showContentForForm()
                AlertDialog.Builder(this@DashboardActivity)
                    .setTitle("Koneksi Error")
                    .setMessage(e.localizedMessage ?: "Terjadi kesalahan koneksi.")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    private fun showContentForForm() {
        binding.layoutDashboardLoading.visibility = View.GONE
        binding.layoutDashboardError.visibility = View.GONE
        binding.layoutProfileComplete.visibility = View.GONE
        binding.layoutProfileIncomplete.visibility = View.VISIBLE
        binding.scrollDashboardContent.visibility = View.VISIBLE
        binding.btnToolbarLogout.visibility = View.VISIBLE
    }

    // STATE 2: Profile Complete Setup
    private fun showCompleteProfile(profile: com.example.tes.model.UserProfile?) {
        binding.layoutDashboardLoading.visibility = View.GONE
        binding.layoutDashboardError.visibility = View.GONE
        binding.layoutProfileIncomplete.visibility = View.GONE
        binding.layoutProfileComplete.visibility = View.VISIBLE
        binding.scrollDashboardContent.visibility = View.VISIBLE
        binding.btnToolbarLogout.visibility = View.VISIBLE

        // Profile details
        binding.tvDashNik.text = profile?.nik ?: "-"
        binding.tvDashPhone.text = profile?.nomorHp ?: "-"
        binding.tvDashProvince.text = profile?.provinsiNama ?: "-"
        binding.tvDashKabupaten.text = profile?.kabupatenNama ?: "-"
        binding.tvDashKecamatan.text = profile?.kecamatanNama ?: "-"
        binding.tvDashKelurahan.text = profile?.kelurahanNama ?: profile?.kelurahan?.nama ?: profile?.desaNama ?: profile?.desa?.nama ?: "-"
        binding.tvDashAddress.text = profile?.alamatLengkap ?: "-"

        // Show location on map
        val lat = profile?.latitude
        val lon = profile?.longitude
        if (lat != null && lon != null) {
            val geoPoint = GeoPoint(lat, lon)
            binding.dashMapComplete.setTileSource(TileSourceFactory.MAPNIK)
            binding.dashMapComplete.setMultiTouchControls(true)
            binding.dashMapComplete.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            
            val mapController = binding.dashMapComplete.controller
            mapController.setZoom(18.0)
            mapController.setCenter(geoPoint)

            val marker = Marker(binding.dashMapComplete)
            marker.position = geoPoint
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            marker.title = "Lokasi Terdaftar"
            marker.icon = ContextCompat.getDrawable(this, R.drawable.ic_location_pin)
            binding.dashMapComplete.overlays.clear()
            binding.dashMapComplete.overlays.add(marker)
            binding.dashMapComplete.invalidate()

            // Disable interaction to make it look like a preview
            binding.dashMapComplete.setOnTouchListener { _, _ -> true }
        }
    }

    private fun showLoading() {
        binding.layoutDashboardLoading.visibility = View.VISIBLE
        binding.layoutDashboardError.visibility = View.GONE
        binding.scrollDashboardContent.visibility = View.GONE
        binding.btnToolbarLogout.visibility = View.GONE
    }

    private fun showError(message: String) {
        binding.layoutDashboardLoading.visibility = View.GONE
        binding.scrollDashboardContent.visibility = View.GONE
        binding.btnToolbarLogout.visibility = View.GONE
        binding.tvDashboardErrorMsg.text = message
        binding.layoutDashboardError.visibility = View.VISIBLE
    }

    private fun performLogout() {
        val prefs = getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        prefs.edit().remove("jwt_token").apply()
        navigateToWelcome()
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun navigateToWelcome() {
        val intent = Intent(this, WelcomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    override fun onResume() {
        super.onResume()
        if (binding.layoutProfileIncomplete.visibility == View.VISIBLE) {
            binding.dashMap.onResume()
        } else if (binding.layoutProfileComplete.visibility == View.VISIBLE) {
            binding.dashMapComplete.onResume()
        }
    }

    override fun onPause() {
        super.onPause()
        if (binding.layoutProfileIncomplete.visibility == View.VISIBLE) {
            binding.dashMap.onPause()
        } else if (binding.layoutProfileComplete.visibility == View.VISIBLE) {
            binding.dashMapComplete.onPause()
        }
    }
}
