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
    private val baseUrl = "https://your-laravel-backend.com/api" // Ganti dengan URL backend Anda
    
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                useAlternativeNames = false
            })
        }
    }

    suspend fun getProvinsi(): List<Provinsi> {
        return client.get("$baseUrl/provinsi").body()
    }

    suspend fun getKabupaten(provinsiId: Int): List<Kabupaten> {
        return client.get("$baseUrl/kabupaten") {
            parameter("provinsi_id", provinsiId)
        }.body()
    }

    suspend fun getKota(kabupatenId: Int): List<Kota> {
        return client.get("$baseUrl/kota") {
            parameter("kabupaten_id", kabupatenId)
        }.body()
    }

    suspend fun register(request: RegistrationRequest): HttpResponse {
        return client.post("$baseUrl/register") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }
}
