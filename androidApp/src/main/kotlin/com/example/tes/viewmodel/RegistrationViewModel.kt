package com.example.tes.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tes.model.Kabupaten
import com.example.tes.model.Kecamatan
import com.example.tes.model.Provinsi
import com.example.tes.network.ApiClient
import kotlinx.coroutines.launch

class RegistrationViewModel : ViewModel() {

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> get() = _isLoading

    private val _provinces = MutableLiveData<List<Provinsi>>()
    val provinces: LiveData<List<Provinsi>> get() = _provinces

    private val _regencies = MutableLiveData<List<Kabupaten>>()
    val regencies: LiveData<List<Kabupaten>> get() = _regencies

    private val _districts = MutableLiveData<List<Kecamatan>>()
    val districts: LiveData<List<Kecamatan>> get() = _districts

    private val _villages = MutableLiveData<List<com.example.tes.model.Kelurahan>>()
    val villages: LiveData<List<com.example.tes.model.Kelurahan>> get() = _villages

    private val _registrationSuccess = MutableLiveData<Boolean>()
    val registrationSuccess: LiveData<Boolean> get() = _registrationSuccess

    private val _registrants = MutableLiveData<List<com.example.tes.model.RegistrasiItem>>()
    val registrants: LiveData<List<com.example.tes.model.RegistrasiItem>> get() = _registrants

    private val _searchLocationResult = MutableLiveData<com.example.tes.model.NominatimResponse?>()
    val searchLocationResult: LiveData<com.example.tes.model.NominatimResponse?> get() = _searchLocationResult

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> get() = _errorMessage

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    fun fetchProvinces() {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val response = ApiClient.apiService.getProvinsi()
                if (response.isSuccessful) {
                    _provinces.value = response.body()?.data ?: emptyList()
                } else {
                    _errorMessage.value = "Gagal mengambil data provinsi: ${response.message()}"
                }
            } catch (e: Exception) {
                _errorMessage.value = handleNetworkError(e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun handleNetworkError(e: Exception): String {
        return when (e) {
            is java.net.ConnectException -> "Tidak dapat terhubung ke server. Pastikan server Laravel sudah dijalankan."
            is java.net.SocketTimeoutException -> "Koneksi ke server timeout."
            is java.net.UnknownHostException -> "Server tidak ditemukan. Periksa koneksi internet Anda."
            else -> "Terjadi kesalahan: ${e.localizedMessage}"
        }
    }

    fun fetchRegencies(provinceId: Int) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val response = ApiClient.apiService.getKabupaten(provinceId)
                if (response.isSuccessful) {
                    _regencies.value = response.body()?.data ?: emptyList()
                } else {
                    _errorMessage.value = "Gagal mengambil data kabupaten: ${response.message()}"
                }
            } catch (e: Exception) {
                _errorMessage.value = handleNetworkError(e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun fetchDistricts(regencyId: Int) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val response = ApiClient.apiService.getKecamatan(regencyId)
                if (response.isSuccessful) {
                    _districts.value = response.body()?.data ?: emptyList()
                } else {
                    _errorMessage.value = "Gagal mengambil data kecamatan: ${response.message()}"
                }
            } catch (e: Exception) {
                _errorMessage.value = handleNetworkError(e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun fetchVillages(districtId: Int) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val response = ApiClient.apiService.getKelurahan(districtId)
                if (response.isSuccessful) {
                    _villages.value = response.body()?.data ?: emptyList()
                } else {
                    _errorMessage.value = "Gagal mengambil data kelurahan: ${response.message()}"
                }
            } catch (e: Exception) {
                _errorMessage.value = handleNetworkError(e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun fetchRegistrants() {
        viewModelScope.launch {
            try {
                val response = ApiClient.apiService.getRegistrasi()
                if (response.isSuccessful) {
                    _registrants.value = response.body() ?: emptyList()
                }
            } catch (e: Exception) {
                // Silently ignore or handle
            }
        }
    }

    fun searchLocation(query: String) {
        viewModelScope.launch {
            try {
                val response = ApiClient.apiService.searchLocation(
                    query = query,
                    userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                )
                if (response.isSuccessful && !response.body().isNullOrEmpty()) {
                    _searchLocationResult.value = response.body()?.firstOrNull()
                } else {
                    // Fallback: Drop the most specific segment (e.g., Kelurahan) and try search again
                    val parts = query.split(", ")
                    if (parts.size > 2) {
                        val fallbackQuery = parts.drop(1).joinToString(", ")
                        searchLocation(fallbackQuery)
                    } else {
                        _searchLocationResult.value = null
                    }
                }
            } catch (e: Exception) {
                _searchLocationResult.value = null
            }
        }
    }

    fun reverseGeocode(lat: Double, lon: Double) {
        viewModelScope.launch {
            try {
                val response = ApiClient.apiService.reverseGeocode(
                    lat = lat,
                    lon = lon,
                    userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                )
                if (response.isSuccessful) {
                    _searchLocationResult.value = response.body()
                }
            } catch (e: Exception) {
                // Silently ignore
            }
        }
    }

    fun register(
        nama: String,
        nik: String,
        phone: String,
        province: String,
        regency: String,
        district: String,
        village: String,
        zipCode: String,
        address: String,
        password: String,
        latitude: Double? = null,
        longitude: Double? = null
    ) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val provinceId = _provinces.value?.find { it.nama == province }?.id
                val regencyId = _regencies.value?.find { it.nama == regency }?.id
                val districtId = _districts.value?.find { it.nama == district }?.id
                val villageId = _villages.value?.find { it.nama == village }?.id
 
                val request = com.example.tes.model.RegistrasiRequest(
                    namaLengkap = nama,
                    nik = nik,
                    nomorHp = phone,
                    alamatLengkap = address,
                    provinsiId = provinceId,
                    provinsiNama = province,
                    kabupatenId = regencyId,
                    kabupatenNama = regency,
                    kecamatanId = districtId,
                    kecamatanNama = district,
                    kelurahanId = villageId,
                    kelurahanNama = village,
                    kodePos = zipCode,
                    password = password,
                    latitude = latitude,
                    longitude = longitude
                )

                val response = ApiClient.apiService.postRegistrasi(request)
                if (response.isSuccessful && response.body()?.status == true) {
                    _registrationSuccess.value = true
                } else {
                    val errorMsg = if (response.code() == 422) {
                        val errorBody = response.errorBody()?.string()
                        if (errorBody != null) {
                            try {
                                val json = org.json.JSONObject(errorBody)
                                val errors = json.optJSONObject("errors")
                                if (errors != null) {
                                    val sb = StringBuilder()
                                    val keys = errors.keys()
                                    while (keys.hasNext()) {
                                        val key = keys.next()
                                        val msgArray = errors.getJSONArray(key)
                                        for (i in 0 until msgArray.length()) {
                                            sb.append("• ").append(msgArray.getString(i)).append("\n")
                                        }
                                    }
                                    sb.toString().trim()
                                } else {
                                    json.optString("message", "Validasi gagal")
                                }
                            } catch (e: Exception) {
                                "Terjadi kesalahan validasi pada server."
                            }
                        } else {
                            "Terjadi kesalahan validasi (422)."
                        }
                    } else {
                        response.errorBody()?.string()?.let {
                            try {
                                org.json.JSONObject(it).optString("message")
                            } catch (e: Exception) { null }
                        } ?: response.message() ?: "Gagal mengirim data registrasi"
                    }
                    _errorMessage.value = errorMsg
                }
            } catch (e: Exception) {
                _errorMessage.value = handleNetworkError(e)
            } finally {
                _isLoading.value = false
            }
        }
    }
}
