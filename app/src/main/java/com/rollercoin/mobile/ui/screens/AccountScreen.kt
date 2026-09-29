package com.rollercoin.mobile.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rollercoin.mobile.AuthUiState
import com.rollercoin.mobile.DashboardUiState
import com.rollercoin.mobile.ui.components.SectionHeader
import com.rollercoin.mobile.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AccountScreen(
    authState: AuthUiState,
    dashboardState: DashboardUiState,
    onUserAgentChange: (String) -> Unit,
    onManualRefreshSession: () -> Unit,
    onToggleBatterySaver: (Boolean) -> Unit = {},
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showLogoutDialog by remember { mutableStateOf(false) }
    val profile = dashboardState.dashboard?.profile

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Konfirmasi Keluar") },
            text = { Text("Apakah Anda yakin ingin keluar dari akun RollerCoin? Token yang tersimpan di perangkat akan dihapus.") },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RcRed)
                ) {
                    Text("Keluar", color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showLogoutDialog = false }) {
                    Text("Batal")
                }
            },
            containerColor = RcSurface,
            titleContentColor = RcTextPrimary,
            textContentColor = RcTextSecondary
        )
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
                    text = "Profil & Pengaturan Sesi",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = RcTextPrimary
                )
                Text(
                    text = "Informasi akun RollerCoin, kredensial lokal, dan konfigurasi bot",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RcTextSecondary
                )
            }
        }

        // Active Session Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = RcSurface),
                border = BorderStroke(1.dp, RcGreen.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(RcGreen.copy(alpha = 0.2f))
                                    .border(1.5.dp, RcGreen, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = RcGreen,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = profile?.name ?: "Sesi Terhubung",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = RcTextPrimary
                                )
                                Text(
                                    text = profile?.email ?: authState.email.ifBlank { "Akun Aktif" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = RcTextSecondary
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = RcGreen.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, RcGreen.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "ONLINE",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = RcGreen
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = RcSurfaceBorder)
                    Spacer(modifier = Modifier.height(14.dp))

                    // User ID row with copy button
                    val userId = profile?.id ?: authState.userId
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = RcSurfaceElevated,
                        border = BorderStroke(1.dp, RcSurfaceBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (userId.isNotBlank()) {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("RollerCoin User ID", userId))
                                    Toast.makeText(context, "User ID disalin", Toast.LENGTH_SHORT).show()
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Badge, contentDescription = null, tint = RcCyan, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "User ID: ${userId.ifBlank { "-" }}",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = RcCyan
                                )
                            }
                            Icon(Icons.Default.ContentCopy, contentDescription = "Salin", tint = RcTextMuted, modifier = Modifier.size(14.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Open Profile in Browser Action
                    Button(
                        onClick = {
                            val raw = profile?.publicProfileLink?.trim().orEmpty()
                            val uid = userId.trim()
                            val url = when {
                                raw.startsWith("http://") || raw.startsWith("https://") -> raw
                                raw.startsWith("/") -> "https://rollercoin.com$raw"
                                raw.isNotBlank() -> "https://rollercoin.com/$raw"
                                uid.isNotBlank() -> "https://rollercoin.com/p/$uid"
                                else -> "https://rollercoin.com"
                            }
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                                Toast.makeText(context, "Membuka profil di browser...", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                try {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("RollerCoin Profile Link", url))
                                    Toast.makeText(context, "Tidak dapat membuka browser. Link disalin:\n$url", Toast.LENGTH_LONG).show()
                                } catch (_: Exception) {
                                    Toast.makeText(context, "Gagal membuka link: $url", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RcCyan.copy(alpha = 0.2f),
                            contentColor = RcCyan
                        ),
                        border = BorderStroke(1.dp, RcCyan.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("account_open_browser_profile_button")
                    ) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Lihat Profil di Browser", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        // Token & Auto-Renew Card
        item {
            SectionHeader(title = "Keamanan Token & Auto-Renew", icon = Icons.Default.VpnKey)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = RcSurface),
                border = BorderStroke(1.dp, RcSurfaceBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Penyimpanan Token Lokal",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = RcTextPrimary
                            )
                            Text(
                                text = "Tersimpan aman dengan Android Keystore (AES-GCM)",
                                style = MaterialTheme.typography.bodySmall,
                                color = RcTextSecondary
                            )
                        }

                        Icon(Icons.Default.Lock, contentDescription = null, tint = RcAmber, modifier = Modifier.size(20.dp))
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Status Refresh Token:", style = MaterialTheme.typography.labelSmall, color = RcTextSecondary)
                            Text(
                                text = if (authState.hasRefreshToken) "Tersedia (Auto-Renew Aktif)" else "Tidak Tersedia",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (authState.hasRefreshToken) RcGreen else RcTextMuted
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text("Masa Berlaku:", style = MaterialTheme.typography.labelSmall, color = RcTextSecondary)
                            val expText = if (authState.tokenExpiresAt > 0L) {
                                val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                                val expDate = Date(authState.tokenExpiresAt * 1000L)
                                fmt.format(expDate)
                            } else "Sesi aktif"
                            Text(
                                text = expText,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = RcCyan
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = onManualRefreshSession,
                        enabled = !authState.isBusy && authState.hasRefreshToken,
                        colors = ButtonDefaults.buttonColors(containerColor = RcCyan, contentColor = RcDarkBackground),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (authState.isBusy) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = RcDarkBackground)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Perbarui Token Sekarang (Refresh Token)", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Battery Saver Mode Section
        item {
            SectionHeader(title = "Efisiensi Daya & Penghemat Baterai", icon = Icons.Default.BatteryChargingFull)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = RcSurface),
                border = BorderStroke(1.dp, if (dashboardState.isBatterySaverEnabled) RcGreen.copy(alpha = 0.5f) else RcSurfaceBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "Mode Hemat Baterai (Polling)",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = RcTextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Mengurangi frekuensi polling data mining power saat aplikasi berada di latar belakang (dari 60s menjadi 180s) untuk efisiensi daya maksimal.",
                                style = MaterialTheme.typography.bodySmall,
                                color = RcTextSecondary
                            )
                        }

                        Switch(
                            checked = dashboardState.isBatterySaverEnabled,
                            onCheckedChange = onToggleBatterySaver,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = RcGreen
                            ),
                            modifier = Modifier.testTag("account_battery_saver_toggle")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = RcSurfaceBorder)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Status Aplikasi:", style = MaterialTheme.typography.labelSmall, color = RcTextSecondary)
                            Text(
                                text = if (dashboardState.isAppInForeground) "Di Depan (Foreground)" else "Latar Belakang (Background)",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (dashboardState.isAppInForeground) RcCyan else RcAmber
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text("Interval Polling Aktif:", style = MaterialTheme.typography.labelSmall, color = RcTextSecondary)
                            Text(
                                text = "${dashboardState.currentPollingIntervalSeconds} Detik",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (dashboardState.isBatterySaverEnabled) RcGreen else RcTextPrimary
                            )
                        }
                    }
                }
            }
        }

        // User-Agent configuration
        item {
            SectionHeader(title = "Header User-Agent", icon = Icons.Default.Settings)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = RcSurface),
                border = BorderStroke(1.dp, RcSurfaceBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "User-Agent yang dikirim ke server RollerCoin untuk menyamarkan request bot:",
                        style = MaterialTheme.typography.bodySmall,
                        color = RcTextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = authState.userAgent,
                        onValueChange = onUserAgentChange,
                        label = { Text("User-Agent Header") },
                        maxLines = 3,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = RcSurfaceElevated,
                            unfocusedContainerColor = RcSurfaceElevated,
                            focusedBorderColor = RcAmber,
                            unfocusedBorderColor = RcSurfaceBorder,
                            focusedTextColor = RcTextPrimary,
                            unfocusedTextColor = RcTextPrimary
                        )
                    )
                }
            }
        }

        // Logout Button Card
        item {
            Button(
                onClick = { showLogoutDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = RcRed.copy(alpha = 0.2f), contentColor = RcRed),
                border = BorderStroke(1.dp, RcRed.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("logout_button")
            ) {
                Icon(Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Keluar dari Akun RollerCoin", fontWeight = FontWeight.Bold)
            }
        }
    }
}
