package com.example.scannerqrcode

import com.example.scannerqrcode.model.DecryptorResponse
import com.example.scannerqrcode.model.UserListResponse
import com.example.scannerqrcode.model.ValidateResponse
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface ApiService {

    @POST(BuildConfig.DECRYPTOR)
    suspend fun decryptor(@Body requestBody: RequestBody): Response<DecryptorResponse>

    @POST(BuildConfig.VALIDATOR)
    suspend fun validator(@Body requestBody: RequestBody): Response<ValidateResponse>

    @GET(BuildConfig.USER_LIST)
    suspend fun getUserList(): Response<UserListResponse>
}