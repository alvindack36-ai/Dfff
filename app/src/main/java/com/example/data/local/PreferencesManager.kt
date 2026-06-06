package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesManager @Inject constructor(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("simgate_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_API_BASE = "api_base"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_DEVICE_TOKEN = "device_token"
        private const val KEY_IS_PAIRED = "is_paired"
        private const val KEY_DEFAULT_SIM_SUB_ID = "default_sim_sub_id" // -1 for Auto, or valid Sub ID
        private const val KEY_LAST_HEARTBEAT = "last_heartbeat"
        private const val DEFAULT_API_BASE = "https://abjwmllylfdbcmhfqwvk.supabase.co/functions/v1"
    }

    var apiBase: String
        get() = prefs.getString(KEY_API_BASE, DEFAULT_API_BASE) ?: DEFAULT_API_BASE
        set(value) = prefs.edit().putString(KEY_API_BASE, value).apply()

    var deviceId: String?
        get() = prefs.getString(KEY_DEVICE_ID, null)
        set(value) = prefs.edit().putString(KEY_DEVICE_ID, value).apply()

    var deviceToken: String?
        get() = prefs.getString(KEY_DEVICE_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_DEVICE_TOKEN, value).apply()

    var isPaired: Boolean
        get() = prefs.getBoolean(KEY_IS_PAIRED, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_PAIRED, value).apply()

    var defaultSimSubId: Int
        get() = prefs.getInt(KEY_DEFAULT_SIM_SUB_ID, -1) // -1 is Auto / default Sim
        set(value) = prefs.edit().putInt(KEY_DEFAULT_SIM_SUB_ID, value).apply()

    var lastHeartbeat: Long
        get() = prefs.getLong(KEY_LAST_HEARTBEAT, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_HEARTBEAT, value).apply()

    fun saveCredentials(api: String, devId: String, token: String) {
        prefs.edit().apply {
            putString(KEY_API_BASE, api)
            putString(KEY_DEVICE_ID, devId)
            putString(KEY_DEVICE_TOKEN, token)
            putBoolean(KEY_IS_PAIRED, true)
        }.apply()
    }

    fun unpair() {
        prefs.edit().apply {
            remove(KEY_DEVICE_ID)
            remove(KEY_DEVICE_TOKEN)
            putBoolean(KEY_IS_PAIRED, false)
        }.apply()
    }
}
