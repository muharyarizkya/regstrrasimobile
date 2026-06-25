package com.example.tes.model

import com.google.gson.annotations.SerializedName

// Model Wilayah
data class Provinsi(
    val id: Int,
    val kode: String,
    val nama: String
) {
    override fun toString(): String = nama
}

data class Kabupaten(
    val id: Int,
    val kode: String,
    @SerializedName("provinsi_id") val provinsiId: Int,
    val nama: String
) {
    override fun toString(): String = nama
}

data class Kecamatan(
    val id: Int,
    val kode: String,
    @SerializedName("kabupaten_id") val kabupatenId: Int,
    val nama: String
) {
    override fun toString(): String = nama
}

// Data class Kelurahan
data class Kelurahan(
    val id: Int,
    val kode: String,
    @SerializedName("kecamatan_id") val kecamatanId: Int,
    val nama: String,
    @SerializedName("kode_pos") val kodePos: String
) {
    override fun toString(): String = nama
}

// Wrapper Response Wilayah
data class WilayahResponse<T>(
    val status: Boolean,
    val message: String,
    val data: List<T>?
)

// Data class Request Registrasi
data class RegistrasiRequest(
    @SerializedName("nama_lengkap") val namaLengkap: String,
    val nik: String,
    @SerializedName("nomor_hp") val nomorHp: String,
    @SerializedName("alamat_lengkap") val alamatLengkap: String,
    
    @SerializedName("provinsi_id") val provinsiId: Int? = null,
    @SerializedName("provinsi_nama") val provinsiNama: String? = null,
    @SerializedName("kabupaten_id") val kabupatenId: Int? = null,
    @SerializedName("kabupaten_nama") val kabupatenNama: String? = null,
    @SerializedName("kecamatan_id") val kecamatanId: Int? = null,
    @SerializedName("kecamatan_nama") val kecamatanNama: String? = null,
    @SerializedName("kelurahan_id") val kelurahanId: Int? = null,
    @SerializedName("kelurahan_nama") val kelurahanNama: String? = null,
    @SerializedName("desa_nama") val desaNama: String? = null,
    val kelurahan: Kelurahan? = null,
    val desa: Kelurahan? = null,
    @SerializedName("kode_pos") val kodePos: String? = null,
    val password: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
)

// Response Registrasi
data class RegistrasiResponse(
    val status: Boolean,
    val message: String,
    val data: RegistrasiRequest?
)

data class LoginRequest(
    @SerializedName("nomor_hp") val nomorHp: String,
    val password: String
)

data class LoginResponse(
    val status: Boolean,
    val message: String,
    val token: String? = null,
    val user: UserProfile? = null
)

data class UserProfile(
    val id: Int,
    @SerializedName("nama_lengkap") val namaLengkap: String,
    val nik: String,
    @SerializedName("nomor_hp") val nomorHp: String,
    @SerializedName("provinsi_nama") val provinsiNama: String? = null,
    @SerializedName("kabupaten_nama") val kabupatenNama: String? = null,
    @SerializedName("kecamatan_nama") val kecamatanNama: String? = null,
    @SerializedName("kelurahan_nama") val kelurahanNama: String? = null,
    @SerializedName("desa_nama") val desaNama: String? = null,
    val kelurahan: Kelurahan? = null,
    val desa: Kelurahan? = null,
    @SerializedName("alamat_lengkap") val alamatLengkap: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
)

data class DebugInfo(
    @SerializedName("raw_payload") val rawPayload: RawPayload,
    @SerializedName("header_type") val headerType: String,
    val algorithm: String,
    @SerializedName("issued_at") val issuedAt: String,
    @SerializedName("expired_at") val expiredAt: String,
    @SerializedName("time_remaining_seconds") val timeRemainingSeconds: Long
)

data class RawPayload(
    val sub: Int,
    val nama: String,
    @SerializedName("nomor_hp") val nomorHp: String,
    val iat: Long,
    val exp: Long
)

data class MeResponse(
    val status: Boolean,
    val message: String,
    val debug: DebugInfo? = null,
    val user: UserProfile? = null
)

// Response Cek NIK
data class CekNikResponse(
    val status: Boolean,
    val terdaftar: Boolean,
    val message: String
)

// Data class untuk list pendaftar di peta
data class RegistrasiItem(
    val id: Int,
    @SerializedName("nama_lengkap") val namaLengkap: String,
    @SerializedName("alamat_lengkap") val alamatLengkap: String,
    val latitude: Double?,
    val longitude: Double?
)

// Data class untuk response Nominatim OSM
data class NominatimResponse(
    val lat: String,
    val lon: String,
    @SerializedName("display_name") val displayName: String,
    val geojson: com.google.gson.JsonElement? = null,
    val address: NominatimAddress? = null
)

data class NominatimAddress(
    val province: String? = null,
    val city: String? = null,
    val town: String? = null,
    val village: String? = null,
    val suburb: String? = null,
    val district: String? = null,
    val state: String? = null,
    val postcode: String? = null
)
