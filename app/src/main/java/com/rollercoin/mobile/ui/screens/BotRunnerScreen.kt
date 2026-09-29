package com.rollercoin.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rollercoin.mobile.*
import com.rollercoin.mobile.ui.components.LogItemView
import com.rollercoin.mobile.ui.components.SectionHeader
import com.rollercoin.mobile.ui.components.StatTile
import com.rollercoin.mobile.ui.theme.*

@Composable
fun BotRunnerScreen(
    botState: BotUiState,
    isLoggedIn: Boolean,
    isFloatingBubbleEnabled: Boolean,
    isBackgroundServiceEnabled: Boolean,
    onStartBot: () -> Unit,
    onStopBot: () -> Unit,
    onSetDelay: (Int) -> Unit,
    onToggleFloatingBubble: (Boolean) -> Unit,
    onToggleBackgroundService: (Boolean) -> Unit,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val logListState = rememberLazyListState()

    var hasOverlayPermission by remember {
        mutableStateOf(android.provider.Settings.canDrawOverlays(context))
    }

    // Auto-scroll logs to bottom on new entries
    LaunchedEffect(botState.logs.size) {
        if (botState.logs.isNotEmpty()) {
            logListState.animateScrollToItem(botState.logs.size - 1)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Auto-Play Bot Runner",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = RcTextPrimary
                )
                Text(
                    text = "Automatisasi permainan mini-game dengan rotasi cooldown",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RcTextSecondary
                )
            }
        }

        // Bot Control Hero Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = RcSurface),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(
                        if (botState.isAutoRunning) RcGreen else RcSurfaceBorder
                    )
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(if (botState.isAutoRunning) RcGreen else RcTextMuted)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (botState.isAutoRunning) "BOT AKTIF" else "BOT STANDBY",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                ),
                                color = if (botState.isAutoRunning) RcGreen else RcTextMuted
                            )
                        }

                        if (botState.cooldownWaitSeconds > 0) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = RcAmber.copy(alpha = 0.2f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, RcAmber.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "Wait: ${Formatters.timeRemaining(botState.cooldownWaitSeconds)}",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = RcAmber
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = botState.statusMessage,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = RcTextPrimary
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Start / Stop Action Button
                    Button(
                        onClick = {
                            if (botState.isAutoRunning) onStopBot() else onStartBot()
                        },
                        enabled = isLoggedIn,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (botState.isAutoRunning) RcRed else RcGreen,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("bot_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (botState.isAutoRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (botState.isAutoRunning) "HENTIKAN AUTO-PLAY" else "MULAI AUTO-PLAY BOT",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    if (!isLoggedIn) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Login terlebih dahulu di tab Akun untuk mengaktifkan bot",
                            style = MaterialTheme.typography.bodySmall,
                            color = RcAmber
                        )
                    }
                }
            }
        }

        // Stats Matrix
        item {
            SectionHeader(title = "Statistik Sesi", icon = Icons.Default.QueryStats)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatTile(
                    label = "Total Dimainkan",
                    value = "${botState.totalGamesPlayed} game",
                    icon = Icons.Default.SportsEsports,
                    accentColor = RcAmber,
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    label = "Mined Power",
                    value = Formatters.hashPower(botState.totalSessionPower.toInt()),
                    icon = Icons.Default.ElectricBolt,
                    accentColor = RcGreen,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatTile(
                    label = "Putaran Saat Ini",
                    value = "Cycle #${botState.currentCycle}",
                    icon = Icons.Default.Sync,
                    accentColor = RcCyan,
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    label = "Power Putaran",
                    value = Formatters.hashPower(botState.cyclePower),
                    icon = Icons.Default.TrendingUp,
                    accentColor = RcPurple,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Delay configuration
        item {
            SectionHeader(title = "Pengaturan Jeda Game", icon = Icons.Default.Timer)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = RcSurface),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(RcSurfaceBorder))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Jeda Antar Game:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = RcTextSecondary
                        )
                        Text(
                            text = "${botState.delayBetweenGamesSeconds} detik",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = RcAmber
                        )
                    }

                    Slider(
                        value = botState.delayBetweenGamesSeconds.toFloat(),
                        onValueChange = { onSetDelay(it.toInt()) },
                        valueRange = 3f..20f,
                        steps = 17,
                        colors = SliderDefaults.colors(
                            thumbColor = RcAmber,
                            activeTrackColor = RcAmber,
                            inactiveTrackColor = RcSurfaceBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("3s (Cepat)", style = MaterialTheme.typography.labelSmall, color = RcTextMuted)
                        Text("20s (Aman)", style = MaterialTheme.typography.labelSmall, color = RcTextMuted)
                    }
                }
            }
        }

        // Background Running & Floating Ball Configuration
        item {
            SectionHeader(title = "Mode Latar Belakang & Bola Mengambang", icon = Icons.Default.FlipToFront)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = RcSurface),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(RcSurfaceBorder))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Floating Bubble Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xCC0F172A))
                                        .border(1.5.dp, RcAmber, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(RcGreen))
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Bola Mengambang (Floating Ball)",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = RcTextPrimary
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Menampilkan bola mengambang semi-transparan di samping layar. Klik bola untuk melihat progres game realtime.",
                                style = MaterialTheme.typography.bodySmall,
                                color = RcTextSecondary
                            )
                        }

                        Switch(
                            checked = isFloatingBubbleEnabled,
                            onCheckedChange = onToggleFloatingBubble,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = RcAmber
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = RcSurfaceBorder)
                    Spacer(modifier = Modifier.height(14.dp))

                    // Background Service Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "Jalankan di Latar Belakang (Foreground Service)",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = RcTextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Bot tetap berjalan otomatis saat aplikasi diminimalkan atau membuka aplikasi lain.",
                                style = MaterialTheme.typography.bodySmall,
                                color = RcTextSecondary
                            )
                        }

                        Switch(
                            checked = isBackgroundServiceEnabled,
                            onCheckedChange = onToggleBackgroundService,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = RcGreen
                            )
                        )
                    }

                    // System Overlay Permission Banner (if not yet granted)
                    if (!hasOverlayPermission && isFloatingBubbleEnabled) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, RcCyan.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Info, contentDescription = null, tint = RcCyan, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Izin Tampil di Luar Aplikasi",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = RcCyan
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Agar bola mengambang tetap muncul di atas aplikasi lain dan layar beranda, berikan izin \"Tampilkan di atas aplikasi lain\".",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = RcTextSecondary
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = {
                                        val intent = android.content.Intent(
                                            android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            android.net.Uri.parse("package:${context.packageName}")
                                        )
                                        context.startActivity(intent)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = RcCyan, contentColor = RcDarkBackground),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Buka Pengaturan Izin Overlay", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Terminal Logs Viewer
        item {
            SectionHeader(
                title = "Live Activity Logs",
                icon = Icons.Default.Terminal,
                action = {
                    TextButton(onClick = onClearLogs) {
                        Text(text = "Hapus Log", color = RcTextMuted, style = MaterialTheme.typography.labelMedium)
                    }
                }
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF070B14)),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(RcSurfaceBorder))
            ) {
                if (botState.logs.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Log aktivitas bot akan tampil di sini...",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = RcTextMuted
                        )
                    }
                } else {
                    LazyColumn(
                        state = logListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                    ) {
                        items(botState.logs, key = { it.id }) { log ->
                            LogItemView(log)
                        }
                    }
                }
            }
        }
    }
}
