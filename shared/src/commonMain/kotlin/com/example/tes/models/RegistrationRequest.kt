package com.example.tes.models

import kotlinx.serialization.Serializable

@Serializable
data class RegistrationRequest(
    val nama_lengkap: String,
    val nik: String,
    val nomor_hp: String,
    val provinsi: String,
    val kabupaten: String,
    val kota: String,
    val alamat_detail: String,
    val titik_map: String?
)
