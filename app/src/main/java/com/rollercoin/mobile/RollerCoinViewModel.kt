package com.rollercoin.mobile

import android.app.Application
import android.content.Context
import android.graphics.BitmapFactory
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rollercoin.mobile.engine.BotEngine
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
    val manualRefreshToken: String = "",
    val captchaChallenge: CaptchaChallenge? = null,
    val captchaPoints: String = "",
    val captchaPointList: List<CaptchaPoint> = emptyList(),
    val otpRequested: Boolean = false,
    val isBusy: Boolean = false,
    val isCheckingSavedSession: Boolean = false,
    val isLoggedIn: Boolean = false,
    val userId: String = "",
    val hasRefreshToken: Boolean = false,
    val tokenExpiresAt: Long = 0L,
    val message: String? = null,
    val error: String? = null,
)

data class DashboardUiState(
    val isLoading: Boolean = false,
    val dashboard: Dashboard? = null,
    val error: String? = null,
    val lastPowerUpdateTime: Long = 0L,
    val isBatterySaverEnabled: Boolean = true,
    val isAppInForeground: Boolean = true,
    val currentPollingIntervalSeconds: Int = 30,
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

    val activeGameUiState: StateFlow<ActiveGameUiState> = BotEngine.activeGameUiState
    val botUiState: StateFlow<BotUiState> = BotEngine.botUiState
    val isFloatingBubbleEnabled: StateFlow<Boolean> = BotEngine.isFloatingBubbleEnabled
    val isBackgroundServiceEnabled: StateFlow<Boolean> = BotEngine.isBackgroundServiceEnabled

    private val _isBatterySaverEnabled = MutableStateFlow(true)
    val isBatterySaverEnabled: StateFlow<Boolean> = _isBatterySaverEnabled.asStateFlow()

    private val _isAppInForeground = MutableStateFlow(true)
    val isAppInForeground: StateFlow<Boolean> = _isAppInForeground.asStateFlow()

    private var powerPollingJob: Job? = null

    companion object {
        const val FOREGROUND_POLL_INTERVAL_SECONDS = 30
        const val BACKGROUND_NORMAL_POLL_INTERVAL_SECONDS = 60
        const val BACKGROUND_BATTERY_SAVER_POLL_INTERVAL_SECONDS = 180

        fun calculateInterval(inForeground: Boolean, batterySaverEnabled: Boolean, systemPowerSave: Boolean = false): Int {
            return when {
                inForeground -> FOREGROUND_POLL_INTERVAL_SECONDS
                batterySaverEnabled || systemPowerSave -> BACKGROUND_BATTERY_SAVER_POLL_INTERVAL_SECONDS
                else -> BACKGROUND_NORMAL_POLL_INTERVAL_SECONDS
            }
        }
    }

    private var otpRequest: OtpRequest? = null

    init {
        restoreSavedState()
    }

    private fun restoreSavedState() {
        viewModelScope.launch {
            try {
                val savedEmail = store.get("email")
                val savedAgent = store.get("userAgent").ifBlank { DEFAULT_USER_AGENT }
                val savedAccess = store.get("access")
                val savedRefresh = store.get("refresh")
                val savedDelay = store.getInt("botDelay", 5).coerceIn(3, 30)
                val savedBatterySaver = store.getBoolean("batterySaver", true)

                _isBatterySaverEnabled.value = savedBatterySaver
                BotEngine.setBatterySaverEnabled(savedBatterySaver)
                _dashboardUiState.update {
                    it.copy(
                        isBatterySaverEnabled = savedBatterySaver,
                        currentPollingIntervalSeconds = calculatePowerPollingIntervalSeconds(),
                    )
                }

                BotEngine.initialize(savedDelay)

                // Validasi lokal dilakukan hanya ketika ada token dan refresh token yang tersimpan
                val hasTokens = savedAccess.isNotBlank() && savedRefresh.isNotBlank()
                if (!hasTokens) {
                    // Jika tidak ada token atau refresh token, jangan validasi lokal, pengguna langsung di layar login
                    _authUiState.update {
                        it.copy(
                            email = savedEmail,
                            userAgent = savedAgent,
                            isCheckingSavedSession = false,
                            isLoggedIn = false,
                        )
                    }
                    return@launch
                }

                // Tampilkan indikator pemeriksaan sesi lokal karena ada token yang perlu divalidasi
                _authUiState.update {
                    it.copy(
                        email = savedEmail,
                        userAgent = savedAgent,
                        isCheckingSavedSession = true,
                        error = null,
                    )
                }

                repository.setCredentials(savedAccess, savedRefresh, savedAgent)
                var currentAccess = savedAccess
                var currentRefresh = savedRefresh

                // 1. Jika token lokal sudah expired, gunakan refresh token untuk memperbarui
                val isExpired = repository.isTokenExpired()
                if (isExpired) {
                    addLog("Token lokal kedaluwarsa. Memperbarui sesi otomatis menggunakan refresh token...", LogLevel.INFO)
                    val newTokens = withContext(Dispatchers.IO) {
                        runCatching { repository.refreshAccessToken() }.getOrNull()
                    }
                    if (newTokens != null) {
                        currentAccess = newTokens.accessToken
                        currentRefresh = newTokens.refreshToken
                        saveAuth(currentAccess, currentRefresh)
                        repository.setCredentials(currentAccess, currentRefresh, savedAgent)
                        addLog("Token berhasil diperbarui otomatis untuk user ${repository.userId()}", LogLevel.SUCCESS)
                    } else {
                        // Refresh token gagal -> sesi tidak valid, pengguna harus login ulang
                        invalidateSessionAndRequireLogin("Sesi telah kedaluwarsa dan refresh token tidak valid. Silakan login kembali.")
                        return@launch
                    }
                }

                // 2. Validasi sesi aktif ke server RollerCoin
                val isSessionValid = withContext(Dispatchers.IO) {
                    runCatching { repository.profile() }.isSuccess
                }

                if (isSessionValid) {
                    // Sesi valid! Pengguna masuk ke aplikasi
                    _authUiState.update {
                        it.copy(
                            email = savedEmail,
                            userAgent = savedAgent,
                            isLoggedIn = true,
                            isCheckingSavedSession = false,
                            userId = repository.userId(),
                            hasRefreshToken = true,
                            tokenExpiresAt = repository.tokenExpiresAt(),
                            message = "Sesi valid",
                            error = null,
                        )
                    }
                    addLog("Sesi lokal valid untuk user ${repository.userId()}. Masuk ke dashboard.", LogLevel.SUCCESS)
                    refreshDashboard()
                } else {
                    // Validasi profil gagal; coba refreshAccessToken sekali lagi jika belum
                    var recovered = false
                    if (!isExpired && currentRefresh.isNotBlank()) {
                        addLog("Verifikasi sesi gagal. Mencoba memperbarui via refresh token...", LogLevel.INFO)
                        val refreshed = withContext(Dispatchers.IO) {
                            runCatching { repository.refreshAccessToken() }.getOrNull()
                        }
                        if (refreshed != null) {
                            currentAccess = refreshed.accessToken
                            currentRefresh = refreshed.refreshToken
                            saveAuth(currentAccess, currentRefresh)
                            repository.setCredentials(currentAccess, currentRefresh, savedAgent)
                            val retryValid = withContext(Dispatchers.IO) {
                                runCatching { repository.profile() }.isSuccess
                            }
                            if (retryValid) {
                                recovered = true
                                _authUiState.update {
                                    it.copy(
                                        email = savedEmail,
                                        userAgent = savedAgent,
                                        isLoggedIn = true,
                                        isCheckingSavedSession = false,
                                        userId = repository.userId(),
                                        hasRefreshToken = true,
                                        tokenExpiresAt = repository.tokenExpiresAt(),
                                        message = "Sesi diperbarui & valid",
                                        error = null,
                                    )
                                }
                                addLog("Sesi berhasil diperbarui dan divalidasi.", LogLevel.SUCCESS)
                                refreshDashboard()
                            }
                        }
                    }

                    if (!recovered) {
                        // Sesi tidak valid -> hapus sesi dan arahkan pengguna untuk login ulang
                        invalidateSessionAndRequireLogin("Sesi lokal tidak valid atau telah kedaluwarsa. Silakan login kembali.")
                    }
                }
            } catch (e: Throwable) {
                invalidateSessionAndRequireLogin("Gagal memvalidasi sesi lokal: ${e.message}. Silakan login kembali.")
            }
        }
    }

    private fun invalidateSessionAndRequireLogin(errorMessage: String) {
        store.remove("access")
        store.remove("refresh")
        repository.disconnect()
        _authUiState.update {
            it.copy(
                isLoggedIn = false,
                isCheckingSavedSession = false,
                hasRefreshToken = false,
                userId = "",
                error = errorMessage,
            )
        }
        _dashboardUiState.update {
            it.copy(
                isLoading = false,
                dashboard = null,
                error = null,
            )
        }
        addLog(errorMessage, LogLevel.WARNING)
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

    fun updateManualRefreshToken(value: String) {
        _authUiState.update { it.copy(manualRefreshToken = value, error = null) }
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
                        hasRefreshToken = tokens.refreshToken.isNotBlank(),
                        tokenExpiresAt = repository.tokenExpiresAt(),
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
        val rawAccess = _authUiState.value.manualToken.trim()
        val access = rawAccess.removePrefix("Bearer ").trim()
        val refresh = _authUiState.value.manualRefreshToken.trim()
        if (access.isBlank()) {
            _authUiState.update { it.copy(error = "Masukkan Bearer token RollerCoin") }
            return
        }
        repository.setCredentials(access, refresh, _authUiState.value.userAgent)
        val uid = repository.userId()
        if (uid.isBlank()) {
            _authUiState.update { it.copy(error = "Format token tidak valid (JWT user_id tidak ditemukan)") }
            return
        }
        saveAuth(access, refresh)
        _authUiState.update {
            it.copy(
                isLoggedIn = true,
                userId = uid,
                hasRefreshToken = refresh.isNotBlank(),
                tokenExpiresAt = repository.tokenExpiresAt(),
                message = "Berhasil masuk dengan direct token",
                error = null,
            )
        }
        addLog("Tersambung dengan Bearer token (User ID: $uid)", LogLevel.SUCCESS)
        refreshDashboard()
    }

    fun manualRefreshSession() {
        if (!repository.hasRefreshToken()) {
            _authUiState.update { it.copy(error = "Refresh token tidak tersedia pada sesi ini") }
            return
        }
        viewModelScope.launch {
            _authUiState.update { it.copy(isBusy = true, error = null, message = "Memperbarui token...") }
            try {
                val newTokens = withContext(Dispatchers.IO) { repository.refreshAccessToken() }
                saveAuth(newTokens.accessToken, newTokens.refreshToken)
                _authUiState.update {
                    it.copy(
                        isBusy = false,
                        hasRefreshToken = true,
                        tokenExpiresAt = repository.tokenExpiresAt(),
                        message = "Token berhasil diperbarui!",
                    )
                }
                addLog("Token berhasil diperbarui via manual refresh", LogLevel.SUCCESS)
                refreshDashboard()
            } catch (e: Throwable) {
                _authUiState.update {
                    it.copy(isBusy = false, error = "Gagal memperbarui token: ${e.message}")
                }
                addLog("Gagal refresh token: ${e.message}", LogLevel.ERROR)
            }
        }
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
                // Pre-check: if access token has expired and refresh token is available, refresh it first
                if (repository.isTokenExpired() && repository.hasRefreshToken()) {
                    addLog("Token kedaluwarsa sebelum muat dashboard. Memperbarui otomatis...", LogLevel.INFO)
                    val newTokens = withContext(Dispatchers.IO) { repository.refreshAccessToken() }
                    saveAuth(newTokens.accessToken, newTokens.refreshToken)
                    _authUiState.update {
                        it.copy(
                            tokenExpiresAt = repository.tokenExpiresAt(),
                            hasRefreshToken = true,
                        )
                    }
                }
                val snapshot = withContext(Dispatchers.IO) { repository.loadDashboard() }
                _dashboardUiState.update {
                    it.copy(
                        isLoading = false,
                        dashboard = snapshot,
                        error = null,
                        lastPowerUpdateTime = System.currentTimeMillis(),
                        currentPollingIntervalSeconds = calculatePowerPollingIntervalSeconds(),
                    )
                }
                startPowerPolling()
            } catch (e: Throwable) {
                val isAuthErr = e.message?.contains("401") == true || e.message?.contains("token", ignoreCase = true) == true
                if (isAuthErr && repository.hasRefreshToken()) {
                    val refreshed = withContext(Dispatchers.IO) {
                        runCatching { repository.refreshAccessToken() }.getOrNull()
                    }
                    if (refreshed != null) {
                        saveAuth(refreshed.accessToken, refreshed.refreshToken)
                        _authUiState.update {
                            it.copy(tokenExpiresAt = repository.tokenExpiresAt())
                        }
                        val retrySnapshot = withContext(Dispatchers.IO) {
                            runCatching { repository.loadDashboard() }.getOrNull()
                        }
                        if (retrySnapshot != null) {
                            _dashboardUiState.update {
                                it.copy(
                                    isLoading = false,
                                    dashboard = retrySnapshot,
                                    error = null,
                                    lastPowerUpdateTime = System.currentTimeMillis(),
                                    currentPollingIntervalSeconds = calculatePowerPollingIntervalSeconds(),
                                )
                            }
                            startPowerPolling()
                            return@launch
                        }
                    }
                }
                if (isAuthErr) {
                    invalidateSessionAndRequireLogin("Sesi login telah kedaluwarsa. Silakan login kembali.")
                    return@launch
                }
                _dashboardUiState.update {
                    it.copy(isLoading = false, error = "Gagal memuat dashboard: ${e.message}")
                }
                addLog("Error dashboard: ${e.message}", LogLevel.ERROR)
            }
        }
    }

    fun calculatePowerPollingIntervalSeconds(): Int {
        val inForeground = _isAppInForeground.value
        val batterySaver = _isBatterySaverEnabled.value
        val powerManager = getApplication<Application>().getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isSystemPowerSave = powerManager?.isPowerSaveMode == true
        return calculateInterval(inForeground, batterySaver, isSystemPowerSave)
    }

    fun setAppForeground(inForeground: Boolean) {
        if (_isAppInForeground.value == inForeground) return
        val wasBackground = !_isAppInForeground.value
        _isAppInForeground.value = inForeground
        val intervalSec = calculatePowerPollingIntervalSeconds()
        _dashboardUiState.update {
            it.copy(
                isAppInForeground = inForeground,
                currentPollingIntervalSeconds = intervalSec,
            )
        }
        if (!inForeground && _isBatterySaverEnabled.value) {
            addLog("Mode Hemat Baterai aktif: Polling mining power di latar belakang diperlambat (${intervalSec}s)", LogLevel.INFO)
        } else if (inForeground) {
            addLog("Aplikasi di depan: Polling mining power berjalan normal (${intervalSec}s)", LogLevel.INFO)
            if (wasBackground) {
                // Instantly refresh power upon returning to foreground
                fetchMiningPowerOnce()
            }
        }
        startPowerPolling()
    }

    fun fetchMiningPowerOnce() {
        if (!_authUiState.value.isLoggedIn) return
        viewModelScope.launch {
            try {
                if (repository.isTokenExpired() && repository.hasRefreshToken()) {
                    val newTokens = withContext(Dispatchers.IO) {
                        runCatching { repository.refreshAccessToken() }.getOrNull()
                    }
                    if (newTokens != null) {
                        saveAuth(newTokens.accessToken, newTokens.refreshToken)
                        _authUiState.update { it.copy(tokenExpiresAt = repository.tokenExpiresAt()) }
                    }
                }
                val powerSnapshot = withContext(Dispatchers.IO) {
                    repository.fetchPowerInfo()
                }
                if (powerSnapshot != null) {
                    _dashboardUiState.update { current ->
                        val updatedDashboard = current.dashboard?.copy(power = powerSnapshot)
                        current.copy(
                            dashboard = updatedDashboard,
                            lastPowerUpdateTime = System.currentTimeMillis(),
                        )
                    }
                }
            } catch (e: Throwable) {
                // Ignore transient network errors
            }
        }
    }

    fun toggleBatterySaver(enabled: Boolean) {
        _isBatterySaverEnabled.value = enabled
        store.putBoolean("batterySaver", enabled)
        BotEngine.setBatterySaverEnabled(enabled)
        val intervalSec = calculatePowerPollingIntervalSeconds()
        _dashboardUiState.update {
            it.copy(
                isBatterySaverEnabled = enabled,
                currentPollingIntervalSeconds = intervalSec,
            )
        }
        addLog(
            if (enabled) "Mode Hemat Baterai diaktifkan: Polling mining power di latar belakang dikurangi (${BACKGROUND_BATTERY_SAVER_POLL_INTERVAL_SECONDS}s)"
            else "Mode Hemat Baterai dinonaktifkan: Polling background normal (${BACKGROUND_NORMAL_POLL_INTERVAL_SECONDS}s)",
            LogLevel.INFO
        )
        startPowerPolling()
    }

    fun startPowerPolling() {
        powerPollingJob?.cancel()
        if (!_authUiState.value.isLoggedIn) return

        powerPollingJob = viewModelScope.launch {
            while (_authUiState.value.isLoggedIn) {
                val intervalSec = calculatePowerPollingIntervalSeconds()
                _dashboardUiState.update {
                    it.copy(
                        currentPollingIntervalSeconds = intervalSec,
                        isBatterySaverEnabled = _isBatterySaverEnabled.value,
                        isAppInForeground = _isAppInForeground.value,
                    )
                }
                delay(intervalSec * 1000L)

                if (!_authUiState.value.isLoggedIn) break

                try {
                    // Pre-check token expiration before polling
                    if (repository.isTokenExpired() && repository.hasRefreshToken()) {
                        val newTokens = withContext(Dispatchers.IO) {
                            runCatching { repository.refreshAccessToken() }.getOrNull()
                        }
                        if (newTokens != null) {
                            saveAuth(newTokens.accessToken, newTokens.refreshToken)
                            _authUiState.update { it.copy(tokenExpiresAt = repository.tokenExpiresAt()) }
                        }
                    }

                    val powerSnapshot = withContext(Dispatchers.IO) {
                        repository.fetchPowerInfo()
                    }
                    if (powerSnapshot != null) {
                        _dashboardUiState.update { current ->
                            val updatedDashboard = current.dashboard?.copy(power = powerSnapshot)
                            current.copy(
                                dashboard = updatedDashboard,
                                lastPowerUpdateTime = System.currentTimeMillis(),
                            )
                        }
                    }
                } catch (e: Throwable) {
                    // Periodic polling handles errors silently
                }
            }
        }
    }

    fun startAutoBot() {
        BotEngine.startBot(getApplication(), repository) {
            refreshDashboard()
        }
    }

    fun stopAutoBot() {
        BotEngine.stopBot(getApplication())
    }

    fun setBotDelay(delaySeconds: Int) {
        BotEngine.setDelay(delaySeconds, store)
    }

    fun clearLogs() {
        BotEngine.clearLogs()
    }

    fun toggleFloatingBubble(enabled: Boolean) {
        BotEngine.setFloatingBubbleEnabled(enabled, getApplication())
    }

    fun toggleBackgroundService(enabled: Boolean) {
        BotEngine.setBackgroundServiceEnabled(enabled)
    }

    fun addLog(message: String, level: LogLevel = LogLevel.INFO) {
        BotEngine.addLog(message, level)
    }

    fun logout() {
        stopAutoBot()
        powerPollingJob?.cancel()
        powerPollingJob = null
        repository.disconnect()
        store.clear()
        _authUiState.value = AuthUiState(
            isCheckingSavedSession = false,
            isLoggedIn = false
        )
        _dashboardUiState.value = DashboardUiState()
        addLog("Akun telah dikeluarkan", LogLevel.INFO)
    }

    override fun onCleared() {
        // Note: we don't automatically kill the bot on ViewModel clear if background service is enabled,
        // but if user logged out or explicitly stopped, it is stopped.
        powerPollingJob?.cancel()
        powerPollingJob = null
        repository.disconnect()
        super.onCleared()
    }
}
