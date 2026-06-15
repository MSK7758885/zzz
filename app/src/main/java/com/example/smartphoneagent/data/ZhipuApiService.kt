package com.example.smartphoneagent.data

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface ZhipuApiService {

    @POST("api/paas/v4/chat/completions")
    suspend fun chatCompletion(
        @Header("Authorization") authorization: String,
        @Body request: ZhipuRequest
    ): ZhipuResponse
}
