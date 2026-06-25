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

    @GET("registrasi")
    suspend fun getRegistrasi(): Response<List<RegistrasiItem>>

    @POST("registrasi")
    suspend fun postRegistrasi(
        @Body request: RegistrasiRequest
    ): Response<RegistrasiResponse>

    @PUT("registrasi/{id}")
    suspend fun updateRegistrasi(
        @Header("Authorization") token: String,
        @Path("id") id: Int,
        @Body request: RegistrasiRequest
    ): Response<RegistrasiResponse>

    @POST("login")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<LoginResponse>

    @GET("auth/me")
    suspend fun getMe(
        @Header("Authorization") token: String
    ): Response<MeResponse>

    @GET("https://nominatim.openstreetmap.org/search")
    suspend fun searchLocation(
        @Query("q") query: String,
        @Header("User-Agent") userAgent: String,
        @Query("polygon_geojson") polygonGeoJson: Int = 1,
        @Query("format") format: String = "json",
        @Query("limit") limit: Int = 1
    ): Response<List<NominatimResponse>>

    @GET("https://nominatim.openstreetmap.org/reverse")
    suspend fun reverseGeocode(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Header("User-Agent") userAgent: String,
        @Query("polygon_geojson") polygonGeoJson: Int = 1,
        @Query("format") format: String = "jsonv2",
        @Query("addressdetails") addressDetails: Int = 1
    ): Response<NominatimResponse>
}

object ApiClient {
    private const val BASE_URL = "http://172.16.121.142:8000/api/"

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
