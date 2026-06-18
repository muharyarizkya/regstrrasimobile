package com.example.tes.models

import kotlinx.serialization.Serializable

@Serializable
data class Provinsi(
    val id: Int,
    val nama: String
)

@Serializable
data class Kabupaten(
    val id: Int,
    val provinsi_id: Int,
    val nama: String
)

@Serializable
data class Kota(
    val id: Int,
    val kabupaten_id: Int,
    val nama: String
)
