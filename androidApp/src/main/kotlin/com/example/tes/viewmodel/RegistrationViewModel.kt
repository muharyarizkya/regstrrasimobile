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

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> get() = _errorMessage

    fun fetchProvinces() {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val response = ApiClient.apiService.getProvinsi()
                if (response.isSuccessful) {
                    _provinces.value = response.body()?.data ?: emptyList()
                } else {
                    _errorMessage.value = "Gagal mengambil data provinsi"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
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
                    _errorMessage.value = "Gagal mengambil data kabupaten"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Error: ${e.message}"
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
                    _errorMessage.value = "Gagal mengambil data kecamatan"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Error: ${e.message}"
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
                    _errorMessage.value = "Gagal mengambil data kelurahan"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
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
        address: String
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
                    kodePos = zipCode
                )

                val response = ApiClient.apiService.postRegistrasi(request)
                if (response.isSuccessful && response.body()?.status == true) {
                    _registrationSuccess.value = true
                } else {
                    _errorMessage.value = response.body()?.message ?: "Gagal mengirim data registrasi"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
