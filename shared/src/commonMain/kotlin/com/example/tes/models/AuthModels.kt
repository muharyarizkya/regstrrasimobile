package com.example.tes.models

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val nomor_hp: String,
    val password: String
)

@Serializable
data class UserProfile(
    val id: Int,
    val nama_lengkap: String,
    val nik: String,
    val nomor_hp: String,
    val provinsi_nama: String? = null,
    val kabupaten_nama: String? = null,
    val kecamatan_nama: String? = null,
    val kelurahan_nama: String? = null,
    val alamat_lengkap: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val created_at: String? = null,
    val updated_at: String? = null
)

@Serializable
data class LoginResponse(
    val status: Boolean,
    val message: String,
    val token: String? = null,
    val user: UserProfile? = null
)

@Serializable
data class DebugInfo(
    val raw_payload: RawPayload,
    val header_type: String,
    val algorithm: String,
    val issued_at: String,
    val expired_at: String,
    val time_remaining_seconds: Long
)

@Serializable
data class RawPayload(
    val sub: Int,
    val nama: String,
    val nomor_hp: String,
    val iat: Long,
    val exp: Long
)

@Serializable
data class MeResponse(
    val status: Boolean,
    val message: String,
    val debug: DebugInfo? = null,
    val user: UserProfile? = null
)
