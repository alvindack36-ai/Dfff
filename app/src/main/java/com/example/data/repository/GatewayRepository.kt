package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.local.*
import com.example.data.remote.*
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GatewayRepository @Inject constructor(
    private val context: Context,
    private val api: GatewayApi,
    private val appDatabase: AppDatabase,
    private val preferencesManager: PreferencesManager,
    private val moshi: Moshi
) {
    private val messageDao = appDatabase.messageDao()
    private val outboxDao = appDatabase.outboxDao()

    companion object {
        private const val TAG = "GatewayRepository"
    }

    val allMessages: Flow<List<MessageEntity>> = messageDao.getAllMessages()
    
    fun getMessagesByType(type: String): Flow<List<MessageEntity>> {
        return messageDao.getMessagesByType(type)
    }

    fun getSentToday(): Flow<Int> = messageDao.getSentCountToday()
    fun getFailedToday(): Flow<Int> = messageDao.getFailedCountToday()
    fun getReceivedToday(): Flow<Int> = messageDao.getReceivedCountToday()

    suspend fun clearHistory() {
        messageDao.clearHistory()
    }

    suspend fun getMessageById(messageId: String): MessageEntity? {
        return messageDao.getMessageById(messageId)
    }

    suspend fun saveLocalMessage(msg: MessageEntity) {
        messageDao.insertMessage(msg)
    }

    suspend fun updateLocalMessage(msg: MessageEntity) {
        messageDao.updateMessage(msg)
    }

    suspend fun connectDevice(apiBase: String, deviceId: String, token: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Momentarily stage credentials
            val prevApi = preferencesManager.apiBase
            val prevId = preferencesManager.deviceId
            val prevToken = preferencesManager.deviceToken
            
            preferencesManager.saveCredentials(apiBase, deviceId, token)

            val request = BaseRequest(
                deviceId = deviceId,
                deviceToken = token,
                osVersion = android.os.Build.VERSION.RELEASE,
                deviceModel = android.os.Build.MODEL,
                deviceBrand = android.os.Build.BRAND,
                appVersion = "1.1.0"
            )
            val response = api.connectDevice(request)

            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                // Restore
                preferencesManager.saveCredentials(prevApi, prevId ?: "", prevToken ?: "")
                Result.failure(Exception("Connection failed: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting device", e)
            Result.failure(e)
        }
    }

    suspend fun sendHeartbeat(
        battery: Int,
        signal: Int,
        charging: Boolean,
        networkType: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val devId = preferencesManager.deviceId ?: return@withContext Result.failure(Exception("Device not paired"))
        val devToken = preferencesManager.deviceToken ?: return@withContext Result.failure(Exception("Device not paired"))

        try {
            val req = HeartbeatRequest(
                deviceId = devId,
                deviceToken = devToken,
                batteryLevel = battery,
                signalStrength = signal,
                charging = charging,
                networkType = networkType,
                osVersion = android.os.Build.VERSION.RELEASE,
                deviceModel = android.os.Build.MODEL,
                deviceBrand = android.os.Build.BRAND,
                appVersion = "1.1.0"
            )
            val response = api.sendHeartbeat(req)
            if (response.isSuccessful) {
                preferencesManager.lastHeartbeat = System.currentTimeMillis()
                Result.success(Unit)
            } else {
                Result.failure(Exception("Heartbeat failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Heartbeat failed with exception", e)
            Result.failure(e)
        }
    }

    suspend fun pollDevice(): List<SmsTask> = withContext(Dispatchers.IO) {
        val devId = preferencesManager.deviceId ?: return@withContext emptyList()
        val devToken = preferencesManager.deviceToken ?: return@withContext emptyList()

        try {
            val response = api.pollDevice(
                BaseRequest(
                    deviceId = devId,
                    deviceToken = devToken,
                    osVersion = android.os.Build.VERSION.RELEASE,
                    deviceModel = android.os.Build.MODEL,
                    deviceBrand = android.os.Build.BRAND,
                    appVersion = "1.1.0"
                )
            )
            if (response.isSuccessful) {
                response.body()?.messages ?: emptyList()
            } else {
                Log.e(TAG, "Device polling error: ${response.code()}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Device polling failed with exception", e)
            emptyList()
        }
    }

    suspend fun reportSmsSent(messageId: String, simUsed: String) = withContext(Dispatchers.IO) {
        val devId = preferencesManager.deviceId ?: return@withContext
        val devToken = preferencesManager.deviceToken ?: return@withContext

        val req = SmsStatusRequest(devId, devToken, messageId, simUsed = simUsed)
        try {
            val response = api.reportSmsSent(req)
            if (!response.isSuccessful) {
                queueOutboxEvent("sms-sent", req)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Network failed for reportsmsSent, queueing offline", e)
            queueOutboxEvent("sms-sent", req)
        }
    }

    suspend fun reportSmsFailed(messageId: String, errorReason: String) = withContext(Dispatchers.IO) {
        val devId = preferencesManager.deviceId ?: return@withContext
        val devToken = preferencesManager.deviceToken ?: return@withContext

        val req = SmsStatusRequest(devId, devToken, messageId, errorReason = errorReason)
        try {
            val response = api.reportSmsFailed(req)
            if (!response.isSuccessful) {
                queueOutboxEvent("sms-failed", req)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Network failed for reportSmsFailed, queueing offline", e)
            queueOutboxEvent("sms-failed", req)
        }
    }

    suspend fun reportIncomingSms(sender: String, body: String, timestamp: Long, simSlot: Int) = withContext(Dispatchers.IO) {
        val devId = preferencesManager.deviceId ?: return@withContext
        val devToken = preferencesManager.deviceToken ?: return@withContext

        val req = IncomingSmsRequest(devId, devToken, sender, body, timestamp, simSlot)
        try {
            val response = api.reportIncomingSms(req)
            if (!response.isSuccessful) {
                queueOutboxEvent("incoming-sms", req)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Network failed for reportIncomingSms, queueing offline", e)
            queueOutboxEvent("incoming-sms", req)
        }
    }

    private suspend fun <T : Any> queueOutboxEvent(endpoint: String, payload: T) {
        try {
            val adapter = moshi.adapter(payload.javaClass)
            val json = adapter.toJson(payload)
            val event = OutboxEventEntity(
                endpoint = endpoint,
                payloadJson = json,
                attempts = 0,
                nextRetryAt = System.currentTimeMillis()
            )
            outboxDao.insertEvent(event)
            Log.i(TAG, "Queued offline event for $endpoint")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to queue outbox event", e)
        }
    }

    suspend fun syncPendingOutbox(): Int = withContext(Dispatchers.IO) {
        val events = outboxDao.getPendingEvents()
        if (events.isEmpty()) return@withContext 0

        var successfulSyncs = 0
        Log.i(TAG, "Syncing offline-first database queue: found ${events.size} items")

        for (event in events) {
            var success = false
            try {
                when (event.endpoint) {
                    "sms-sent" -> {
                        val payload = moshi.adapter(SmsStatusRequest::class.java).fromJson(event.payloadJson)
                        if (payload != null) {
                            val response = api.reportSmsSent(payload)
                            success = response.isSuccessful
                        }
                    }
                    "sms-failed" -> {
                        val payload = moshi.adapter(SmsStatusRequest::class.java).fromJson(event.payloadJson)
                        if (payload != null) {
                            val response = api.reportSmsFailed(payload)
                            success = response.isSuccessful
                        }
                    }
                    "incoming-sms" -> {
                        val payload = moshi.adapter(IncomingSmsRequest::class.java).fromJson(event.payloadJson)
                        if (payload != null) {
                            val response = api.reportIncomingSms(payload)
                            success = response.isSuccessful
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error synchronizing outbox entry ${event.id}: ${e.message}")
            }

            if (success) {
                outboxDao.deleteEvent(event)
                successfulSyncs++
            } else {
                val nextDelay = when (event.attempts) {
                    0 -> 5000L
                    1 -> 15000L
                    2 -> 45000L
                    else -> 120000L
                }
                outboxDao.updateEvent(
                    event.copy(
                        attempts = event.attempts + 1,
                        nextRetryAt = System.currentTimeMillis() + nextDelay
                    )
                )
            }
        }
        successfulSyncs
    }
}
