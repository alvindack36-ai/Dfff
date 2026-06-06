package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.GatewayViewModel

@Composable
fun SettingsScreen(
    viewModel: GatewayViewModel,
    onEditCredentials: () -> Unit
) {
    val apiBase by viewModel.apiBaseUrl.collectAsState()
    val deviceId by viewModel.deviceId.collectAsState()
    val deviceToken by viewModel.deviceToken.collectAsState()

    var showTestSmsDialog by remember { mutableStateOf(false) }
    var testRecipient by remember { mutableStateOf("") }
    var testBody by remember { mutableStateOf("SimGate Test SMS from Gateway") }

    var showUnpairConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "System Settings",
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            "Configure gateway channels, unpair nodes or run test bounds",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(20.dp))

        // Device Configurations Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    "Connection Bindings",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                ConfigDetailRow(label = "Endpoint base", value = apiBase)
                ConfigDetailRow(label = "Device IdentityID", value = deviceId)
                ConfigDetailRow(label = "Secret token", value = deviceToken)
                ConfigDetailRow(label = "App version", value = "1.0.0 (SimGate Official Build)")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Action Buttons list
        Text(
            "Administrative Maintenance",
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            item {
                SettingsActionCard(
                    title = "Send Test SMS",
                    description = "Validate outgoing SMS capabilities via local hardware carrier manually.",
                    icon = Icons.Default.Send,
                    onClick = { showTestSmsDialog = true }
                )
            }
            item {
                SettingsActionCard(
                    title = "Restart Gateway Loops",
                    description = "Cycle main background receivers, threads and synchronizers.",
                    icon = Icons.Default.Refresh,
                    onClick = {
                        viewModel.stopGateway()
                        viewModel.startGateway()
                    }
                )
            }
            item {
                SettingsActionCard(
                    title = "Clear Locally Logged History",
                    description = "Purge cached message logs and queues from this phone (SQLite storage).",
                    icon = Icons.Default.DeleteForever,
                    color = Color(0xFFEF4444),
                    onClick = { viewModel.clearHistory() }
                )
            }
            item {
                SettingsActionCard(
                    title = "Disconnect & Unpair Device",
                    description = "Clear secret token bindings from memory storage and halt all background syncing.",
                    icon = Icons.Default.PowerSettingsNew,
                    color = Color(0xFFEF4444),
                    onClick = { showUnpairConfirm = true }
                )
            }
        }

        // Test SMS Dialog View
        if (showTestSmsDialog) {
            AlertDialog(
                onDismissRequest = { showTestSmsDialog = false },
                title = { Text("Send Test Outbox SMS") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Verify local SMS dispatch bindings by entering a recipient manually.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(
                            value = testRecipient,
                            onValueChange = { testRecipient = it },
                            label = { Text("Phone Number (+xx...)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = testBody,
                            onValueChange = { testBody = it },
                            label = { Text("Message Body") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (testRecipient.isNotBlank()) {
                                viewModel.sendTestSms(testRecipient, testBody)
                                showTestSmsDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Send SMS")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showTestSmsDialog = false }) { Text("Cancel") }
                }
            )
        }

        // Unpair Confirm Dialog View
        if (showUnpairConfirm) {
            AlertDialog(
                onDismissRequest = { showUnpairConfirm = false },
                title = { Text("Halt & Unpair Gateway?") },
                text = {
                    Text(
                        "Are you sure you want to stop background SMS syncing, clear memory registers and unpair this device? You will need to scan another credentials QR code to pair again.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.unpair()
                            showUnpairConfirm = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                    ) {
                        Text("Unpair Node")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showUnpairConfirm = false }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
fun ConfigDetailRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

@Composable
fun SettingsActionCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(color.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    description,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
