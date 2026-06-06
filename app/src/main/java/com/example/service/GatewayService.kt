package com.example.service

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.SimGateApp
import com.example.data.local.MessageEntity
import com.example.data.local.PreferencesManager
import com.example.data.remote.SmsTask
import com.example.data.repository.GatewayRepository
import com.example.receiver.WatchdogReceiver
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class GatewayService : Service() {

    @Inject
    lateinit var repository: GatewayRepository

    @Inject
    lateinit var prefs: PreferencesManager

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Uncaught error in GatewayService scope", throwable)
    }
    private var serviceJob = SupervisorJob()
    private var serviceScope = CoroutineScope(Dispatchers.Default + serviceJob + exceptionHandler)

    private var heartbeatJob: Job? = null
    private var pollingJob: Job? = null

    private var wakeLock: PowerManager.WakeLock? = null
    private var connectivityManager: ConnectivityManager? = null

    companion object {
        private const val TAG = "GatewayService"
        const val NOTIFICATION_ID = 8821
        private const val SENT_SMS_INTENT_PREFIX = "com.example.SMS_SENT_"
        private const val DELIVERED_SMS_INTENT_PREFIX = "com.example.SMS_DELIVERED_"

        @Volatile
        var isRunning = false
            private set

        private val _serviceStatus = MutableStateFlow("Stopped")
        val serviceStatus = _serviceStatus.asStateFlow()

        private val _lastHeartbeatTime = MutableStateFlow(0L)
        val lastHeartbeatTime = _lastHeartbeatTime.asStateFlow()

        private val _networkName = MutableStateFlow("Unknown")
        val networkName = _networkName.asStateFlow()

        private val _signalLevel = MutableStateFlow(4) // 0 to 4
        val signalLevel = _signalLevel.asStateFlow()
    }

    private val smsSentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action ?: return
            if (action.startsWith(SENT_SMS_INTENT_PREFIX)) {
                val messageId = action.substring(SENT_SMS_INTENT_PREFIX.length)
                val resultCode = resultCode
                serviceScope.launch {
                    handleSmsSentResult(messageId, resultCode)
                }
            }
        }
    }

    private var isNetworkAvailable = true
    private var pollIntervalMs = 5000L
    private var lastReconnectDelayIndex = 0
    private val reconnectDelays = listOf(5000L, 10000L, 20000L, 30000L)

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.i(TAG, "Network restored. Triggering immediate poll and outbox sync.")
            isNetworkAvailable = true
            updateStatusLabel("Online")
            // Immediate triggers
            serviceScope.launch {
                repository.syncPendingOutbox()
                triggerImmediatePoll()
            }
        }

        override fun onLost(network: Network) {
            Log.w(TAG, "Network lost.")
            isNetworkAvailable = false
            updateStatusLabel("Offline")
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate: Initializing SimGate SMS Service")
        
        // Start foreground immediately to obey background start restrictions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                buildServiceNotification("SimGate Active", "Checking credentials..."),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, buildServiceNotification("SimGate Active", "Checking credentials..."))
        }

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        try {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SimGate::SmsLock").apply {
                setReferenceCounted(false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create wake lock in onCreate", e)
        }

        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager?.registerNetworkCallback(networkRequest, networkCallback)

        registerSmsStatusReceivers()
        registerWatchdogAlarm()
        monitorTelephony()

        isRunning = true
        _serviceStatus.value = "Online"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.getStringExtra("action")
        Log.d(TAG, "onStartCommand received action: $action")
        
        if (action == "STOP") {
            stopServiceGracefully()
            return START_NOT_STICKY
        }

        // Commencing loop tasks if they aren't started
        startEventLoops()

        return START_STICKY
    }

    private fun startEventLoops() {
        if (heartbeatJob?.isActive == true && pollingJob?.isActive == true) {
            Log.d(TAG, "Event loops are already actively running, skipping redundant launch.")
            return
        }

        // Clean up any partially active jobs first
        heartbeatJob?.cancel()
        pollingJob?.cancel()

        heartbeatJob = serviceScope.launch {
            // Heartbeat Loop (60s)
            while (isActive) {
                try {
                    if (isNetworkAvailable && prefs.isPaired) {
                        acquireTemporaryWakeLock(10000L)
                        val batteryPct = getBatteryPercentage()
                        val isCharging = getIsCharging()
                        val netType = getNetworkType()
                        val signal = _signalLevel.value

                        Log.d(TAG, "Sending periodic heart-beat telemetry status")
                        repository.sendHeartbeat(batteryPct, signal, isCharging, netType)
                        _lastHeartbeatTime.value = System.currentTimeMillis()
                        
                        // Also flush offline events
                        repository.syncPendingOutbox()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in heartbeat loop execution", e)
                }
                delay(60000L)
            }
        }

        pollingJob = serviceScope.launch {
            // Polling Loop (Dynamic: 5s normal, 30s if battery < 15%)
            while (isActive) {
                try {
                    if (isNetworkAvailable && prefs.isPaired) {
                        acquireTemporaryWakeLock(10000L)
                        val batteryPct = getBatteryPercentage()
                        pollIntervalMs = if (batteryPct < 15) 30000L else 5000L

                        Log.v(TAG, "Polling gateway server, interval: $pollIntervalMs ms")
                        val tasks = repository.pollDevice()
                        if (tasks.isNotEmpty()) {
                            Log.i(TAG, "Polled ${tasks.size} outbound SMS tasks from queue")
                            for (task in tasks) {
                                sendSmsTask(task)
                            }
                        }
                        delay(pollIntervalMs)
                    } else if (!isNetworkAvailable) {
                        // Backoff retry if network is completely down
                        val delay = reconnectDelays[lastReconnectDelayIndex]
                        Log.v(TAG, "No network. Backing off search poll for $delay ms")
                        lastReconnectDelayIndex = (lastReconnectDelayIndex + 1).coerceAtMost(reconnectDelays.size - 1)
                        updateStatusLabel("Reconnecting")
                        delay(delay)
                    } else {
                        delay(5000L)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in polling loop execution", e)
                    delay(5000L)
                }
            }
        }
    }

    private suspend fun triggerImmediatePoll() {
        val tasks = repository.pollDevice()
        for (task in tasks) {
            sendSmsTask(task)
        }
    }

    private fun updateStatusLabel(status: String) {
        _serviceStatus.value = status
        updateForegroundNotification()
    }

    private fun updateForegroundNotification() {
        val systemNotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val message = "Status: ${_serviceStatus.value} | Device: ${prefs.deviceId ?: "Unpaired"}"
        systemNotificationManager.notify(NOTIFICATION_ID, buildServiceNotification("SimGate Active", message))
    }

    private fun registerSmsStatusReceivers() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Register matching filter with prefix
            val filter = IntentFilter().apply {
                addDataScheme("sms")
                // Register dynamically for sentinel prefix matching
            }
            // For simplicity we register broad intent receiver or register by actions.
            // Under compose and safety, dynamic sent broadcast actions are registered programmatically.
        }
    }

    private suspend fun sendSmsTask(task: SmsTask) {
        acquireTemporaryWakeLock(15000L)
        withContext(Dispatchers.Main) {
            try {
                // Check selection preference
                val selectedSubId = prefs.defaultSimSubId
                
                // Track state locally first
                val progressMessage = MessageEntity(
                    id = task.id,
                    recipient = task.recipient,
                    body = task.body,
                    type = "SENT",
                    status = "PENDING",
                    simIndex = if (selectedSubId == -1) 0 else 1 // Simplified representation
                )
                repository.saveLocalMessage(progressMessage)

                val smsManager = if (selectedSubId != -1 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                    SmsManager.getSmsManagerForSubscriptionId(selectedSubId)
                } else {
                    SmsManager.getDefault()
                }

                val parts = smsManager.divideMessage(task.body)
                val sentIntentAction = "$SENT_SMS_INTENT_PREFIX${task.id}"
                
                // Register the temporary receiver for this message lifecycle dynamically
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    registerReceiver(smsSentReceiver, IntentFilter(sentIntentAction), RECEIVER_EXPORTED_IF_NEEDED())
                } else {
                    registerReceiver(smsSentReceiver, IntentFilter(sentIntentAction))
                }

                val sentIntents = ArrayList<PendingIntent>()
                for (i in 0 until parts.size) {
                    val intent = Intent(sentIntentAction)
                    val pi = PendingIntent.getBroadcast(
                        this@GatewayService,
                        task.id.hashCode() + i,
                        intent,
                        PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
                    )
                    sentIntents.add(pi)
                }

                if (parts.size > 1) {
                    smsManager.sendMultipartTextMessage(task.recipient, null, parts, sentIntents, null)
                } else {
                    smsManager.sendTextMessage(task.recipient, null, task.body, sentIntents[0], null)
                }
                
                Log.i(TAG, "SmsManager dispatched payload for message ID: ${task.id}")
            } catch (e: Exception) {
                Log.e(TAG, "CRITICAL: SmsManager crash sending payload", e)
                markLocalMessageFailed(task.id, e.message ?: "Unknown crash")
                repository.reportSmsFailed(task.id, e.message ?: "SmsManager dispatch crashed")
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun RECEIVER_EXPORTED_IF_NEEDED(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            RECEIVER_EXPORTED
        } else {
            0
        }
    }

    private suspend fun handleSmsSentResult(messageId: String, resultCode: Int) {
        acquireTemporaryWakeLock(15000L)
        val currentRecord = repository.getMessageById(messageId) ?: return
        
        try {
            unregisterReceiver(smsSentReceiver)
        } catch (_: Exception) {}

        if (resultCode == Activity.RESULT_OK) {
            Log.i(TAG, "SMS Message ID $messageId successfully dispatched to networks!")
            val updated = currentRecord.copy(status = "SENT", updatedAt = System.currentTimeMillis())
            repository.updateLocalMessage(updated)
            repository.reportSmsSent(messageId, "SIM 1") // Can parse real SIM info if available
        } else {
            val errorMsg = when (resultCode) {
                SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "Generic Failure"
                SmsManager.RESULT_ERROR_NO_SERVICE -> "No network service"
                SmsManager.RESULT_ERROR_NULL_PDU -> "Null PDU error"
                SmsManager.RESULT_ERROR_RADIO_OFF -> "Radio transmitter off"
                else -> "Failure Code: $resultCode"
            }
            Log.w(TAG, "SMS Message ID $messageId failed to dispatch: $errorMsg")
            
            val updatedAttempts = currentRecord.attempts + 1
            if (updatedAttempts < 4) {
                // Schedule/Retrigger Send with synthetic exponential backoff
                val backoffDelays = listOf(5000L, 15000L, 45000L, 120000L)
                val delayTime = backoffDelays.getOrElse(updatedAttempts) { 15000L }
                Log.i(TAG, "Scheduling SMS ID $messageId retry #$updatedAttempts in $delayTime ms")
                
                repository.updateLocalMessage(currentRecord.copy(attempts = updatedAttempts, lastError = errorMsg))
                serviceScope.launch {
                    delay(delayTime)
                    sendSmsTask(SmsTask(currentRecord.id, currentRecord.recipient, currentRecord.body))
                }
            } else {
                markLocalMessageFailed(messageId, errorMsg)
                repository.reportSmsFailed(messageId, errorMsg)
            }
        }
    }

    private suspend fun markLocalMessageFailed(messageId: String, reason: String) {
        val currentRecord = repository.getMessageById(messageId) ?: return
        val updated = currentRecord.copy(
            status = "FAILED",
            type = "FAILED",
            lastError = reason,
            updatedAt = System.currentTimeMillis()
        )
        repository.updateLocalMessage(updated)
    }

    private fun getBatteryPercentage(): Int {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = registerReceiver(null, filter)
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level != -1 && scale != -1) {
            ((level.toFloat() / scale.toFloat()) * 100).toInt()
        } else 100
    }

    private fun getIsCharging(): Boolean {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = registerReceiver(null, filter)
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }

    private fun getNetworkType(): String {
        val activeNetwork = connectivityManager?.activeNetwork ?: return "NONE"
        val caps = connectivityManager?.getNetworkCapabilities(activeNetwork) ?: return "NONE"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
            else -> "OTHER"
        }
    }

    private fun monitorTelephony() {
        try {
            val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            _networkName.value = telephonyManager.networkOperatorName ?: "Carrier"

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                @Suppress("DEPRECATION")
                telephonyManager.listen(object : PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onSignalStrengthsChanged(strength: SignalStrength?) {
                        super.onSignalStrengthsChanged(strength)
                        if (strength != null) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                _signalLevel.value = strength.level
                            } else {
                                // Fallback mapping
                                _signalLevel.value = 4
                            }
                        }
                    }
                }, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Telephony tracking error", e)
        }
    }

    private fun registerWatchdogAlarm() {
        val intent = Intent(this, WatchdogReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            2424,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // Fire alarm every 15 minutes
        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + 900000L,
            900000L,
            pendingIntent
        )
    }

    private fun buildServiceNotification(title: String, body: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        // Stop intent
        val stopIntent = Intent(this, GatewayService::class.java).apply {
            putExtra("action", "STOP")
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val bannerColor = 0x16a34a // Hex primary color #16a34a

        return NotificationCompat.Builder(this, SimGateApp.CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(android.R.drawable.stat_notify_chat) // Small system default chat icon
            .setColor(bannerColor)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Gateway", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun acquireTemporaryWakeLock(timeoutMs: Long = 10000L) {
        try {
            if (wakeLock == null) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SimGate::SmsLock").apply {
                    setReferenceCounted(false)
                }
            }
            wakeLock?.acquire(timeoutMs)
            Log.d(TAG, "Temporary wake lock acquired for $timeoutMs ms")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire temporary wake lock", e)
        }
    }

    private fun stopServiceGracefully() {
        Log.i(TAG, "Stopping SimGate Gateway service gracefully")
        isRunning = false
        _serviceStatus.value = "Stopped"
        
        // Disable AlarmManager watchdog when manually stopped
        try {
            val intent = Intent(this, WatchdogReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                this,
                2424,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
                alarmManager.cancel(pendingIntent)
            }
        } catch (_: Exception) {}

        connectivityManager?.unregisterNetworkCallback(networkCallback)
        wakeLock?.let {
            try {
                if (it.isHeld) {
                    it.release()
                }
                Unit
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing wake lock in stopServiceGracefully", e)
            } finally {
                wakeLock = null
            }
        }
        
        serviceJob.cancel()
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        _serviceStatus.value = "Stopped"
        serviceJob.cancel()
        wakeLock?.let {
            try {
                if (it.isHeld) {
                    it.release()
                }
                Unit
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing wake lock in onDestroy", e)
            } finally {
                wakeLock = null
            }
        }
        Log.i(TAG, "onDestroy service complete")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
