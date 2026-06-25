package com.example.tes.api

import com.example.tes.models.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class RegistrationApi {
    private val baseUrl = "http://172.16.121.104:8000/api"
    
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                useAlternativeNames = false
            })
        }
    }

    suspend fun getProvinsi(): List<Provinsi> {
        return client.get("$baseUrl/wilayah/provinsi").body<ProvinsiResponse>().data
    }

    suspend fun getKabupaten(provinsiId: Int): List<Kabupaten> {
        return client.get("$baseUrl/wilayah/kabupaten/$provinsiId").body<KabupatenResponse>().data
    }

    suspend fun getKota(kabupatenId: Int): List<Kota> {
        return client.get("$baseUrl/wilayah/kecamatan/$kabupatenId").body<KotaResponse>().data
    }

    suspend fun register(request: RegistrationRequest): HttpResponse {
        return client.post("$baseUrl/registrasi") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun login(request: LoginRequest): LoginResponse {
        return client.post("$baseUrl/login") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun getMe(token: String): MeResponse {
        return client.get("$baseUrl/auth/me") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }.body()
    }
}
