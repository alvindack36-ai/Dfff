package com.example.ui.viewmodel

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.MessageEntity
import com.example.data.local.PreferencesManager
import com.example.data.repository.GatewayRepository
import com.example.service.GatewayService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SimCardInfo(
    val subscriptionId: Int,
    val carrierName: String,
    val displayName: String,
    val slotIndex: Int,
    val phoneNumber: String?
)

@HiltViewModel
class GatewayViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: GatewayRepository,
    val prefs: PreferencesManager
) : ViewModel() {

    private val TAG = "GatewayViewModel"

    // Preferences properties exposed reactively
    private val _isPaired = MutableStateFlow(prefs.isPaired)
    val isPaired = _isPaired.asStateFlow()

    private val _apiBaseUrl = MutableStateFlow(prefs.apiBase)
    val apiBaseUrl = _apiBaseUrl.asStateFlow()

    private val _deviceId = MutableStateFlow(prefs.deviceId ?: "")
    val deviceId = _deviceId.asStateFlow()

    private val _deviceToken = MutableStateFlow(prefs.deviceToken ?: "•••••••••")
    val deviceToken = _deviceToken.asStateFlow()

    // Service connection status and heartbeat rates
    val connectionStatus: StateFlow<String> = GatewayService.serviceStatus
    val lastHeartbeatTime: StateFlow<Long> = GatewayService.lastHeartbeatTime
    val networkName: StateFlow<String> = GatewayService.networkName
    val signalStrengthLevel: StateFlow<Int> = GatewayService.signalLevel

    // Active Database stats counts
    val sentCountToday: StateFlow<Int> = repository.getSentToday()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val failedCountToday: StateFlow<Int> = repository.getFailedToday()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val receivedCountToday: StateFlow<Int> = repository.getReceivedToday()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Log records
    val logMessages: StateFlow<List<MessageEntity>> = repository.allMessages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // SIM Cards lists
    private val _simCards = MutableStateFlow<List<SimCardInfo>>(emptyList())
    val simCards = _simCards.asStateFlow()

    private val _selectedSimSubId = MutableStateFlow(prefs.defaultSimSubId)
    val selectedSimSubId = _selectedSimSubId.asStateFlow()

    // Progress connecting status
    private val _isConnecting = MutableStateFlow(false)
    val isConnecting = _isConnecting.asStateFlow()

    private val _connectionError = MutableStateFlow<String?>(null)
    val connectionError = _connectionError.asStateFlow()

    init {
        loadSims()
    }

    fun loadSims() {
        try {
            val systemSubscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            if (systemSubscriptionManager != null) {
                @SuppressLint("MissingPermission")
                val activeList = systemSubscriptionManager.activeSubscriptionInfoList
                if (activeList != null) {
                    val list = activeList.map { info ->
                        SimCardInfo(
                            subscriptionId = info.subscriptionId,
                            carrierName = info.carrierName?.toString() ?: "Carrier",
                            displayName = info.displayName?.toString() ?: "SIM ${info.simSlotIndex + 1}",
                            slotIndex = info.simSlotIndex,
                            phoneNumber = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                "" // System secure restrictions typically prevent phone number retrieval natively, but we specify placeholder
                            } else {
                                @Suppress("DEPRECATION")
                                info.number ?: ""
                            }
                        )
                    }
                    _simCards.value = list
                    Log.i(TAG, "Loaded SIMS: ${list.size}")
                } else {
                    _simCards.value = emptyList()
                    Log.w(TAG, "No active SIM subscription cards detected.")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to inspect subscription card manager", e)
        }
    }

    fun selectSim(subId: Int) {
        prefs.defaultSimSubId = subId
        _selectedSimSubId.value = subId
    }

    fun connectDevicePair(api: String, devId: String, token: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _isConnecting.value = true
            _connectionError.value = null
            
            val result = repository.connectDevice(api.trim(), devId.trim(), token.trim())
            if (result.isSuccess) {
                prefs.saveCredentials(api.trim(), devId.trim(), token.trim())
                _isPaired.value = true
                _apiBaseUrl.value = prefs.apiBase
                _deviceId.value = prefs.deviceId ?: ""
                _deviceToken.value = prefs.deviceToken ?: ""
                
                // Immediately kick-start polling & synchronization
                startGateway()
                onSuccess()
            } else {
                _connectionError.value = result.exceptionOrNull()?.message ?: "Unknown pairing error occurred."
            }
            _isConnecting.value = false
        }
    }

    fun unpair() {
        stopGateway()
        prefs.unpair()
        _isPaired.value = false
        _deviceId.value = ""
        _deviceMutedToken()
    }

    fun setConnectionError(error: String?) {
        _connectionError.value = error
    }

    private fun _deviceMutedToken() {
        _deviceToken.value = "•••••••••"
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun startGateway() {
        val intent = Intent(context, GatewayService::class.java).apply {
            putExtra("action", "START")
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start GatewayService as foreground service", e)
        }
    }

    fun stopGateway() {
        val intent = Intent(context, GatewayService::class.java).apply {
            putExtra("action", "STOP")
        }
        try {
            context.startService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send STOP command to GatewayService", e)
        }
    }

    fun sendTestSms(recipient: String, body: String) {
        viewModelScope.launch(Dispatchers.IO) {
            // Direct mock inject send test action
            val progressMessage = MessageEntity(
                recipient = recipient,
                body = body,
                type = "SENT",
                status = "PENDING",
                simIndex = prefs.defaultSimSubId
            )
            repository.saveLocalMessage(progressMessage)

            // Send standard sendSMS callback logic or dispatch direct task
            try {
                val selectedSubId = prefs.defaultSimSubId
                val smsManager = if (selectedSubId != -1 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                    SmsManager.getSmsManagerForSubscriptionId(selectedSubId)
                } else {
                    SmsManager.getDefault()
                }

                smsManager.sendTextMessage(recipient, null, body, null, null)
                
                // Set sent state
                val successRecord = progressMessage.copy(status = "SENT", updatedAt = System.currentTimeMillis())
                repository.updateLocalMessage(successRecord)
                
                // Post remote report
                repository.reportSmsSent(successRecord.id, "SIM Test")
            } catch (e: Exception) {
                val failRecord = progressMessage.copy(status = "FAILED", type = "FAILED", lastError = e.message, updatedAt = System.currentTimeMillis())
                repository.updateLocalMessage(failRecord)
                repository.reportSmsFailed(failRecord.id, e.message ?: "Test send crash")
            }
        }
    }

    fun syncOutboxManual() {
        viewModelScope.launch {
            repository.syncPendingOutbox()
        }
    }
}
