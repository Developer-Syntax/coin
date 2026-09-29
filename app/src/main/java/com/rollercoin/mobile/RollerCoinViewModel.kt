package com.rollercoin.mobile

import android.app.Application
import android.graphics.BitmapFactory
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
    val isCheckingSavedSession: Boolean = true,
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

    private var otpRequest: OtpRequest? = null

    init {
        restoreSavedState()
    }

    private fun restoreSavedState() {
        viewModelScope.launch {
            _authUiState.update { it.copy(isCheckingSavedSession = true) }
            try {
                val savedEmail = store.get("email")
                val savedAgent = store.get("userAgent").ifBlank { DEFAULT_USER_AGENT }
                val savedAccess = store.get("access")
                val savedRefresh = store.get("refresh")
                val savedDelay = store.getInt("botDelay", 5).coerceIn(3, 30)

                BotEngine.initialize(savedDelay)

                if (savedAccess.isNotBlank()) {
                    val isExpired = TokenUtils.isExpired(savedAccess)

                    if (isExpired && savedRefresh.isNotBlank()) {
                        // Access token expired, attempt automatic silent refresh with stored refresh token
                        repository.setCredentials(savedAccess, savedRefresh, savedAgent)
                        addLog("Token lokal kedaluwarsa. Memperbarui sesi otomatis menggunakan refresh token...", LogLevel.INFO)
                        val newTokens = withContext(Dispatchers.IO) {
                            runCatching { repository.refreshAccessToken() }.getOrNull()
                        }
                        if (newTokens != null) {
                            saveAuth(newTokens.accessToken, newTokens.refreshToken)
                            _authUiState.update {
                                it.copy(
                                    email = savedEmail,
                                    userAgent = savedAgent,
                                    isLoggedIn = true,
                                    isCheckingSavedSession = false,
                                    userId = repository.userId(),
                                    hasRefreshToken = true,
                                    tokenExpiresAt = repository.tokenExpiresAt(),
                                    message = "Sesi berhasil diperbarui otomatis dari refresh token",
                                )
                            }
                            addLog("Token berhasil diperbarui otomatis untuk user ${repository.userId()}", LogLevel.SUCCESS)
                            refreshDashboard()
                            return@launch
                        } else {
                            // Refresh failed
                            _authUiState.update {
                                it.copy(
                                    email = savedEmail,
                                    userAgent = savedAgent,
                                    isLoggedIn = false,
                                    isCheckingSavedSession = false,
                                    error = "Sesi telah kedaluwarsa dan refresh token tidak valid. Silakan login kembali.",
                                )
                            }
                            return@launch
                        }
                    } else if (isExpired && savedRefresh.isBlank()) {
                        // Expired without refresh token
                        _authUiState.update {
                            it.copy(
                                email = savedEmail,
                                userAgent = savedAgent,
                                isLoggedIn = false,
                                isCheckingSavedSession = false,
                                error = "Token lokal telah kedaluwarsa. Silakan masukkan token baru atau login via OTP.",
                            )
                        }
                        return@launch
                    }

                    // Token is not expired!
                    repository.setCredentials(savedAccess, savedRefresh, savedAgent)
                    _authUiState.update {
                        it.copy(
                            email = savedEmail,
                            userAgent = savedAgent,
                            isLoggedIn = true,
                            isCheckingSavedSession = false,
                            userId = repository.userId(),
                            hasRefreshToken = savedRefresh.isNotBlank(),
                            tokenExpiresAt = repository.tokenExpiresAt(),
                            message = "Sesi tersimpan dimuat",
                        )
                    }
                    refreshDashboard()
                } else {
                    _authUiState.update {
                        it.copy(
                            email = savedEmail,
                            userAgent = savedAgent,
                            isCheckingSavedSession = false,
                            isLoggedIn = false,
                        )
                    }
                }
            } catch (e: Throwable) {
                _authUiState.update {
                    it.copy(
                        isCheckingSavedSession = false,
                        isLoggedIn = false,
                        error = "Gagal memulihkan sesi: ${e.message}",
                    )
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
                    it.copy(isLoading = false, dashboard = snapshot, error = null)
                }
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
                                it.copy(isLoading = false, dashboard = retrySnapshot, error = null)
                            }
                            return@launch
                        }
                    }
                }
                _dashboardUiState.update {
                    it.copy(isLoading = false, error = "Gagal memuat dashboard: ${e.message}")
                }
                addLog("Error dashboard: ${e.message}", LogLevel.ERROR)
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
        repository.disconnect()
        store.clear()
        _authUiState.value = AuthUiState()
        _dashboardUiState.value = DashboardUiState()
        addLog("Akun telah dikeluarkan", LogLevel.INFO)
    }

    override fun onCleared() {
        // Note: we don't automatically kill the bot on ViewModel clear if background service is enabled,
        // but if user logged out or explicitly stopped, it is stopped.
        repository.disconnect()
        super.onCleared()
    }
}
