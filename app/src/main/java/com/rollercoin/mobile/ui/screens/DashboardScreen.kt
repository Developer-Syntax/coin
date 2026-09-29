package com.rollercoin.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rollercoin.mobile.*
import com.rollercoin.mobile.ui.components.SectionHeader
import com.rollercoin.mobile.ui.components.StatTile
import com.rollercoin.mobile.ui.theme.*

@Composable
fun DashboardScreen(
    dashboardState: DashboardUiState,
    onRefresh: () -> Unit,
    onNavigateToBot: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dashboard = dashboardState.dashboard

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
                        text = "Statistik penambangan & status akun",
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
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(RcRed.copy(alpha = 0.4f)))
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

        // Profile Overview Card
        item {
            val profile = dashboard?.profile
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = RcSurface),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(RcSurfaceBorder))
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
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(RcAmber.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = RcAmber,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = profile?.name ?: "Memuat profil...",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = RcTextPrimary
                                )
                                Text(
                                    text = profile?.email ?: "Menyambungkan...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = RcTextSecondary
                                )
                            }
                        }

                        // Active / Banned Pill
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (profile?.active != false) RcGreen.copy(alpha = 0.15f) else RcRed.copy(alpha = 0.2f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (profile?.active != false) RcGreen.copy(alpha = 0.4f) else RcRed
                            )
                        ) {
                            Text(
                                text = if (profile?.active != false) "ACTIVE" else "BANNED",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (profile?.active != false) RcGreen else RcRed
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = RcSurfaceBorder, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(text = "Jumlah Miner", style = MaterialTheme.typography.labelSmall, color = RcTextSecondary)
                            Text(
                                text = "${profile?.miners ?: 0}",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = RcTextPrimary
                            )
                        }
                        Column {
                            Text(text = "Status Akun", style = MaterialTheme.typography.labelSmall, color = RcTextSecondary)
                            Text(
                                text = if (profile?.premium == true) "Premium" else "Free",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (profile?.premium == true) RcAmber else RcTextPrimary
                            )
                        }
                        Column {
                            Text(text = "League ID", style = MaterialTheme.typography.labelSmall, color = RcTextSecondary)
                            Text(
                                text = profile?.leagueId ?: "N/A",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = RcPurple
                            )
                        }
                    }
                }
            }
        }

        // Hash Power Section
        item {
            SectionHeader(title = "Hash Power Info", icon = Icons.Default.ElectricBolt)
            val power = dashboard?.power
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatTile(
                    label = "Total Power",
                    value = power?.let { Formatters.hashPowerGh(it.total) } ?: "-",
                    icon = Icons.Default.Speed,
                    accentColor = RcCyan,
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    label = "Net Power",
                    value = power?.let { Formatters.hashPowerGh(it.net) } ?: "-",
                    icon = Icons.Default.TrendingUp,
                    accentColor = RcGreen,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // PC Config
        item {
            val pc = dashboard?.pcConfig
            SectionHeader(title = "PC Miner Config", icon = Icons.Default.Computer)
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
                            text = pc?.name ?: "Basic PC",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = RcTextPrimary
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = RcAmber.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, RcAmber.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "LEVEL ${pc?.level ?: 0} / ${pc?.maxLevel ?: 4}",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = RcAmber
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(text = "Next Level", style = MaterialTheme.typography.labelSmall, color = RcTextSecondary)
                            Text(
                                text = if ((pc?.gamesToNextLevel ?: 0) > 0) "${pc?.gamesToNextLevel} games lagi" else "MAX LEVEL",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = RcCyan
                            )
                        }
                        Column {
                            Text(text = "Power Hold", style = MaterialTheme.typography.labelSmall, color = RcTextSecondary)
                            Text(
                                text = "${pc?.powerHoldingDays ?: 1} Hari",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = RcGreen
                            )
                        }
                    }
                }
            }
        }

        // Mini Games Info / Bot Launcher
        item {
            val availableCount = dashboard?.games?.count { it.cooldownSeconds <= 0 } ?: 0
            SectionHeader(
                title = "Mini-Games RollerCoin",
                icon = Icons.Default.SportsEsports,
                action = {
                    TextButton(onClick = onNavigateToBot) {
                        Text(text = "Ke Auto-Bot", color = RcAmber, fontWeight = FontWeight.Bold)
                    }
                }
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = RcSurface),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(RcSurfaceBorder))
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "$availableCount dari 15 Game Siap Dimainkan",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = RcTextPrimary
                        )
                        Text(
                            text = if (availableCount > 0) "Jalankan Auto-Bot untuk memainkan game otomatis & menambah power!" else "Semua game sedang cooldown",
                            style = MaterialTheme.typography.bodySmall,
                            color = RcTextSecondary
                        )
                    }

                    Button(
                        onClick = onNavigateToBot,
                        colors = ButtonDefaults.buttonColors(containerColor = RcAmber, contentColor = RcDarkBackground),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Auto-Bot", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Currencies Section
        val currencies = dashboard?.currencies ?: emptyList()
        if (currencies.isNotEmpty()) {
            item {
                SectionHeader(title = "Mata Uang & Token", icon = Icons.Default.MonetizationOn)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(currencies.take(8)) { cur ->
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = RcSurface),
                            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(RcSurfaceBorder)),
                            modifier = Modifier.width(130.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = cur.code,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = RcAmber
                                )
                                Text(
                                    text = cur.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = RcTextSecondary,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
