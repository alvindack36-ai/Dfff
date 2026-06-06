package com.example.ui.screens

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.MessageEntity
import com.example.ui.viewmodel.GatewayViewModel
import com.example.ui.viewmodel.SimCardInfo
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HomeScreen(
    viewModel: GatewayViewModel,
    onEditCredentials: () -> Unit,
    onNavigateSims: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    
    val pairingState by viewModel.isPaired.collectAsState()
    val apiBaseUrl by viewModel.apiBaseUrl.collectAsState()
    val deviceId by viewModel.deviceId.collectAsState()
    
    val status by viewModel.connectionStatus.collectAsState()
    val lastHeartbeat by viewModel.lastHeartbeatTime.collectAsState()
    val carrierName by viewModel.networkName.collectAsState()
    val signalStrength by viewModel.signalStrengthLevel.collectAsState()
    
    val sentToday by viewModel.sentCountToday.collectAsState()
    val failedToday by viewModel.failedCountToday.collectAsState()
    val receivedToday by viewModel.receivedCountToday.collectAsState()
    val logs by viewModel.logMessages.collectAsState()
    val sims by viewModel.simCards.collectAsState()
    val selectedSimSubId by viewModel.selectedSimSubId.collectAsState()

    val formattedHeartbeat = remember(lastHeartbeat) {
        if (lastHeartbeat == 0L) "Never"
        else SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(Date(lastHeartbeat))
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(28.dp))
            
            // Screen Header Line
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.CellTower,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            "SMS Gateway",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            "Your phone is running as SMS Gateway",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = { viewModel.syncOutboxManual() }) {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = "Notifications / Alerts",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Live Connection Status Widget Card
        item {
            val isOnline = status == "Online"
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isOnline) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) 
                                     else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isOnline) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) 
                                  else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    if (isOnline) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    else Color.DarkGray.copy(alpha = 0.15f),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isOnline) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                contentDescription = null,
                                tint = if (isOnline) MaterialTheme.colorScheme.primary else Color.Gray,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (isOnline) "Connected" else "Stopped",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = if (isOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Gateway is running and connected to server",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Last heartbeat: $formattedHeartbeat",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(if (isOnline) MaterialTheme.colorScheme.primary else Color.Gray, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    status,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isOnline) MaterialTheme.colorScheme.primary else Color.Gray
                                )
                            }
                        }
                    }
                    Icon(
                        Icons.Default.SettingsInputAntenna,
                        contentDescription = null,
                        tint = if (isOnline) MaterialTheme.colorScheme.primary else Color.Gray,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Device ID Display Block
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Smartphone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Device ID", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                deviceId,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    IconButton(onClick = { clipboardManager.setText(AnnotatedString(deviceId)) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy ID", modifier = Modifier.size(16.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Stats Row (Battery / Signal / Uptime)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                /* stats card usage is handled in subcomposable. StatsMiniCard uses dp and sp properly there. */
                StatsMiniCard(
                    modifier = Modifier.weight(1.3f),
                    title = "Battery",
                    value = "87%", // Live value placeholder matching UI layout
                    subValue = "Charging",
                    icon = Icons.Default.BatteryChargingFull,
                    iconColor = MaterialTheme.colorScheme.primary
                )
                StatsMiniCard(
                    modifier = Modifier.weight(1.3f),
                    title = "Signal",
                    value = "$signalStrength/4 Strong",
                    subValue = carrierName,
                    icon = Icons.Default.SignalCellularAlt,
                    iconColor = MaterialTheme.colorScheme.primary
                )
                StatsMiniCard(
                    modifier = Modifier.weight(1f),
                    title = "Uptime",
                    value = "Online",
                    subValue = "Sticky",
                    icon = Icons.Default.Update,
                    iconColor = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // SIM Card Selector Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "SIM Information",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "Manage SIMs",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onNavigateSims() }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Active SIM Card Row
        if (sims.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SimCard, contentDescription = null, tint = Color.Gray)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("No active SIM subscriptions detected. Using Auto.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        } else {
            items(sims) { sim ->
                val isSelected = selectedSimSubId == sim.subscriptionId || (selectedSimSubId == -1 && sim.slotIndex == 0)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clickable { viewModel.selectSim(sim.subscriptionId) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.05f) 
                                         else MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) 
                                      else Color.Transparent
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(6.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.SimCard, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "SIM ${sim.slotIndex + 1} (${sim.carrierName})",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isSelected) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        SuggestionChip(
                                            onClick = {},
                                            label = { Text("Default", fontSize = 9.sp) },
                                            modifier = Modifier.height(18.dp)
                                        )
                                    }
                                }
                                Text(
                                    sim.phoneNumber?.ifBlank { "Line active" } ?: "Line active",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = isSelected,
                            onCheckedChange = { if (it) viewModel.selectSim(sim.subscriptionId) }
                        )
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
        }

        // Metrics Widget Row
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricWidget(
                    modifier = Modifier.weight(1f),
                    title = "Sent Today",
                    count = sentToday,
                    color = MaterialTheme.colorScheme.primary,
                    icon = Icons.Default.ArrowCircleUp
                )
                MetricWidget(
                    modifier = Modifier.weight(1f),
                    title = "Failed Today",
                    count = failedToday,
                    color = Color(0xFFF59E0B),
                    icon = Icons.Default.Warning
                )
                MetricWidget(
                    modifier = Modifier.weight(1f),
                    title = "Received Today",
                    count = receivedToday,
                    color = Color(0xFF3B82F6),
                    icon = Icons.Default.ArrowCircleDown
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Quick Actions block
        item {
            Text(
                "Quick Actions",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickActionButton(
                    modifier = Modifier.weight(1f),
                    title = "Start Gateway",
                    icon = Icons.Default.PlayArrow,
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    contentColor = MaterialTheme.colorScheme.primary,
                    onClick = { viewModel.startGateway() }
                )
                QuickActionButton(
                    modifier = Modifier.weight(1f),
                    title = "Stop Gateway",
                    icon = Icons.Default.Stop,
                    containerColor = Color(0xFFEF4444).copy(alpha = 0.15f),
                    contentColor = Color(0xFFEF4444),
                    onClick = { viewModel.stopGateway() }
                )
                QuickActionButton(
                    modifier = Modifier.weight(1f),
                    title = "Edit Credentials",
                    icon = Icons.Default.Edit,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    onClick = { onEditCredentials() }
                )
                QuickActionButton(
                    modifier = Modifier.weight(1f),
                    title = "Refresh Status",
                    icon = Icons.Default.Refresh,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    onClick = { 
                        viewModel.loadSims()
                        viewModel.syncOutboxManual()
                    }
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // Recent Activity list
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Recent Activity",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "View All",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { /* Tab or custom screen */ }
                )
            }
        }

        if (logs.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.SmsFailed, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No recent log activity in pool", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            items(logs.take(5)) { item ->
                RecentActivityRow(item)
            }
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

@Composable
fun StatsMiniCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subValue: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(title, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(subValue, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
fun MetricWidget(
    modifier: Modifier = Modifier,
    title: String,
    count: Int,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(color.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
                }
                Text(
                    count.toString(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(title, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun QuickActionButton(
    modifier: Modifier = Modifier,
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = title, tint = contentColor, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(title, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = contentColor, maxLines = 1)
        }
    }
}

@Composable
fun RecentActivityRow(entity: MessageEntity) {
    val isIncoming = entity.type == "INCOMING"
    val isFailed = entity.type == "FAILED"

    val iconColor = when {
        isFailed -> Color(0xFFEF4444)
        isIncoming -> Color(0xFF3B82F6)
        else -> MaterialTheme.colorScheme.primary
    }

    val icon = when {
        isFailed -> Icons.Default.Warning
        isIncoming -> Icons.Default.ArrowCircleDown
        else -> Icons.Default.ArrowCircleUp
    }

    val statusText = when {
        isFailed -> "Failed"
        isIncoming -> "Received"
        else -> "Sent"
    }

    val formattedTime = run {
        val date = Date(entity.createdAt)
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(date)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(iconColor.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        entity.recipient,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        entity.body,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formattedTime, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(statusText, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = iconColor)
            }
        }
    }
}
