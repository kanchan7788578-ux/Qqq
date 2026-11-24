package com.example.gamepad.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.SettingsInputAntenna
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.gamepad.i18n.AppStrings
import com.example.gamepad.i18n.StringsProvider
import com.example.gamepad.model.AppLanguage
import com.example.gamepad.model.ButtonSymbolStyle
import com.example.gamepad.model.ConnectionState
import com.example.gamepad.model.ControllerLayout
import com.example.gamepad.model.DiscoveredTv
import com.example.gamepad.model.NetworkMode
import com.example.gamepad.model.PollingRate
import com.example.gamepad.network.GamepadPacketCodec
import com.example.gamepad.network.NetworkTopologyHelper
import com.example.gamepad.ui.MainUiState
import com.example.ui.theme.ButtonAEmerald
import com.example.ui.theme.CarbonBorder
import com.example.ui.theme.CarbonElevated
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonIndigo
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.StatusHotspotAmber
import com.example.ui.theme.StatusLiveGreen
import com.example.ui.theme.StatusOfflineRed
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ConnectionHubScreen(
    state: MainUiState,
    onScanClick: () -> Unit,
    onStopScanClick: () -> Unit,
    onConnectTv: (DiscoveredTv) -> Unit,
    onManualConnect: (ip: String, port: Int, name: String) -> Unit,
    onDisconnect: () -> Unit,
    onOpenGamepad: () -> Unit,
    onStartTvReceiver: () -> Unit,
    onStartLoopbackTest: () -> Unit,
    onRemoveSavedTv: (DiscoveredTv) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onPollingRateChange: (PollingRate) -> Unit,
    onHapticsChange: (Boolean) -> Unit,
    onLayoutChange: (ControllerLayout) -> Unit,
    onSymbolStyleChange: (ButtonSymbolStyle) -> Unit,
    onDeadzoneChange: (Float) -> Unit,
    onAutoLandscapeChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val strings = StringsProvider.forLanguage(state.settings.language)
    var showManualDialog by remember { mutableStateOf(false) }
    var showGuideSheet by remember { mutableStateOf(false) }

    val pulseTransition = rememberInfiniteTransition(label = "scan_pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0E1422), ObsidianBg, Color(0xFF07090E))
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(ElectricCyan.copy(alpha = 0.25f), NeonIndigo.copy(alpha = 0.25f))
                                    )
                                )
                                .border(1.dp, ElectricCyan.copy(alpha = 0.6f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Gamepad,
                                contentDescription = "PulsePad logo",
                                tint = ElectricCyan,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = strings.appTitle,
                                style = MaterialTheme.typography.headlineMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = strings.offlineBadge,
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = CarbonElevated,
                            border = BorderStroke(1.dp, CarbonBorder),
                            modifier = Modifier
                                .testTag("language_toggle_button")
                                .clip(RoundedCornerShape(50))
                                .clickable {
                                    val next = if (state.settings.language == AppLanguage.BN) {
                                        AppLanguage.EN
                                    } else {
                                        AppLanguage.BN
                                    }
                                    onLanguageChange(next)
                                }
                        ) {
                            Text(
                                text = if (state.settings.language == AppLanguage.BN) "EN" else "বাংলা",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextPrimary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = { showGuideSheet = true },
                            modifier = Modifier.testTag("guide_help_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.HelpOutline,
                                contentDescription = strings.protocolGuideTitle,
                                tint = TextSecondary
                            )
                        }
                    }
                }
            }

            // Hotspot & Local Network Diagnostic Card
            item {
                val net = state.networkStatus
                val (statusTitle, statusColor, statusIcon) = when (net.mode) {
                    NetworkMode.HOTSPOT_AP -> Triple(
                        "${strings.hotspotActive} • ${net.localIp ?: ""}",
                        StatusLiveGreen,
                        Icons.Rounded.WifiTethering
                    )
                    NetworkMode.LOCAL_WIFI -> Triple(
                        "${strings.wifiActive} • ${net.localIp ?: ""}",
                        ElectricCyan,
                        Icons.Rounded.Wifi
                    )
                    NetworkMode.OFFLINE -> Triple(
                        strings.networkOffline,
                        StatusHotspotAmber,
                        Icons.Rounded.WifiOff
                    )
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = CarbonSurface),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.45f)),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = statusIcon,
                                    contentDescription = statusTitle,
                                    tint = statusColor,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = statusTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = statusColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            TextButton(
                                onClick = {
                                    runCatching {
                                        context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                                    }.onFailure {
                                        runCatching {
                                            context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                                        }
                                    }
                                },
                                modifier = Modifier.testTag("open_hotspot_settings_button")
                            ) {
                                Text(strings.openHotspotSettings, color = ElectricCyan)
                            }
                        }

                        if (net.mode == NetworkMode.OFFLINE) {
                            Text(
                                text = strings.hotspotHint,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }

            // Active Connection Card (when connected or connecting)
            item {
                AnimatedVisibility(visible = state.connectionState !is ConnectionState.Disconnected) {
                    when (val conn = state.connectionState) {
                        is ConnectionState.Connecting -> {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF102230)),
                                border = BorderStroke(1.dp, ElectricCyan),
                                shape = MaterialTheme.shapes.large
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        color = ElectricCyan,
                                        strokeWidth = 2.5.dp
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "${conn.target.name} (${conn.target.host})...",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = TextPrimary
                                    )
                                }
                            }
                        }
                        is ConnectionState.Connected -> {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0E2229)),
                                border = BorderStroke(1.5.dp, StatusLiveGreen.copy(alpha = 0.8f)),
                                shape = MaterialTheme.shapes.large
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(StatusLiveGreen)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    text = conn.target.name,
                                                    style = MaterialTheme.typography.titleLarge,
                                                    color = TextPrimary
                                                )
                                                Text(
                                                    text = "${conn.target.host}:${conn.target.port} • ${conn.rttMs} ms",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = StatusLiveGreen
                                                )
                                            }
                                        }
                                        OutlinedButton(
                                            onClick = onDisconnect,
                                            border = BorderStroke(1.dp, StatusOfflineRed.copy(alpha = 0.6f)),
                                            modifier = Modifier.testTag("disconnect_tv_button")
                                        ) {
                                            Text(strings.disconnect, color = StatusOfflineRed)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = onOpenGamepad,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = ElectricCyan,
                                            contentColor = ObsidianBg
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("open_gamepad_button")
                                    ) {
                                        Icon(Icons.Rounded.Gamepad, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(strings.openGamepad, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        ConnectionState.Disconnected -> Unit
                    }
                }
            }

            // Primary Scan & Direct IP Actions
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { if (state.isScanning) onStopScanClick() else onScanClick() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (state.isScanning) CarbonElevated else ElectricCyan,
                            contentColor = if (state.isScanning) ElectricCyan else ObsidianBg
                        ),
                        border = if (state.isScanning) {
                            BorderStroke(1.5.dp, ElectricCyan.copy(alpha = pulseAlpha))
                        } else null,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .weight(1.45f)
                            .height(54.dp)
                            .testTag("scan_tv_button")
                    ) {
                        if (state.isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = ElectricCyan,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = strings.scanningTvs,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Icon(Icons.Rounded.Radar, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(strings.scanForTv, fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedButton(
                        onClick = { showManualDialog = true },
                        border = BorderStroke(1.dp, CarbonBorder),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .testTag("manual_ip_button")
                    ) {
                        Icon(Icons.Rounded.Link, contentDescription = null, tint = TextPrimary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = strings.manualIpConnect,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Discovered TVs Section
            item {
                Text(
                    text = strings.discoveredTvs,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (state.discoveredTvs.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CarbonSurface),
                        border = BorderStroke(1.dp, CarbonBorder),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Tv,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(34.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = strings.noTvFoundYet,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = strings.noTvTip,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            } else {
                items(state.discoveredTvs, key = { "disc_${it.id}" }) { tv ->
                    TvDeviceCard(
                        tv = tv,
                        connectLabel = strings.connectNow,
                        onConnect = {
                            onConnectTv(tv)
                            onOpenGamepad()
                        },
                        onRemove = null
                    )
                }
            }

            // Saved TVs
            if (state.settings.savedTvs.isNotEmpty()) {
                item {
                    Text(
                        text = strings.savedTvs,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                items(state.settings.savedTvs, key = { "saved_${it.id}" }) { tv ->
                    TvDeviceCard(
                        tv = tv,
                        connectLabel = strings.connectNow,
                        onConnect = {
                            onConnectTv(tv)
                            onOpenGamepad()
                        },
                        onRemove = { onRemoveSavedTv(tv) }
                    )
                }
            }

            // Dual Utility Row: TV Receiver Mode + Instant Latency Loopback Test
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CarbonSurface),
                        border = BorderStroke(1.dp, NeonIndigo.copy(alpha = 0.55f)),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.CastConnected,
                                    contentDescription = null,
                                    tint = NeonIndigo
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = strings.tvReceiverModeTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = strings.tvReceiverModeDesc,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                minLines = 3
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = onStartTvReceiver,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NeonIndigo,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("start_tv_receiver_button")
                            ) {
                                Text(strings.startTvReceiver, maxLines = 1)
                            }
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = CarbonSurface),
                        border = BorderStroke(1.dp, ButtonAEmerald.copy(alpha = 0.45f)),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Speed,
                                    contentDescription = null,
                                    tint = ButtonAEmerald
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = strings.loopbackTestTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = strings.loopbackTestDesc,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                minLines = 3
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = onStartLoopbackTest,
                                border = BorderStroke(1.dp, ButtonAEmerald),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("loopback_test_button")
                            ) {
                                Text(strings.runLoopbackTest, color = ButtonAEmerald, maxLines = 1)
                            }
                        }
                    }
                }
            }

            // Controller Settings Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CarbonSurface),
                    border = BorderStroke(1.dp, CarbonBorder),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.SettingsInputAntenna,
                                contentDescription = null,
                                tint = ElectricCyan
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = strings.controllerSettings,
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary
                            )
                        }

                        // Polling Rate
                        Text(
                            text = strings.pollingRate,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PollingRate.entries.forEach { rate ->
                                val selected = state.settings.pollingRate == rate
                                FilterChip(
                                    selected = selected,
                                    onClick = { onPollingRateChange(rate) },
                                    label = { Text(rate.label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ElectricCyan.copy(alpha = 0.2f),
                                        selectedLabelColor = ElectricCyan
                                    ),
                                    modifier = Modifier.testTag("polling_${rate.hz}_chip")
                                )
                            }
                        }

                        // Pad Layout
                        Text(
                            text = strings.layoutLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val layoutPairs = listOf(
                                ControllerLayout.STANDARD to strings.layoutStandard,
                                ControllerLayout.ARCADE to strings.layoutArcade,
                                ControllerLayout.RACING_GYRO to strings.layoutRacing
                            )
                            layoutPairs.forEach { (layout, label) ->
                                FilterChip(
                                    selected = state.settings.layout == layout,
                                    onClick = { onLayoutChange(layout) },
                                    label = { Text(label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = NeonIndigo.copy(alpha = 0.28f),
                                        selectedLabelColor = Color(0xFFD5D2FF)
                                    ),
                                    modifier = Modifier.testTag("layout_${layout.name.lowercase()}_chip")
                                )
                            }
                        }

                        // Button Symbol Style
                        Text(
                            text = strings.buttonStyleLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = state.settings.symbolStyle == ButtonSymbolStyle.XBOX_ABXY,
                                onClick = { onSymbolStyleChange(ButtonSymbolStyle.XBOX_ABXY) },
                                label = { Text("A B X Y") },
                                modifier = Modifier.testTag("symbol_abxy_chip")
                            )
                            FilterChip(
                                selected = state.settings.symbolStyle == ButtonSymbolStyle.PLAYSTATION_SHAPES,
                                onClick = { onSymbolStyleChange(ButtonSymbolStyle.PLAYSTATION_SHAPES) },
                                label = { Text("✕ ○ □ △") },
                                modifier = Modifier.testTag("symbol_ps_chip")
                            )
                        }

                        // Toggles
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(strings.hapticsLabel, color = TextPrimary)
                            Switch(
                                checked = state.settings.hapticsEnabled,
                                onCheckedChange = onHapticsChange,
                                colors = SwitchDefaults.colors(checkedTrackColor = ElectricCyan),
                                modifier = Modifier.testTag("haptics_switch")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(strings.autoLandscapeLabel, color = TextPrimary)
                            Switch(
                                checked = state.settings.autoLandscape,
                                onCheckedChange = onAutoLandscapeChange,
                                colors = SwitchDefaults.colors(checkedTrackColor = ElectricCyan),
                                modifier = Modifier.testTag("auto_landscape_switch")
                            )
                        }

                        // Deadzone Slider
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(strings.deadzoneLabel, color = TextSecondary)
                                Text(
                                    text = "${(state.settings.deadzone * 100).roundToInt()}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = ElectricCyan
                                )
                            }
                            Slider(
                                value = state.settings.deadzone,
                                onValueChange = onDeadzoneChange,
                                valueRange = 0.02f..0.28f,
                                colors = SliderDefaults.colors(
                                    thumbColor = ElectricCyan,
                                    activeTrackColor = ElectricCyan
                                ),
                                modifier = Modifier.testTag("deadzone_slider")
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    if (showManualDialog) {
        ManualIpDialog(
            strings = strings,
            defaultIp = state.networkStatus.gatewayCandidates.firstOrNull() ?: "192.168.43.100",
            onDismiss = { showManualDialog = false },
            onConfirm = { ip, port, name ->
                showManualDialog = false
                onManualConnect(ip, port, name)
                onOpenGamepad()
            }
        )
    }

    if (showGuideSheet) {
        ModalBottomSheet(
            onDismissRequest = { showGuideSheet = false },
            containerColor = CarbonSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = strings.howToConnectTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    color = ElectricCyan
                )
                Text(strings.step1Hotspot, color = TextPrimary)
                Text(strings.step2SameNetwork, color = TextPrimary)
                Text(strings.step3TapConnect, color = TextPrimary)

                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = ObsidianBg,
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, CarbonBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "OPEN BINARY UDP SPEC (NO INTERNET)",
                            style = MaterialTheme.typography.labelMedium,
                            color = ElectricCyan
                        )
                        Text(
                            text = "Game Port: UDP ${GamepadPacketCodec.GAMEPAD_UDP_PORT} | Discovery: UDP ${GamepadPacketCodec.DISCOVERY_UDP_PORT} & mDNS ${GamepadPacketCodec.NSD_SERVICE_TYPE}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                        Text(
                            text = "Packet (24B): [PP][0x01][0x01][Seq:i32][Btns:i32][LX,LY,RX,RY,LT,RT:i16]",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
            }
        }
    }
}

@Composable
private fun TvDeviceCard(
    tv: DiscoveredTv,
    connectLabel: String,
    onConnect: () -> Unit,
    onRemove: (() -> Unit)?
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CarbonElevated),
        border = BorderStroke(1.dp, CarbonBorder),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Tv,
                    contentDescription = tv.name,
                    tint = ElectricCyan,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = tv.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${tv.host}:${tv.port} • ${tv.transport}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onRemove != null) {
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier.testTag("remove_saved_tv_${tv.host}")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Remove saved TV",
                            tint = TextMuted
                        )
                    }
                }
                Button(
                    onClick = onConnect,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan,
                        contentColor = ObsidianBg
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("connect_tv_${tv.host}")
                ) {
                    Text(connectLabel, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ManualIpDialog(
    strings: AppStrings,
    defaultIp: String,
    onDismiss: () -> Unit,
    onConfirm: (ip: String, port: Int, name: String) -> Unit
) {
    var ip by remember { mutableStateOf(defaultIp) }
    var port by remember { mutableStateOf(GamepadPacketCodec.GAMEPAD_UDP_PORT.toString()) }
    var name by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CarbonSurface,
        title = { Text(strings.manualIpConnect, color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = ip,
                    onValueChange = {
                        ip = it
                        errorText = null
                    },
                    label = { Text(strings.tvIpAddress) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = errorText != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manual_ip_input")
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter { ch -> ch.isDigit() } },
                    label = { Text(strings.portLabel) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manual_port_input")
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(strings.tvNameOptional) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manual_name_input")
                )
                if (errorText != null) {
                    Text(
                        text = errorText!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = StatusOfflineRed
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanIp = ip.trim()
                    val parsedPort = port.toIntOrNull()?.coerceIn(1024, 65535)
                        ?: GamepadPacketCodec.GAMEPAD_UDP_PORT
                    if (!NetworkTopologyHelper.isValidIpv4(cleanIp)) {
                        errorText = strings.invalidIpError
                    } else {
                        onConfirm(cleanIp, parsedPort, name)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = ObsidianBg),
                modifier = Modifier.testTag("manual_connect_confirm_button")
            ) {
                Text(strings.connect, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel, color = TextSecondary)
            }
        }
    )
}
