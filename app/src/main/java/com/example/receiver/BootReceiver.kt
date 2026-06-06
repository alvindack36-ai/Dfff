package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.data.local.PreferencesManager
import com.example.service.GatewayService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var prefs: PreferencesManager

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.i("BootReceiver", "Received boot broadcast action: $action")
        
        if (action == Intent.ACTION_BOOT_COMPLETED || action == "android.intent.action.QUICKBOOT_POWERON") {
            if (prefs.isPaired) {
                Log.i("BootReceiver", "Device is paired, auto-starting SimGate Foreground Service")
                
                val serviceIntent = Intent(context, GatewayService::class.java).apply {
                    putExtra("action", "START")
                }
                
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Failed to auto-start SimGate service due to background restrictions", e)
                }
            } else {
                Log.i("BootReceiver", "Device is not paired yet, skipping auto-start")
            }
        }
    }
}
