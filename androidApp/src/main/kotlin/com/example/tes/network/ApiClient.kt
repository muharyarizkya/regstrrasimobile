package com.example.tes.network

import com.example.tes.model.*
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

interface ApiService {
    @GET("wilayah/provinsi")
    suspend fun getProvinsi(): Response<WilayahResponse<Provinsi>>

    @GET("wilayah/kabupaten/{provinsi_id}")
    suspend fun getKabupaten(
        @Path("provinsi_id") provinsiId: Int
    ): Response<WilayahResponse<Kabupaten>>

    @GET("wilayah/kecamatan/{kabupaten_id}")
    suspend fun getKecamatan(
        @Path("kabupaten_id") kabupatenId: Int
    ): Response<WilayahResponse<Kecamatan>>

    @GET("wilayah/kelurahan/{kecamatan_id}")
    suspend fun getKelurahan(
        @Path("kecamatan_id") kecamatanId: Int
    ): Response<WilayahResponse<Kelurahan>>

    @GET("registrasi/nik/{nik}")
    suspend fun cekNik(
        @Path("nik") nik: String
    ): Response<CekNikResponse>

    @POST("registrasi")
    suspend fun postRegistrasi(
        @Body request: RegistrasiRequest
    ): Response<RegistrasiResponse>
}

object ApiClient {
    private const val BASE_URL = "http://172.16.121.251:8000/api/"

    private val logger = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(logger)
        .build()

    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(okHttpClient)
            .build()
            .create(ApiService::class.java)
    }
}
