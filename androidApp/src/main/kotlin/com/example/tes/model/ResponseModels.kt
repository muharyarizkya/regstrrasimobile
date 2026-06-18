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
    @SerializedName("kode_pos") val kodePos: String? = null
)

// Response Registrasi
data class RegistrasiResponse(
    val status: Boolean,
    val message: String,
    val data: RegistrasiRequest?
)

// Response Cek NIK
data class CekNikResponse(
    val status: Boolean,
    val terdaftar: Boolean,
    val message: String
)
