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

@Serializable
data class ProvinsiResponse(
    val status: Boolean,
    val message: String,
    val data: List<Provinsi>
)

@Serializable
data class KabupatenResponse(
    val status: Boolean,
    val message: String,
    val data: List<Kabupaten>
)

@Serializable
data class KotaResponse(
    val status: Boolean,
    val message: String,
    val data: List<Kota>
)
