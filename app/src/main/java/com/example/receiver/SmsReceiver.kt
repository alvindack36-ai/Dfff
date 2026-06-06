package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.example.data.local.MessageEntity
import com.example.data.repository.GatewayRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {

    @Inject
    lateinit var repository: GatewayRepository

    companion object {
        private const val TAG = "SmsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isNullOrEmpty()) return

            CoroutineScope(Dispatchers.IO).launch {
                for (sms in messages) {
                    val sender = sms.displayOriginatingAddress ?: "Unknown"
                    val body = sms.displayMessageBody ?: ""
                    val timestamp = sms.timestampMillis

                    Log.i(TAG, "Incoming SMS from $sender: $body")

                    // Create local entity
                    val messageEntity = MessageEntity(
                        recipient = sender, // For incoming, recipient is the sender (for simple table columns mapping)
                        body = body,
                        type = "INCOMING",
                        status = "SENT", // Already received
                        createdAt = timestamp,
                        updatedAt = timestamp,
                        simIndex = sms.indexOnIcc
                    )

                    // Save locally and report to server
                    repository.saveLocalMessage(messageEntity)
                    repository.reportIncomingSms(
                        sender = sender,
                        body = body,
                        timestamp = timestamp,
                        simSlot = 0 // Default slot for simplicity or ICC index
                    )
                }
            }
        }
    }
}
