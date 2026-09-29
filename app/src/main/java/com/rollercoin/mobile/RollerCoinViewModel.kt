package com.rollercoin.mobile

import android.app.Application
import android.graphics.BitmapFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CaptchaPoint(
    val x: Int,
    val y: Int,
    val ratioX: Float,
    val ratioY: Float,
)

data class AuthUiState(
    val email: String = "",
    val userAgent: String = DEFAULT_USER_AGENT,
    val otpCode: String = "",
    val manualToken: String = "",
    val captchaChallenge: CaptchaChallenge? = null,
    val captchaPoints: String = "",
    val captchaPointList: List<CaptchaPoint> = emptyList(),
    val otpRequested: Boolean = false,
    val isBusy: Boolean = false,
    val isLoggedIn: Boolean = false,
    val userId: String = "",
    val message: String? = null,
    val error: String? = null,
)

data class DashboardUiState(
    val isLoading: Boolean = false,
    val dashboard: Dashboard? = null,
    val error: String? = null,
)

data class ActiveGameUiState(
    val activeGame: ActiveGame? = null,
    val remainingSeconds: Int = 0,
    val progress: Float = 0f,
    val isSubmitting: Boolean = false,
    val lastFinished: FinishedGame? = null,
)

data class BotUiState(
    val isAutoRunning: Boolean = false,
    val statusMessage: String = "IDLE - Siap dijalankan",
    val delayBetweenGamesSeconds: Int = 5,
    val currentCycle: Int = 0,
    val cyclePower: Int = 0,
    val totalSessionPower: Long = 0L,
    val totalGamesPlayed: Int = 0,
    val cooldownWaitSeconds: Int = 0,
    val logs: List<BotLog> = emptyList(),
)

private const val DEFAULT_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 10; Redmi Note 7 Build/QKQ1.190910.002) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.7680.177 Mobile Safari/537.36"

class RollerCoinViewModel(application: Application) : AndroidViewModel(application) {
    private val store = TokenStore(application)
    private val repository = RollerCoinRepository()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private val _authUiState = MutableStateFlow(AuthUiState())
    val authUiState: StateFlow<AuthUiState> = _authUiState.asStateFlow()

    private val _dashboardUiState = MutableStateFlow(DashboardUiState())
    val dashboardUiState: StateFlow<DashboardUiState> = _dashboardUiState.asStateFlow()

    private val _activeGameUiState = MutableStateFlow(ActiveGameUiState())
    val activeGameUiState: StateFlow<ActiveGameUiState> = _activeGameUiState.asStateFlow()

    private val _botUiState = MutableStateFlow(BotUiState())
    val botUiState: StateFlow<BotUiState> = _botUiState.asStateFlow()

    private var otpRequest: OtpRequest? = null
    private var botJob: Job? = null

    init {
        restoreSavedState()
    }

    private fun restoreSavedState() {
        runCatching {
            val savedEmail = store.get("email")
            val savedAgent = store.get("userAgent").ifBlank { DEFAULT_USER_AGENT }
            val savedAccess = store.get("access")
            val savedRefresh = store.get("refresh")
            val savedDelay = store.getInt("botDelay", 5).coerceIn(3, 30)

            _botUiState.update { it.copy(delayBetweenGamesSeconds = savedDelay) }

            if (savedAccess.isNotBlank()) {
                repository.setCredentials(savedAccess, savedRefresh, savedAgent)
                _authUiState.update {
                    it.copy(
                        email = savedEmail,
                        userAgent = savedAgent,
                        isLoggedIn = true,
                        userId = repository.userId(),
                        message = "Sesi tersimpan dimuat",
                    )
                }
                refreshDashboard()
            } else {
                _authUiState.update {
                    it.copy(email = savedEmail, userAgent = savedAgent)
                }
            }
        }
    }

    fun updateEmail(value: String) {
        _authUiState.update { it.copy(email = value, error = null) }
    }

    fun updateUserAgent(value: String) {
        _authUiState.update { it.copy(userAgent = value, error = null) }
    }

    fun updateOtpCode(value: String) {
        _authUiState.update { it.copy(otpCode = value, error = null) }
    }

    fun updateManualToken(value: String) {
        _authUiState.update { it.copy(manualToken = value, error = null) }
    }

    fun updateCaptchaPoints(value: String) {
        _authUiState.update { it.copy(captchaPoints = value, error = null) }
    }

    fun addCaptchaPoint(x: Int, y: Int, ratioX: Float, ratioY: Float) {
        val currentList = _authUiState.value.captchaPointList.toMutableList()
        currentList.add(CaptchaPoint(x, y, ratioX, ratioY))
        val pointsStr = currentList.joinToString(",") { "${it.x},${it.y}" }
        _authUiState.update {
            it.copy(
                captchaPointList = currentList,
                captchaPoints = pointsStr,
                error = null,
                message = "Titik #${currentList.size} ditambahkan: ($x, $y)",
            )
        }
    }

    fun removeLastCaptchaPoint() {
        val currentList = _authUiState.value.captchaPointList.toMutableList()
        if (currentList.isNotEmpty()) {
            currentList.removeAt(currentList.size - 1)
            val pointsStr = currentList.joinToString(",") { "${it.x},${it.y}" }
            _authUiState.update {
                it.copy(
                    captchaPointList = currentList,
                    captchaPoints = pointsStr,
                    error = null,
                    message = if (currentList.isEmpty()) "Semua titik dihapus" else "Titik terakhir dihapus",
                )
            }
        }
    }

    fun clearCaptchaPoints() {
        _authUiState.update {
            it.copy(
                captchaPointList = emptyList(),
                captchaPoints = "",
                error = null,
                message = "Titik target direset",
            )
        }
    }

    fun setBotDelay(seconds: Int) {
        val bounded = seconds.coerceIn(3, 30)
        store.putInt("botDelay", bounded)
        _botUiState.update { it.copy(delayBetweenGamesSeconds = bounded) }
    }

    fun prepareCaptcha() {
        val email = _authUiState.value.email.trim()
        if (email.isBlank()) {
            _authUiState.update { it.copy(error = "Masukkan email akun RollerCoin") }
            return
        }
        viewModelScope.launch {
            _authUiState.update { it.copy(isBusy = true, error = null, message = "Memeriksa status CAPTCHA...") }
            try {
                val status = withContext(Dispatchers.IO) { repository.captchaStatus() }
                if (!status.required) {
                    _authUiState.update {
                        it.copy(
                            isBusy = false,
                            captchaChallenge = null,
                            captchaPointList = emptyList(),
                            captchaPoints = "",
                            message = "CAPTCHA tidak diperlukan. Langsung kirim OTP.",
                        )
                    }
                } else {
                    val challenge = status.challenge ?: throw ApiException("Challenge CAPTCHA kosong")
                    val fullChallenge = withContext(Dispatchers.IO) {
                        repository.createCaptcha(challenge, status.maxDots)
                    }

                    // Run automatic image analysis matching bot.php
                    val autoPoints = if (fullChallenge.image != null && fullChallenge.targetImage != null) {
                        withContext(Dispatchers.Default) {
                            val bitmap = BitmapFactory.decodeByteArray(fullChallenge.image, 0, fullChallenge.image.size)
                            val w = bitmap?.width ?: 1
                            val h = bitmap?.height ?: 1
                            val rawPoints = CaptchaAnalyzer.analyze(
                                fullChallenge.image,
                                fullChallenge.targetImage,
                                fullChallenge.maxDots,
                            )
                            rawPoints.map { (x, y) ->
                                CaptchaPoint(
                                    x = x,
                                    y = y,
                                    ratioX = (x.toFloat() / w).coerceIn(0f, 1f),
                                    ratioY = (y.toFloat() / h).coerceIn(0f, 1f),
                                )
                            }
                        }
                    } else emptyList()

                    val autoPointsStr = autoPoints.joinToString(",") { "${it.x},${it.y}" }

                    _authUiState.update {
                        it.copy(
                            isBusy = false,
                            captchaChallenge = fullChallenge,
                            captchaPointList = autoPoints,
                            captchaPoints = autoPointsStr,
                            message = if (autoPoints.isNotEmpty()) {
                                "Ikon target dan gambar dimuat. ${autoPoints.size} titik terdeteksi otomatis (dapat diklik langsung jika ingin diubah)."
                            } else {
                                "Ikon target dan gambar dimuat. Sentuh ikon pada gambar untuk memilih titik target."
                            },
                        )
                    }
                }
            } catch (e: Throwable) {
                _authUiState.update {
                    it.copy(isBusy = false, error = "Gagal memuat CAPTCHA: ${e.message}")
                }
            }
        }
    }

    fun autoDetectCaptchaPoints() {
        val challenge = _authUiState.value.captchaChallenge ?: return
        val img = challenge.image ?: return
        val thumb = challenge.targetImage ?: return
        viewModelScope.launch {
            _authUiState.update { it.copy(isBusy = true, error = null, message = "Menganalisis gambar CAPTCHA...") }
            try {
                val autoPoints = withContext(Dispatchers.Default) {
                    val bitmap = BitmapFactory.decodeByteArray(img, 0, img.size)
                    val w = bitmap?.width ?: 1
                    val h = bitmap?.height ?: 1
                    val rawPoints = CaptchaAnalyzer.analyze(img, thumb, challenge.maxDots)
                    rawPoints.map { (x, y) ->
                        CaptchaPoint(
                            x = x,
                            y = y,
                            ratioX = (x.toFloat() / w).coerceIn(0f, 1f),
                            ratioY = (y.toFloat() / h).coerceIn(0f, 1f),
                        )
                    }
                }
                val autoPointsStr = autoPoints.joinToString(",") { "${it.x},${it.y}" }
                _authUiState.update {
                    it.copy(
                        isBusy = false,
                        captchaPointList = autoPoints,
                        captchaPoints = autoPointsStr,
                        message = if (autoPoints.isNotEmpty()) {
                            "Analisis bot: ${autoPoints.size} titik terdeteksi: $autoPointsStr"
                        } else {
                            "Tidak ada titik otomatis terdeteksi, silakan ketuk manual pada gambar"
                        },
                    )
                }
            } catch (e: Throwable) {
                _authUiState.update {
                    it.copy(isBusy = false, error = "Analisis gagal: ${e.message}")
                }
            }
        }
    }

    fun validateCaptcha() {
        val challenge = _authUiState.value.captchaChallenge?.challenge
        if (challenge.isNullOrBlank()) {
            _authUiState.update { it.copy(error = "Siapkan CAPTCHA terlebih dahulu") }
            return
        }
        val points = _authUiState.value.captchaPoints.trim()
        if (points.isBlank()) {
            _authUiState.update { it.copy(error = "Masukkan koordinat titik CAPTCHA") }
            return
        }
        viewModelScope.launch {
            _authUiState.update { it.copy(isBusy = true, error = null, message = "Memvalidasi CAPTCHA...") }
            try {
                val ok = withContext(Dispatchers.IO) { repository.validateCaptcha(challenge, points) }
                if (ok) {
                    _authUiState.update {
                        it.copy(isBusy = false, message = "CAPTCHA valid! Tekan Kirim OTP sekarang.")
                    }
                } else {
                    _authUiState.update {
                        it.copy(isBusy = false, error = "CAPTCHA tidak valid. Coba siapkan challenge baru.")
                    }
                }
            } catch (e: Throwable) {
                _authUiState.update {
                    it.copy(isBusy = false, error = "Validasi CAPTCHA error: ${e.message}")
                }
            }
        }
    }

    fun requestOtp() {
        val email = _authUiState.value.email.trim()
        if (email.isBlank()) {
            _authUiState.update { it.copy(error = "Masukkan email terlebih dahulu") }
            return
        }
        viewModelScope.launch {
            _authUiState.update { it.copy(isBusy = true, error = null, message = "Mengirim OTP ke $email...") }
            try {
                val challenge = _authUiState.value.captchaChallenge?.challenge
                val res = withContext(Dispatchers.IO) { repository.requestOtp(email, challenge) }
                otpRequest = res
                _authUiState.update {
                    it.copy(
                        isBusy = false,
                        otpRequested = true,
                        message = "Kode OTP telah dikirim ke $email. Periksa inbox/spam.",
                    )
                }
            } catch (e: Throwable) {
                _authUiState.update {
                    it.copy(isBusy = false, error = "Gagal kirim OTP: ${e.message}")
                }
            }
        }
    }

    fun validateOtp() {
        val req = otpRequest
        if (req == null) {
            _authUiState.update { it.copy(error = "Tekan Kirim OTP terlebih dahulu") }
            return
        }
        val code = _authUiState.value.otpCode.trim()
        if (code.isBlank()) {
            _authUiState.update { it.copy(error = "Masukkan kode OTP dari email") }
            return
        }
        viewModelScope.launch {
            _authUiState.update { it.copy(isBusy = true, error = null, message = "Mengonfirmasi OTP...") }
            try {
                val tokens = withContext(Dispatchers.IO) {
                    repository.validateOtp(req.userId, req.codeId, code)
                }
                saveAuth(tokens.accessToken, tokens.refreshToken)
                _authUiState.update {
                    it.copy(
                        isBusy = false,
                        isLoggedIn = true,
                        userId = repository.userId(),
                        message = "Login berhasil!",
                    )
                }
                addLog("Login berhasil untuk user ${repository.userId()}", LogLevel.SUCCESS)
                refreshDashboard()
            } catch (e: Throwable) {
                _authUiState.update {
                    it.copy(isBusy = false, error = "Login gagal: ${e.message}")
                }
            }
        }
    }

    fun directTokenLogin() {
        val raw = _authUiState.value.manualToken.trim()
        val token = raw.removePrefix("Bearer ").trim()
        if (token.isBlank()) {
            _authUiState.update { it.copy(error = "Masukkan Bearer token RollerCoin") }
            return
        }
        repository.setCredentials(token, "", _authUiState.value.userAgent)
        val uid = repository.userId()
        if (uid.isBlank()) {
            _authUiState.update { it.copy(error = "Format token tidak valid (JWT user_id tidak ditemukan)") }
            return
        }
        saveAuth(token, "")
        _authUiState.update {
            it.copy(
                isLoggedIn = true,
                userId = uid,
                message = "Berhasil masuk dengan direct token",
                error = null,
            )
        }
        addLog("Tersambung dengan Bearer token (User ID: $uid)", LogLevel.SUCCESS)
        refreshDashboard()
    }

    private fun saveAuth(access: String, refresh: String) {
        store.put("access", access)
        if (refresh.isNotBlank()) store.put("refresh", refresh)
        store.put("email", _authUiState.value.email)
        store.put("userAgent", _authUiState.value.userAgent)
    }

    fun refreshDashboard() {
        if (!_authUiState.value.isLoggedIn) return
        viewModelScope.launch {
            _dashboardUiState.update { it.copy(isLoading = true, error = null) }
            try {
                val snapshot = withContext(Dispatchers.IO) { repository.loadDashboard() }
                _dashboardUiState.update {
                    it.copy(isLoading = false, dashboard = snapshot, error = null)
                }
            } catch (e: Throwable) {
                _dashboardUiState.update {
                    it.copy(isLoading = false, error = "Gagal memuat dashboard: ${e.message}")
                }
                addLog("Error dashboard: ${e.message}", LogLevel.ERROR)
            }
        }
    }

    private suspend fun runGameCountdown(active: ActiveGame): FinishedGame? {
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
            delay(500)
        }

        _activeGameUiState.update { it.copy(remainingSeconds = 0, progress = 1f, isSubmitting = true) }
        addLog("Mengirim hasil ${active.game.definition.name}...", LogLevel.INFO)

        return try {
            val finished = withContext(Dispatchers.IO) { repository.finishGame(active) }
            _activeGameUiState.update {
                it.copy(activeGame = null, isSubmitting = false, lastFinished = finished)
            }
            addLog(
                "✓ ${finished.gameName} SELESAI · Power: ${Formatters.hashPower(finished.actualPower)} (${finished.durationSeconds}s)",
                LogLevel.SUCCESS,
            )
            refreshDashboard()
            finished
        } catch (e: Throwable) {
            _activeGameUiState.update { it.copy(activeGame = null, isSubmitting = false) }
            addLog("✗ Gagal mengirim hasil: ${e.message}", LogLevel.ERROR)
            null
        }
    }

    fun startAutoBot() {
        if (_botUiState.value.isAutoRunning) return
        botJob?.cancel()
        _botUiState.update {
            it.copy(
                isAutoRunning = true,
                statusMessage = "Bot aktif · Menyiapkan putaran...",
            )
        }
        addLog("▶ AUTO-PLAY BOT DIMULAI", LogLevel.SUCCESS)

        botJob = viewModelScope.launch {
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

                // Ambil daftar game terbaru
                val gamesData: List<GameState> = try {
                    withContext(Dispatchers.IO) { repository.fetchGamesList() }
                } catch (e: Throwable) {
                    addLog("Gagal mengambil data game: ${e.message}", LogLevel.WARNING)
                    delay(15000)
                    continue
                }

                // Cek apakah ada game yang tersedia
                val availableGames = gamesData.filter { it.cooldownSeconds <= 0 }
                if (availableGames.isEmpty()) {
                    val minCooldown = gamesData.map { it.cooldownSeconds }.filter { it > 0 }.minOrNull() ?: 60
                    addLog("Semua game dalam cooldown. Menunggu ${Formatters.cooldown(minCooldown)}...", LogLevel.WARNING)

                    for (sec in minCooldown downTo 1) {
                        if (!_botUiState.value.isAutoRunning) break
                        _botUiState.update {
                            it.copy(
                                cooldownWaitSeconds = sec,
                                statusMessage = "Cooldown semua game: ${Formatters.timeRemaining(sec)}",
                            )
                        }
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

                    val finished = runGameCountdown(active)
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
                        delay(1000)
                    }
                }

                addLog("Putaran #$cycle selesai. Mined power: ${Formatters.hashPower(cyclePower)}", LogLevel.SUCCESS)
                delay(3000)
            }
        }
    }

    fun stopAutoBot() {
        botJob?.cancel()
        botJob = null
        _botUiState.update {
            it.copy(
                isAutoRunning = false,
                statusMessage = "IDLE - Bot dihentikan",
                cooldownWaitSeconds = 0,
            )
        }
        addLog("⏹ AUTO-PLAY BOT DIHENTIKAN", LogLevel.WARNING)
    }

    fun clearLogs() {
        _botUiState.update { it.copy(logs = emptyList()) }
    }

    fun logout() {
        stopAutoBot()
        repository.disconnect()
        store.clear()
        _authUiState.value = AuthUiState()
        _dashboardUiState.value = DashboardUiState()
        _activeGameUiState.value = ActiveGameUiState()
        addLog("Akun telah dikeluarkan", LogLevel.INFO)
    }

    private fun addLog(message: String, level: LogLevel = LogLevel.INFO) {
        val entry = BotLog(
            timestamp = timeFormat.format(Date()),
            message = message,
            level = level,
        )
        _botUiState.update {
            val list = (it.logs + entry).takeLast(100)
            it.copy(logs = list)
        }
    }

    override fun onCleared() {
        stopAutoBot()
        repository.disconnect()
        super.onCleared()
    }
}
