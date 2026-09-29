package com.rollercoin.mobile.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.rollercoin.mobile.ActiveGameUiState
import com.rollercoin.mobile.BotUiState
import com.rollercoin.mobile.Formatters
import com.rollercoin.mobile.R
import com.rollercoin.mobile.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun FloatingGameBubble(
    botState: BotUiState,
    activeState: ActiveGameUiState,
    onStopBot: () -> Unit,
    onOpenBotTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!botState.isAutoRunning) return

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val bubbleSizePx = with(density) { 58.dp.toPx() }

    var offsetX by remember { mutableFloatStateOf(screenWidthPx - bubbleSizePx - 24f) }
    var offsetY by remember { mutableFloatStateOf(screenHeightPx * 0.35f) }
    var showInfoDialog by remember { mutableStateOf(false) }

    // Pulsing animation when a game is actively playing
    val isGameActive = activeState.activeGame != null
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isGameActive) 1.14f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bubblePulse"
    )

    // Semi-transparent floating ball positioned on screen side
    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // Ensure outside clicks are not blocked except on bubble itself
            }
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .size(58.dp)
                .scale(pulseScale)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offsetX = (offsetX + dragAmount.x).coerceIn(12f, screenWidthPx - bubbleSizePx - 12f)
                        offsetY = (offsetY + dragAmount.y).coerceIn(80f, screenHeightPx - bubbleSizePx - 140f)
                    }
                }
                .clip(CircleShape)
                // Semi-transparent dark circular ball (80% opacity)
                .background(Color(0xCC0F172A))
                // Glowing border
                .border(
                    BorderStroke(
                        2.dp,
                        Brush.linearGradient(
                            if (isGameActive) listOf(RcGreen, RcCyan) else listOf(RcAmber, Color(0xFFF97316))
                        )
                    ),
                    CircleShape
                )
                .clickable { showInfoDialog = true }
                .testTag("floating_game_ball"),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_rollercoin_logo),
                contentDescription = "RollerCoin Floating Ball",
                modifier = Modifier.size(34.dp)
            )

            // Status indicator dot
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(if (isGameActive) RcGreen else RcAmber)
                    .border(1.5.dp, Color.Black, CircleShape)
            )
        }
    }

    // Modal Info Dialog when the floating ball is clicked
    if (showInfoDialog) {
        FloatingGameInfoDialog(
            botState = botState,
            activeState = activeState,
            onDismiss = { showInfoDialog = false },
            onStopBot = {
                showInfoDialog = false
                onStopBot()
            },
            onOpenBotTab = {
                showInfoDialog = false
                onOpenBotTab()
            }
        )
    }
}

@Composable
fun FloatingGameInfoDialog(
    botState: BotUiState,
    activeState: ActiveGameUiState,
    onDismiss: () -> Unit,
    onStopBot: () -> Unit,
    onOpenBotTab: () -> Unit
) {
    val active = activeState.activeGame

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xF00B132B)), // Translucent tech dark
            border = BorderStroke(1.5.dp, RcAmber)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(RcAmber.copy(alpha = 0.2f))
                                .border(1.dp, RcAmber, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_rollercoin_logo),
                                contentDescription = null,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "PLAY GAME INFO",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp
                                ),
                                color = RcAmber
                            )
                            Text(
                                text = "Monitoring Real-Time Game",
                                style = MaterialTheme.typography.labelSmall,
                                color = RcTextSecondary
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(RcSurfaceElevated)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Active Game Box
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    border = BorderStroke(
                        1.dp,
                        if (active != null) RcGreen.copy(alpha = 0.6f) else RcSurfaceBorder
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
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
                                        .background(if (active != null) RcGreen else RcAmber)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = active?.game?.definition?.name ?: (if (botState.cooldownWaitSeconds > 0) "Cooldown Semua Game" else "Standby"),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                            }

                            if (active != null) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = RcGreen.copy(alpha = 0.2f),
                                    border = BorderStroke(1.dp, RcGreen.copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = "LEVEL ${active.game.level}",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = RcGreenLight
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = if (active != null) {
                                if (activeState.isSubmitting) "Menyelesaikan skor dan validasi token..."
                                else "Sedang dimainkan otomatis oleh bot"
                            } else {
                                botState.statusMessage
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = RcCyan
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Progress bar & Countdown
                        if (active != null) {
                            LinearProgressIndicator(
                                progress = { activeState.progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = RcGreen,
                                trackColor = Color(0xFF0F172A)
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Target Power: ${Formatters.hashPower(active.targetPower)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = RcTextSecondary
                                )
                                Text(
                                    text = "${activeState.remainingSeconds}s tersisa",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = RcGreenLight
                                )
                            }
                        } else if (botState.cooldownWaitSeconds > 0) {
                            Text(
                                text = "Sisa Waktu Tunggu: ${Formatters.timeRemaining(botState.cooldownWaitSeconds)}",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = RcAmber
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Session Summary Tiles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                        border = BorderStroke(1.dp, RcSurfaceBorder)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Dimainkan", style = MaterialTheme.typography.labelSmall, color = RcTextSecondary)
                            Text(
                                text = "${botState.totalGamesPlayed} game",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = RcAmber
                            )
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                        border = BorderStroke(1.dp, RcSurfaceBorder)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Total Mined", style = MaterialTheme.typography.labelSmall, color = RcTextSecondary)
                            Text(
                                text = Formatters.hashPower(botState.totalSessionPower.toInt()),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = RcGreen
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Putaran #${botState.currentCycle} · Power Putaran: ${Formatters.hashPower(botState.cyclePower)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = RcTextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onOpenBotTab,
                        colors = ButtonDefaults.buttonColors(containerColor = RcCyan, contentColor = RcDarkBackground),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.SmartToy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Tab Bot", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Button(
                        onClick = onStopBot,
                        colors = ButtonDefaults.buttonColors(containerColor = RcRed, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Hentikan", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
