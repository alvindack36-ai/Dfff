package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class BaseRequest(
    @Json(name = "device_id") val deviceId: String,
    @Json(name = "device_token") val deviceToken: String
)

@JsonClass(generateAdapter = true)
data class HeartbeatRequest(
    @Json(name = "device_id") val deviceId: String,
    @Json(name = "device_token") val deviceToken: String,
    @Json(name = "battery_level") val batteryLevel: Int,
    @Json(name = "signal_strength") val signalStrength: Int,
    @Json(name = "charging") val charging: Boolean,
    @Json(name = "network_type") val networkType: String
)

@JsonClass(generateAdapter = true)
data class PollResponse(
    @Json(name = "messages") val messages: List<SmsTask>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class SmsTask(
    @Json(name = "id") val id: String,
    @Json(name = "recipient") val recipient: String,
    @Json(name = "body") val body: String,
    @Json(name = "sim_slot") val simSlot: Int? = null
)

@JsonClass(generateAdapter = true)
data class SmsStatusRequest(
    @Json(name = "device_id") val deviceId: String,
    @Json(name = "device_token") val deviceToken: String,
    @Json(name = "message_id") val messageId: String,
    @Json(name = "sim_used") val simUsed: String? = null,
    @Json(name = "error_reason") val errorReason: String? = null
)

@JsonClass(generateAdapter = true)
data class IncomingSmsRequest(
    @Json(name = "device_id") val deviceId: String,
    @Json(name = "device_token") val deviceToken: String,
    @Json(name = "sender") val sender: String,
    @Json(name = "body") val body: String,
    @Json(name = "received_at") val receivedAt: Long,
    @Json(name = "sim_slot") val simSlot: Int
)

@JsonClass(generateAdapter = true)
data class CommonResponse(
    @Json(name = "success") val success: Boolean? = true,
    @Json(name = "message") val message: String? = null
)
