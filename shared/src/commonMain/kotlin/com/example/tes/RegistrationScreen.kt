package com.example.tes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.tes.api.RegistrationApi
import com.example.tes.models.*
import kotlinx.coroutines.launch

@Composable
fun RegistrationScreen() {
    val scope = rememberCoroutineScope()
    val api = remember { RegistrationApi() }
    
    var fullName by remember { mutableStateOf("") }
    var nik by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    
    // Address Selection Data
    var provinsiList by remember { mutableStateOf(emptyList<Provinsi>()) }
    var kabupatenList by remember { mutableStateOf(emptyList<Kabupaten>()) }
    var kotaList by remember { mutableStateOf(emptyList<Kota>()) }

    // Selected Address Objects
    var selectedProvinsi by remember { mutableStateOf<Provinsi?>(null) }
    var selectedKabupaten by remember { mutableStateOf<Kabupaten?>(null) }
    var selectedKota by remember { mutableStateOf<Kota?>(null) }
    
    var alamatDetail by remember { mutableStateOf("") }
    var mapLocation by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(false) }
    var isAddressLoading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    // Fetch Provinsi on Start
    LaunchedEffect(Unit) {
        try {
            isAddressLoading = true
            provinsiList = api.getProvinsi()
        } catch (e: Exception) {
            message = "Error fetch provinsi: ${e.message}"
        } finally {
            isAddressLoading = false
        }
    }

    // Fetch Kabupaten when Provinsi changes
    LaunchedEffect(selectedProvinsi) {
        selectedProvinsi?.let {
            try {
                isAddressLoading = true
                selectedKabupaten = null
                selectedKota = null
                kabupatenList = api.getKabupaten(it.id)
            } catch (e: Exception) {
                message = "Error fetch kabupaten: ${e.message}"
            } finally {
                isAddressLoading = false
            }
        } ?: run {
            kabupatenList = emptyList()
            selectedKabupaten = null
            selectedKota = null
        }
    }

    // Fetch Kota when Kabupaten changes
    LaunchedEffect(selectedKabupaten) {
        selectedKabupaten?.let {
            try {
                isAddressLoading = true
                selectedKota = null
                kotaList = api.getKota(it.id)
            } catch (e: Exception) {
                message = "Error fetch kota: ${e.message}"
            } finally {
                isAddressLoading = false
            }
        } ?: run {
            kotaList = emptyList()
            selectedKota = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Form Registrasi",
            style = MaterialTheme.typography.headlineMedium
        )

        if (message.isNotEmpty()) {
            Text(
                text = message, 
                color = if (message.contains("Error")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        OutlinedTextField(
            value = fullName,
            onValueChange = { fullName = it },
            label = { Text("Nama Lengkap") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = nik,
            onValueChange = { nik = it },
            label = { Text("NIK") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        OutlinedTextField(
            value = phoneNumber,
            onValueChange = { phoneNumber = it },
            label = { Text("Nomor HP") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
        )

        HorizontalDivider()

        Text(
            text = "Data Alamat",
            style = MaterialTheme.typography.titleMedium
        )

        if (isAddressLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        // Provinsi Dropdown
        AppDropdown(
            label = "Provinsi",
            options = provinsiList.map { it.nama },
            selectedOption = selectedProvinsi?.nama ?: "",
            onOptionSelected = { name ->
                selectedProvinsi = provinsiList.find { it.nama == name }
            }
        )

        // Kabupaten Dropdown
        AppDropdown(
            label = "Kabupaten",
            options = kabupatenList.map { it.nama },
            selectedOption = selectedKabupaten?.nama ?: "",
            onOptionSelected = { name ->
                selectedKabupaten = kabupatenList.find { it.nama == name }
            },
            enabled = selectedProvinsi != null
        )

        // Kota Dropdown
        AppDropdown(
            label = "Kota",
            options = kotaList.map { it.nama },
            selectedOption = selectedKota?.nama ?: "",
            onOptionSelected = { name ->
                selectedKota = kotaList.find { it.nama == name }
            },
            enabled = selectedKabupaten != null
        )

        // Alamat Tinggal / Detail (Ketik Manual)
        OutlinedTextField(
            value = alamatDetail,
            onValueChange = { alamatDetail = it },
            label = { Text("Alamat Tinggal / Detail (Manual)") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            placeholder = { Text("Contoh: Jl. Merdeka No. 123, RT 01/RW 02") }
        )

        // Pilih Lokasi dari Map
        Button(
            onClick = { 
                mapLocation = "Lat: -6.2000, Lng: 106.8166" 
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Text(if (mapLocation.isEmpty()) "Pilih Titik dari Map" else "Lokasi Terpilih: $mapLocation")
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                scope.launch {
                    isLoading = true
                    try {
                        val request = RegistrationRequest(
                            nama_lengkap = fullName,
                            nik = nik,
                            nomor_hp = phoneNumber,
                            provinsi = selectedProvinsi?.nama ?: "",
                            kabupaten = selectedKabupaten?.nama ?: "",
                            kota = selectedKota?.nama ?: "",
                            alamat_detail = alamatDetail,
                            titik_map = mapLocation.ifEmpty { null }
                        )
                        val response = api.register(request)
                        if (response.status.value in 200..299) {
                            message = "Registrasi Berhasil!"
                        } else {
                            message = "Error: ${response.status.description}"
                        }
                    } catch (e: Exception) {
                        message = "Error: ${e.message}"
                    } finally {
                        isLoading = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading && fullName.isNotEmpty() && nik.isNotEmpty() && 
                      phoneNumber.isNotEmpty() && selectedProvinsi != null && 
                      selectedKabupaten != null && selectedKota != null && 
                      alamatDetail.isNotEmpty()
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text("Daftar Sekarang")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDropdown(
    label: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedOption,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, true).fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { selectionOption ->
                DropdownMenuItem(
                    text = { Text(selectionOption) },
                    onClick = {
                        onOptionSelected(selectionOption)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}
