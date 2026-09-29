package com.rollercoin.mobile.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.TrendingUp
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rollercoin.mobile.*
import com.rollercoin.mobile.ui.components.SectionHeader
import com.rollercoin.mobile.ui.components.StatTile
import com.rollercoin.mobile.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    dashboardState: DashboardUiState,
    authState: AuthUiState,
    onRefresh: () -> Unit,
    onNavigateToBot: () -> Unit,
    onManualRefreshSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dashboard = dashboardState.dashboard
    val profile = dashboard?.profile
    val power = dashboard?.power
    val pc = dashboard?.pcConfig
    val currencies = dashboard?.currencies ?: emptyList()
    val games = dashboard?.games ?: emptyList()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "RollerCoin Dashboard",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = RcTextPrimary
                    )
                    Text(
                        text = "Statistik penambangan, profil akun & saldo kripto",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RcTextSecondary
                    )
                }

                IconButton(
                    onClick = onRefresh,
                    enabled = !dashboardState.isLoading,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(RcSurface)
                        .testTag("refresh_dashboard_button")
                ) {
                    if (dashboardState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = RcAmber,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Muat Ulang",
                            tint = RcAmber
                        )
                    }
                }
            }
        }

        // Error message if any
        if (dashboardState.error != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = RcRed.copy(alpha = 0.15f)),
                    border = BorderStroke(1.dp, RcRed.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = RcRed
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = dashboardState.error,
                            style = MaterialTheme.typography.bodySmall,
                            color = RcTextPrimary
                        )
                    }
                }
            }
        }

        // 1. Comprehensive Profile Hero Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = RcSurface),
                border = BorderStroke(
                    1.dp,
                    if (profile?.active != false) RcSurfaceBorder else RcRed.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    // Profile Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(
                                            listOf(RcAmber.copy(alpha = 0.4f), Color(0xFF1E293B))
                                        )
                                    )
                                    .border(2.dp, RcAmber, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = (profile?.name?.take(2)?.uppercase()) ?: "RC",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = RcAmber
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = profile?.name ?: "Pemain RollerCoin",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = RcTextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = profile?.email ?: authState.email.ifBlank { "Menyambungkan..." },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = RcTextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Status badges column
                        Column(horizontalAlignment = Alignment.End) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (profile?.active != false) RcGreen.copy(alpha = 0.15f) else RcRed.copy(alpha = 0.2f),
                                border = BorderStroke(
                                    1.dp,
                                    if (profile?.active != false) RcGreen.copy(alpha = 0.5f) else RcRed
                                )
                            ) {
                                Text(
                                    text = if (profile?.active != false) "AKTIF" else "DIBEKUKAN",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (profile?.active != false) RcGreen else RcRed
                                )
                            }

                            if (profile?.premium == true) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = RcAmber.copy(alpha = 0.2f),
                                    border = BorderStroke(1.dp, RcAmber)
                                ) {
                                    Text(
                                        text = "★ VIP PASS",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = RcAmber
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // User ID & Rank Row
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
                                    Toast.makeText(context, "User ID disalin ke clipboard", Toast.LENGTH_SHORT).show()
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Badge, contentDescription = null, tint = RcCyan, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "UID: ${if (userId.isNotBlank()) userId else "-"}",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = RcCyan
                                )
                            }
                            Icon(Icons.Default.ContentCopy, contentDescription = "Salin UID", tint = RcTextMuted, modifier = Modifier.size(14.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = RcSurfaceBorder, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(14.dp))

                    // Detailed Profile Metrics Grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Global Rank
                        Column {
                            Text(text = "Peringkat Global", style = MaterialTheme.typography.labelSmall, color = RcTextSecondary)
                            Text(
                                text = if ((profile?.rank ?: 0L) > 0L) "#${profile?.rank}" else "Leaderboard",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = RcAmber
                            )
                        }

                        // Miners & Racks
                        Column {
                            Text(text = "Miner / Rak", style = MaterialTheme.typography.labelSmall, color = RcTextSecondary)
                            Text(
                                text = "${profile?.miners ?: 0} / ${profile?.racks ?: 0}",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = RcTextPrimary
                            )
                        }

                        // Bonus Power %
                        Column {
                            Text(text = "Bonus Power", style = MaterialTheme.typography.labelSmall, color = RcTextSecondary)
                            Text(
                                text = profile?.bonusPowerPercent?.let { String.format(Locale.US, "+%.2f%%", it) } ?: "+0.00%",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = RcGreen
                            )
                        }

                        // Referrals
                        Column {
                            Text(text = "Referral", style = MaterialTheme.typography.labelSmall, color = RcTextSecondary)
                            Text(
                                text = "${profile?.referrals ?: 0}",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = RcCyan
                            )
                        }
                    }

                    // Registration & League details
                    // Registration & League details + Browser Profile Action
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Terdaftar: ${profile?.registration?.take(10) ?: "N/A"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = RcTextMuted
                        )

                        Button(
                            onClick = {
                                val raw = profile?.publicProfileLink?.trim().orEmpty()
                                val uid = (profile?.id ?: authState.userId).trim()
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
                                        Toast.makeText(context, "Tidak dapat membuka browser. Link disalin ke clipboard:\n$url", Toast.LENGTH_LONG).show()
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
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("open_profile_browser_button")
                        ) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Lihat Profil di Browser", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 2. Hash Power Breakdown & Realtime Analytics
        item {
            SectionHeader(title = "Hash Power & Penambangan", icon = Icons.Default.ElectricBolt)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = RcSurface),
                border = BorderStroke(1.dp, RcSurfaceBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Highlights: Total & Net Power
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatTile(
                            label = "Total Hashpower",
                            value = power?.let { Formatters.hashPowerGh(it.total) } ?: "-",
                            icon = Icons.Default.Speed,
                            accentColor = RcCyan,
                            modifier = Modifier.weight(1f)
                        )
                        StatTile(
                            label = "Net Hashpower",
                            value = power?.let { Formatters.hashPowerGh(it.net) } ?: "-",
                            icon = Icons.AutoMirrored.Filled.TrendingUp,
                            accentColor = RcGreen,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = RcSurfaceBorder)
                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Rincian Sumber Hash Power:",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = RcTextPrimary
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Power Split Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(RcAmber))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Game Power", style = MaterialTheme.typography.labelSmall, color = RcTextSecondary)
                            }
                            Text(
                                text = power?.let { Formatters.hashPowerGh(it.gamesPower) } ?: "-",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = RcAmber
                            )
                        }

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(RcCyan))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Miner Power", style = MaterialTheme.typography.labelSmall, color = RcTextSecondary)
                            }
                            Text(
                                text = power?.let { Formatters.hashPowerGh(it.minersPower) } ?: "-",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = RcCyan
                            )
                        }

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(RcGreen))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Penalty / Potongan", style = MaterialTheme.typography.labelSmall, color = RcTextSecondary)
                            }
                            Text(
                                text = power?.let { if (it.penalty > 0L) Formatters.hashPowerGh(it.penalty) else "0 Gh/s" } ?: "0 Gh/s",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = if ((power?.penalty ?: 0L) > 0L) RcRed else RcTextPrimary
                            )
                        }
                    }

                    // Visual Progress Bar
                    val totalP = power?.total ?: 0L
                    if (totalP > 0L) {
                        Spacer(modifier = Modifier.height(12.dp))
                        val gamesRatio = ((power?.gamesPower ?: 0L).toFloat() / totalP.toFloat()).coerceIn(0f, 1f)
                        val minersRatio = ((power?.minersPower ?: 0L).toFloat() / totalP.toFloat()).coerceIn(0f, 1f)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(RcSurfaceElevated)
                        ) {
                            if (gamesRatio > 0f) {
                                Box(
                                    modifier = Modifier
                                        .weight(gamesRatio.coerceAtLeast(0.01f))
                                        .fillMaxHeight()
                                        .background(RcAmber)
                                )
                            }
                            if (minersRatio > 0f) {
                                Box(
                                    modifier = Modifier
                                        .weight(minersRatio.coerceAtLeast(0.01f))
                                        .fillMaxHeight()
                                        .background(RcCyan)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. PC Miner System Status
        item {
            SectionHeader(title = "PC Miner & Durasi Power", icon = Icons.Default.Computer)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = RcSurface),
                border = BorderStroke(1.dp, RcSurfaceBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(RcGreen.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Dns, contentDescription = null, tint = RcGreen, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = pc?.name ?: "Roller PC",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = RcTextPrimary
                                )
                                Text(
                                    text = "Masa Aktif Power: ${pc?.powerHoldingDays ?: 1} Hari",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = RcGreen
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = RcAmber.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, RcAmber.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "LEVEL ${pc?.level ?: 1} / ${pc?.maxLevel ?: 4}",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = RcAmber
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Progress indicators
                    val currentLvl = pc?.level ?: 1
                    val maxLvl = pc?.maxLevel ?: 4
                    LinearProgressIndicator(
                        progress = { (currentLvl.toFloat() / maxLvl.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = RcGreen,
                        trackColor = RcSurfaceBorder
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if ((pc?.gamesToNextLevel ?: 0) > 0)
                                "Mainkan ${pc?.gamesToNextLevel} game lagi untuk naik tier"
                            else "Tier PC Tertinggi (Maksimal)",
                            style = MaterialTheme.typography.bodySmall,
                            color = RcTextSecondary
                        )

                        if (!pc?.expireDate.isNullOrBlank()) {
                            Text(
                                text = "Reset: ${pc?.expireDate?.take(10)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = RcAmber
                            )
                        }
                    }
                }
            }
        }

        // 4. Crypto Currencies & Wallets
        if (currencies.isNotEmpty()) {
            item {
                SectionHeader(title = "Dompet Kripto & Token RollerCoin", icon = Icons.Default.AccountBalanceWallet)

                // Highlight Native Tokens first (RLT & RST)
                val nativeTokens = currencies.filter { it.code in listOf("RLT", "RST") }
                val otherCryptos = currencies.filter { it.code !in listOf("RLT", "RST") }

                if (nativeTokens.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        nativeTokens.forEach { token ->
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = RcSurface),
                                border = BorderStroke(1.5.dp, if (token.code == "RLT") RcAmber else RcCyan),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = token.code,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = if (token.code == "RLT") RcAmber else RcCyan
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = (if (token.code == "RLT") RcAmber else RcCyan).copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                text = "OFFICIAL",
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = if (token.code == "RLT") RcAmber else RcCyan
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = Formatters.currencyBalance(token.balance, token.toSmall, 4),
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                        color = RcTextPrimary
                                    )
                                    Text(
                                        text = token.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = RcTextSecondary
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Other Cryptocurrencies Carousel
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(otherCryptos) { cur ->
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = RcSurface),
                            border = BorderStroke(1.dp, RcSurfaceBorder),
                            modifier = Modifier.width(140.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = cur.code,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = RcTextPrimary
                                    )
                                    Icon(
                                        imageVector = Icons.Default.CurrencyBitcoin,
                                        contentDescription = null,
                                        tint = RcAmber,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = Formatters.currencyBalance(cur.balance, cur.toSmall, 6),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = RcCyan,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = cur.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = RcTextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // 5. Mini-Games & Auto-Bot Quick Card
        item {
            val availableCount = games.count { it.cooldownSeconds <= 0 }
            SectionHeader(
                title = "15 Mini-Games & Auto-Bot",
                icon = Icons.Default.SportsEsports,
                action = {
                    TextButton(onClick = onNavigateToBot) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Buka Bot", color = RcAmber, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = RcAmber, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = RcSurface),
                border = BorderStroke(1.dp, RcSurfaceBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "$availableCount dari 15 Game Siap Dimainkan",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = RcTextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (availableCount > 0)
                                    "Auto-Bot siap menyelesaikan putaran game otomatis untuk memperkuat hash power Anda!"
                                else "Semua game sedang dalam masa tunggu (cooldown). Bot akan otomatis menunggu giliran.",
                                style = MaterialTheme.typography.bodySmall,
                                color = RcTextSecondary
                            )
                        }

                        Button(
                            onClick = onNavigateToBot,
                            colors = ButtonDefaults.buttonColors(containerColor = RcAmber, contentColor = RcDarkBackground),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.SmartToy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Jalankan Bot", fontWeight = FontWeight.Bold)
                        }
                    }

                    // Available games chips
                    val readyGames = games.filter { it.cooldownSeconds <= 0 }
                    if (readyGames.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(readyGames.take(6)) { g ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = RcSurfaceElevated,
                                    border = BorderStroke(1.dp, RcAmber.copy(alpha = 0.3f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = g.definition.name,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = RcTextPrimary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "+${Formatters.hashPower(GameCatalog.reward(g))}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = RcGreen
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 6. Security & Token Session Status
        item {
            SectionHeader(title = "Status Sesi & Auto-Renew", icon = Icons.Default.Shield)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                border = BorderStroke(1.dp, RcSurfaceBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = RcGreen, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Sesi Aktif & Tersimpan Lokal",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = RcTextPrimary
                            )
                        }

                        if (authState.hasRefreshToken) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = RcGreen.copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, RcGreen.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = "Auto-Renew Aktif",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = RcGreenLight
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val expiryText = if (authState.tokenExpiresAt > 0L) {
                        val fmt = SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale.getDefault())
                        val expDate = Date(authState.tokenExpiresAt * 1000L)
                        val remainingSec = (authState.tokenExpiresAt - System.currentTimeMillis() / 1000L).coerceAtLeast(0L)
                        val hours = remainingSec / 3600
                        val mins = (remainingSec % 3600) / 60
                        "Berlaku s/d: ${fmt.format(expDate)} (${hours}j ${mins}m lagi)"
                    } else {
                        "Token aktif (Auto-refresh siap saat kedaluwarsa)"
                    }

                    Text(
                        text = expiryText,
                        style = MaterialTheme.typography.bodySmall,
                        color = RcCyan
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    if (authState.hasRefreshToken) {
                        OutlinedButton(
                            onClick = onManualRefreshSession,
                            enabled = !authState.isBusy,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Autorenew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Perbarui Token Sekarang (Manual Refresh)")
                        }
                    }
                }
            }
        }
    }
}
