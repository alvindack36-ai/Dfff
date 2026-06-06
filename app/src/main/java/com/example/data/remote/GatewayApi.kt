package com.example.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface GatewayApi {

    @POST("device-connect")
    suspend fun connectDevice(@Body request: BaseRequest): Response<CommonResponse>

    @POST("device-heartbeat")
    suspend fun sendHeartbeat(@Body request: HeartbeatRequest): Response<CommonResponse>

    @POST("device-poll")
    suspend fun pollDevice(@Body request: BaseRequest): Response<PollResponse>

    @POST("sms-sent")
    suspend fun reportSmsSent(@Body request: SmsStatusRequest): Response<CommonResponse>

    @POST("sms-failed")
    suspend fun reportSmsFailed(@Body request: SmsStatusRequest): Response<CommonResponse>

    @POST("incoming-sms")
    suspend fun reportIncomingSms(@Body request: IncomingSmsRequest): Response<CommonResponse>
}
