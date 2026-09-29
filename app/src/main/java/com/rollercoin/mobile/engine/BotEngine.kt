package com.rollercoin.mobile.engine

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.rollercoin.mobile.*
import com.rollercoin.mobile.service.RollerCoinBotService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BotEngine {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var botJob: Job? = null
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private val _botUiState = MutableStateFlow(BotUiState())
    val botUiState: StateFlow<BotUiState> = _botUiState.asStateFlow()

    private val _activeGameUiState = MutableStateFlow(ActiveGameUiState())
    val activeGameUiState: StateFlow<ActiveGameUiState> = _activeGameUiState.asStateFlow()

    private val _isFloatingBubbleEnabled = MutableStateFlow(true)
    val isFloatingBubbleEnabled: StateFlow<Boolean> = _isFloatingBubbleEnabled.asStateFlow()

    private val _isBackgroundServiceEnabled = MutableStateFlow(true)
    val isBackgroundServiceEnabled: StateFlow<Boolean> = _isBackgroundServiceEnabled.asStateFlow()

    private var repositoryRef: RollerCoinRepository? = null
    private var onRefreshDashboardCallback: (() -> Unit)? = null

    fun initialize(savedDelay: Int, floatingBubbleEnabled: Boolean = true, backgroundEnabled: Boolean = true) {
        _botUiState.update { it.copy(delayBetweenGamesSeconds = savedDelay.coerceIn(3, 30)) }
        _isFloatingBubbleEnabled.value = floatingBubbleEnabled
        _isBackgroundServiceEnabled.value = backgroundEnabled
    }

    fun setFloatingBubbleEnabled(enabled: Boolean, context: Context?) {
        _isFloatingBubbleEnabled.value = enabled
        if (context != null && _botUiState.value.isAutoRunning) {
            val intent = Intent(context, RollerCoinBotService::class.java).apply {
                action = if (enabled) RollerCoinBotService.ACTION_SHOW_BUBBLE else RollerCoinBotService.ACTION_HIDE_BUBBLE
            }
            context.startService(intent)
        }
    }

    fun setBackgroundServiceEnabled(enabled: Boolean) {
        _isBackgroundServiceEnabled.value = enabled
    }

    fun setDelay(delaySeconds: Int, store: TokenStore) {
        val delayClamped = delaySeconds.coerceIn(3, 30)
        _botUiState.update { it.copy(delayBetweenGamesSeconds = delayClamped) }
        store.putInt("botDelay", delayClamped)
    }

    fun clearLogs() {
        _botUiState.update { it.copy(logs = emptyList()) }
    }

    fun addLog(text: String, level: LogLevel = LogLevel.INFO) {
        val timestamp = timeFormat.format(Date())
        val newLog = BotLog(
            id = System.nanoTime(),
            timestamp = timestamp,
            message = text,
            level = level,
        )
        _botUiState.update { current ->
            val updated = current.logs + newLog
            val trimmed = if (updated.size > 200) updated.takeLast(150) else updated
            current.copy(logs = trimmed)
        }
    }

    fun startBot(
        context: Context,
        repository: RollerCoinRepository,
        onRefreshDashboard: () -> Unit
    ) {
        if (_botUiState.value.isAutoRunning) return
        repositoryRef = repository
        onRefreshDashboardCallback = onRefreshDashboard

        botJob?.cancel()
        _botUiState.update {
            it.copy(
                isAutoRunning = true,
                statusMessage = "Bot aktif · Menyiapkan putaran game...",
            )
        }
        addLog("▶ AUTO-PLAY BOT DIMULAI", LogLevel.SUCCESS)

        // Launch Foreground Service for background execution & floating bubble
        if (_isBackgroundServiceEnabled.value) {
            val serviceIntent = Intent(context, RollerCoinBotService::class.java).apply {
                action = RollerCoinBotService.ACTION_START
            }
            ContextCompat.startForegroundService(context, serviceIntent)
        }

        botJob = scope.launch {
            runBotLoop(context, repository, onRefreshDashboard)
        }
    }

    fun stopBot(context: Context?) {
        botJob?.cancel()
        botJob = null
        _botUiState.update {
            it.copy(
                isAutoRunning = false,
                statusMessage = "Bot dihentikan",
                cooldownWaitSeconds = 0,
            )
        }
        _activeGameUiState.update {
            it.copy(activeGame = null, remainingSeconds = 0, isSubmitting = false)
        }
        addLog("⏹ AUTO-PLAY BOT DIHENTIKAN", LogLevel.WARNING)

        if (context != null) {
            val serviceIntent = Intent(context, RollerCoinBotService::class.java).apply {
                action = RollerCoinBotService.ACTION_STOP
            }
            context.startService(serviceIntent)
        }
    }

    private suspend fun runBotLoop(
        context: Context,
        repository: RollerCoinRepository,
        onRefreshDashboard: () -> Unit
    ) {
        var cycle = _botUiState.value.currentCycle
        var totalPower = _botUiState.value.totalSessionPower
        var totalGames = _botUiState.value.totalGamesPlayed

        while (_botUiState.value.isAutoRunning) {
            cycle++
            var cyclePower = 0
            _botUiState.update {
                it.copy(
                    currentCycle = cycle,
                    cyclePower = 0,
                    statusMessage = "Putaran #$cycle · Memeriksa data game...",
                )
            }
            addLog("── Putaran #$cycle Dimulai ──", LogLevel.INFO)
            RollerCoinBotService.updateNotification(context, "Putaran #$cycle Dimulai", "Memeriksa status game...")

            // Ambil daftar game terbaru
            val gamesData: List<GameState> = try {
                withContext(Dispatchers.IO) { repository.fetchGamesList() }
            } catch (e: Throwable) {
                addLog("Gagal mengambil data game: ${e.message}", LogLevel.WARNING)
                repository.disconnect()
                delay(15000)
                continue
            }

            // Cek apakah ada game yang tersedia
            val availableGames = gamesData.filter { it.cooldownSeconds <= 0 }
            if (availableGames.isEmpty()) {
                val minCooldown = gamesData.map { it.cooldownSeconds }.filter { it > 0 }.minOrNull() ?: 60
                addLog("Semua game dalam cooldown. Menunggu ${Formatters.cooldown(minCooldown)}...", LogLevel.WARNING)
                repository.disconnect()

                for (sec in minCooldown downTo 1) {
                    if (!_botUiState.value.isAutoRunning) break
                    _botUiState.update {
                        it.copy(
                            cooldownWaitSeconds = sec,
                            statusMessage = "Cooldown semua game: ${Formatters.timeRemaining(sec)}",
                        )
                    }
                    RollerCoinBotService.updateNotification(
                        context,
                        "Menunggu Cooldown",
                        "${Formatters.timeRemaining(sec)} · Putaran #$cycle"
                    )
                    delay(1000)
                }
                _botUiState.update { it.copy(cooldownWaitSeconds = 0) }
                continue
            }

            // Mainkan semua game yang tidak cooldown
            for (gameState in availableGames) {
                if (!_botUiState.value.isAutoRunning) break

                _botUiState.update {
                    it.copy(statusMessage = "Memainkan #${gameState.definition.number} ${gameState.definition.name}...")
                }
                addLog("Memulai #${gameState.definition.number} ${gameState.definition.name} (Level ${gameState.level})...", LogLevel.INFO)

                val active = try {
                    withContext(Dispatchers.IO) { repository.startGame(gameState) }
                } catch (e: Throwable) {
                    addLog("Skip ${gameState.definition.name}: ${e.message}", LogLevel.WARNING)
                    delay(2000)
                    continue
                }

                val finished = runGameCountdown(context, repository, active, onRefreshDashboard)
                if (finished != null) {
                    cyclePower += finished.actualPower
                    totalPower += finished.actualPower
                    totalGames++
                    _botUiState.update {
                        it.copy(
                            cyclePower = cyclePower,
                            totalSessionPower = totalPower,
                            totalGamesPlayed = totalGames,
                        )
                    }
                }

                if (!_botUiState.value.isAutoRunning) break

                val delaySec = _botUiState.value.delayBetweenGamesSeconds
                addLog("Jeda antar game: ${delaySec}s", LogLevel.INFO)
                for (d in delaySec downTo 1) {
                    if (!_botUiState.value.isAutoRunning) break
                    _botUiState.update {
                        it.copy(statusMessage = "Jeda berikutnya: ${d}s")
                    }
                    RollerCoinBotService.updateNotification(
                        context,
                        "Jeda Game: ${d}s",
                        "Game dimainkan: $totalGames · Power: ${Formatters.hashPower(totalPower.toInt())}"
                    )
                    delay(1000)
                }
            }

            addLog("Mengambil data cooldown terbaru dari server...", LogLevel.INFO)
            val freshGames = try {
                withContext(Dispatchers.IO) { repository.fetchGamesList() }
            } catch (e: Throwable) {
                emptyList()
            }
            val remainingCooldown = freshGames.map { it.cooldownSeconds }.filter { it > 0 }.minOrNull() ?: 15
            if (remainingCooldown > 0 && _botUiState.value.isAutoRunning) {
                addLog("Jeda antar putaran: ${remainingCooldown}s...", LogLevel.INFO)
                for (sec in remainingCooldown downTo 1) {
                    if (!_botUiState.value.isAutoRunning) break
                    _botUiState.update {
                        it.copy(
                            cooldownWaitSeconds = sec,
                            statusMessage = "Menunggu putaran #$cycle berikutnya: ${Formatters.timeRemaining(sec)}",
                        )
                    }
                    RollerCoinBotService.updateNotification(
                        context,
                        "Cooldown Putaran #$cycle",
                        "${Formatters.timeRemaining(sec)} · Total Mined: ${Formatters.hashPower(totalPower.toInt())}"
                    )
                    delay(1000)
                }
                _botUiState.update { it.copy(cooldownWaitSeconds = 0) }
            }
        }
    }

    private suspend fun runGameCountdown(
        context: Context,
        repository: RollerCoinRepository,
        active: ActiveGame,
        onRefreshDashboard: () -> Unit
    ): FinishedGame? {
        _activeGameUiState.update {
            it.copy(
                activeGame = active,
                remainingSeconds = active.totalDurationSeconds,
                progress = 0f,
                isSubmitting = false,
            )
        }
        val total = active.totalDurationSeconds
        val endAt = System.currentTimeMillis() + total * 1000L

        while (true) {
            val remainingMs = endAt - System.currentTimeMillis()
            if (remainingMs <= 0) break
            val sec = (remainingMs / 1000L).toInt() + 1
            val elapsed = total - (remainingMs.toFloat() / 1000f)
            val pct = (elapsed / total.toFloat()).coerceIn(0f, 1f)
            _activeGameUiState.update {
                it.copy(remainingSeconds = sec, progress = pct)
            }
            RollerCoinBotService.updateNotification(
                context,
                "Bermain: ${active.game.definition.name}",
                "Sisa waktu: ${sec}s · Target Power: ${Formatters.hashPower(active.targetPower)}"
            )
            delay(500)
        }

        _activeGameUiState.update { it.copy(remainingSeconds = 0, progress = 1f, isSubmitting = true) }
        addLog("Mengirim hasil ${active.game.definition.name}...", LogLevel.INFO)
        RollerCoinBotService.updateNotification(
            context,
            "Mengirim Hasil...",
            "${active.game.definition.name} selesai dimainkan"
        )

        return try {
            val finished = withContext(Dispatchers.IO) { repository.finishGame(active) }
            _activeGameUiState.update {
                it.copy(activeGame = null, isSubmitting = false, lastFinished = finished)
            }
            addLog(
                "✓ ${finished.gameName} SELESAI · Power: ${Formatters.hashPower(finished.actualPower)} (${finished.durationSeconds}s)",
                LogLevel.SUCCESS,
            )
            withContext(Dispatchers.Main) {
                onRefreshDashboard()
            }
            finished
        } catch (e: Throwable) {
            _activeGameUiState.update { it.copy(activeGame = null, isSubmitting = false) }
            addLog("✗ Gagal mengirim hasil: ${e.message}", LogLevel.ERROR)
            null
        }
    }
}
