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
class WatchdogReceiver : BroadcastReceiver() {

    @Inject
    lateinit var prefs: PreferencesManager

    override fun onReceive(context: Context, intent: Intent) {
        Log.i("WatchdogReceiver", "Watchdog keep-alive check running...")
        
        if (prefs.isPaired && !GatewayService.isRunning) {
            Log.w("WatchdogReceiver", "SimGate GatewayService is paired but not running! Reviving service...")
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
                Log.e("WatchdogReceiver", "Watchdog failed to revive service due to background restrictions", e)
            }
        } else {
            Log.i("WatchdogReceiver", "Watchdog check passed. Service state: ${if (GatewayService.isRunning) "Running" else "Not required"}")
        }
    }
}
