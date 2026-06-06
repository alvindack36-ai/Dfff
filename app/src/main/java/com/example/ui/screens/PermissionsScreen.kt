package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

@Composable
fun PermissionsScreen(
    onAllPermissionsGranted: () -> Unit
) {
    val context = LocalContext.current
    var permissionsState by remember { mutableStateOf(getPermissionsCheckMap(context)) }

    // Check periodically on resume
    DisposableEffect(Unit) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                permissionsState = getPermissionsCheckMap(context)
            }
        }
        // Simplified trigger in LaunchedEffect
        onDispose {}
    }

    LaunchedEffect(permissionsState) {
        val criticalRequired = listOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_PHONE_STATE
        )
        val allCriticalGranted = criticalRequired.all { permissionsState[it] == true }
        if (allCriticalGranted) {
            onAllPermissionsGranted()
        }
    }

    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionsState = getPermissionsCheckMap(context)
    }

    val requestPermissionsAction = {
        val needed = permissionsState.filter { !it.value }.keys.toTypedArray()
        if (needed.isNotEmpty()) {
            launcher.launch(needed)
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "System Permissions",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "SimGate Gateway requires the following permissions to manage SMS routing, listen for device heartbeats, and run reliably in the background.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    PermissionRow(
                        title = "SEND_SMS",
                        description = "Required to dispatch outgoing messages polled from your dashboard.",
                        isGranted = permissionsState[Manifest.permission.SEND_SMS] ?: false
                    )
                }
                item {
                    PermissionRow(
                        title = "READ_SMS",
                        description = "Required to read ICC/Sim details for outbound matching logs.",
                        isGranted = permissionsState[Manifest.permission.READ_SMS] ?: false
                    )
                }
                item {
                    PermissionRow(
                        title = "RECEIVE_SMS",
                        description = "Required to forward incoming client SMS payloads to your server webhook.",
                        isGranted = permissionsState[Manifest.permission.RECEIVE_SMS] ?: false
                    )
                }
                item {
                    PermissionRow(
                        title = "READ_PHONE_STATE",
                        description = "Required to detect carrier, cellular network signal strength and dual SIM states.",
                        isGranted = permissionsState[Manifest.permission.READ_PHONE_STATE] ?: false
                    )
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    item {
                        PermissionRow(
                            title = "POST_NOTIFICATIONS",
                            description = "Required to display the active persistent service overlay in background.",
                            isGranted = permissionsState[Manifest.permission.POST_NOTIFICATIONS] ?: false
                        )
                    }
                }
                item {
                    val isBatteryExempt = isBatteryExempt(context)
                    PermissionRow(
                        title = "Battery Optimization Disabled",
                        description = "Prevents Android OS from freezing SimGate during prolonged sleep.",
                        isGranted = isBatteryExempt,
                        actionButton = {
                            if (!isBatteryExempt) {
                                TextButton(
                                    onClick = { launchBatterySettings(context) },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text("Grant", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    )
                }
                item {
                    PermissionRow(
                        title = "Auto Start Configured",
                        description = "Enables startup boot listeners to restart gateway services on device reboot.",
                        isGranted = true // Boot receiver is declared in Manifest, always available
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = requestPermissionsAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Grant System Permissions", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "You will advance automatically once critical items grant.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun PermissionRow(
    title: String,
    description: String,
    isGranted: Boolean,
    actionButton: @Composable (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            if (actionButton != null) {
                actionButton()
            } else {
                Icon(
                    imageVector = if (isGranted) Icons.Filled.CheckCircle else Icons.Outlined.Cancel,
                    contentDescription = if (isGranted) "Granted" else "Needed",
                    tint = if (isGranted) MaterialTheme.colorScheme.primary else Color.Gray,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

private fun getPermissionsCheckMap(context: Context): Map<String, Boolean> {
    val checkList = mutableListOf(
        Manifest.permission.SEND_SMS,
        Manifest.permission.READ_SMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_PHONE_STATE
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    return checkList.associateWith {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}

private fun isBatteryExempt(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        pm.isIgnoringBatteryOptimizations(context.packageName)
    } else {
        true
    }
}

private fun launchBatterySettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            val genericIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(genericIntent)
        }
    }
}
