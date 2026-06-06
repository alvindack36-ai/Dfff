package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.GatewayViewModel
import com.squareup.moshi.Moshi
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: GatewayViewModel by viewModels()

    @Inject
    lateinit var moshi: Moshi

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val isPaired by viewModel.isPaired.collectAsState()
                var currentNavigationState by remember { mutableStateOf("permissions_check") }
                
                // Active Sub-tab selector within main dashboard
                var selectedTabTab by remember { mutableStateOf("home") }

                // Check permissions on start
                LaunchedEffect(isPaired) {
                    if (!isPaired) {
                        currentNavigationState = "onboarding"
                    } else {
                        // Check if system permissions are already satisfied
                        if (checkAllPermissionsSatisfied(this@MainActivity)) {
                            currentNavigationState = "dashboard"
                            viewModel.startGateway() // Auto-resume on start
                        } else {
                            currentNavigationState = "permissions_screen"
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when (currentNavigationState) {
                        "onboarding" -> {
                            val isConnecting by viewModel.isConnecting.collectAsState()
                            val connError by viewModel.connectionError.collectAsState()

                            OnboardingScreen(
                                isConnecting = isConnecting,
                                connectionError = connError,
                                onConnect = { api, devId, token ->
                                    viewModel.connectDevicePair(api, devId, token, onSuccess = {
                                        currentNavigationState = if (checkAllPermissionsSatisfied(this@MainActivity)) "dashboard" else "permissions_screen"
                                    })
                                },
                                onLaunchScanner = {
                                    currentNavigationState = "scanner"
                                }
                            )
                        }

                        "scanner" -> {
                            QrScannerScreen(
                                onBack = { currentNavigationState = "onboarding" },
                                onQrScanned = { rawJson ->
                                    val parsed = parseQrCredentials(rawJson)
                                    if (parsed != null) {
                                        Log.i(TAG, "QR decoded credentials successfully: ${parsed.deviceId}")
                                        viewModel.connectDevicePair(
                                            api = parsed.api ?: "https://abjwmllylfdbcmhfqwvk.supabase.co/functions/v1",
                                            devId = parsed.deviceId ?: "",
                                            token = parsed.deviceToken ?: "",
                                            onSuccess = {
                                                currentNavigationState = if (checkAllPermissionsSatisfied(this@MainActivity)) "dashboard" else "permissions_screen"
                                            }
                                        )
                                    } else {
                                        Log.e(TAG, "Malformed QR scanner payload decoded")
                                        // Give simple fallback string connection try or direct connect pairing failure
                                        viewModel.connectDevicePair("", "", "INVALID_QR")
                                        currentNavigationState = "onboarding"
                                    }
                                }
                            )
                        }

                        "permissions_screen" -> {
                            PermissionsScreen(
                                onAllPermissionsGranted = {
                                    viewModel.startGateway() // Boot the service immediately
                                    currentNavigationState = "dashboard"
                                }
                            )
                        }

                        "dashboard" -> {
                            Scaffold(
                                bottomBar = {
                                    NavigationBar(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    ) {
                                        NavigationBarItem(
                                            selected = selectedTabTab == "home",
                                            onClick = { selectedTabTab = "home" },
                                            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                                            label = { Text("Home", style = MaterialTheme.typography.labelSmall) }
                                        )
                                        NavigationBarItem(
                                            selected = selectedTabTab == "history",
                                            onClick = { selectedTabTab = "history" },
                                            icon = { Icon(Icons.Default.History, contentDescription = "History") },
                                            label = { Text("History", style = MaterialTheme.typography.labelSmall) }
                                        )
                                        NavigationBarItem(
                                            selected = selectedTabTab == "sims",
                                            onClick = { selectedTabTab = "sims" },
                                            icon = { Icon(Icons.Default.SimCard, contentDescription = "SIMs") },
                                            label = { Text("SIMs", style = MaterialTheme.typography.labelSmall) }
                                        )
                                        NavigationBarItem(
                                            selected = selectedTabTab == "settings",
                                            onClick = { selectedTabTab = "settings" },
                                            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                                            label = { Text("Settings", style = MaterialTheme.typography.labelSmall) }
                                        )
                                    }
                                }
                            ) { innerPadding ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(innerPadding)
                                        .background(MaterialTheme.colorScheme.background)
                                ) {
                                    // Animated Tab transitions
                                    when (selectedTabTab) {
                                        "home" -> HomeScreen(
                                            viewModel = viewModel,
                                            onEditCredentials = {
                                                viewModel.unpair()
                                                currentNavigationState = "onboarding"
                                            },
                                            onNavigateSims = { selectedTabTab = "sims" }
                                        )
                                        "history" -> HistoryScreen(viewModel = viewModel)
                                        "sims" -> SimScreen(viewModel = viewModel)
                                        "settings" -> SettingsScreen(
                                            viewModel = viewModel,
                                            onEditCredentials = {
                                                viewModel.unpair()
                                                currentNavigationState = "onboarding"
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun checkAllPermissionsSatisfied(context: Context): Boolean {
        val criticalRequired = mutableListOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_PHONE_STATE
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        return criticalRequired.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    // JSON Model representing decoded values scan result
    private fun parseQrCredentials(rawJson: String): QrCredentialsDecoded? {
        return try {
            val adapter = moshi.adapter(QrCredentialsDecoded::class.java)
            adapter.fromJson(rawJson.trim())
        } catch (e: Exception) {
            Log.w(TAG, "Moshi failed to parse QR credentials, attempting manual fallback string matching", e)
            
            // Loose manual extraction as robust fallback
            val apiRegex = "\"api\"\\s*:\\s*\"([^\"]+)\"".toRegex()
            val idRegex = "\"device_id\"\\s*:\\s*\"([^\"]+)\"".toRegex()
            val tokenRegex = "\"device_token\"\\s*:\\s*\"([^\"]+)\"".toRegex()

            val api = apiRegex.find(rawJson)?.groups?.get(1)?.value
            val id = idRegex.find(rawJson)?.groups?.get(1)?.value
            val token = tokenRegex.find(rawJson)?.groups?.get(1)?.value

            if (id != null && token != null) {
                QrCredentialsDecoded(api = api, deviceId = id, deviceToken = token)
            } else {
                null
            }
        }
    }
}

// Secure representation container to match JSON parsing
@com.squareup.moshi.JsonClass(generateAdapter = true)
data class QrCredentialsDecoded(
    @com.squareup.moshi.Json(name = "v") val v: Int? = 1,
    @com.squareup.moshi.Json(name = "api") val api: String?,
    @com.squareup.moshi.Json(name = "device_id") val deviceId: String?,
    @com.squareup.moshi.Json(name = "device_token") val deviceToken: String?
)
