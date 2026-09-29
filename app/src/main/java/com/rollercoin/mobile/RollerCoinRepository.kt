package com.rollercoin.mobile

import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

data class CaptchaChallenge(
    val challenge: String?,
    val required: Boolean,
    val image: ByteArray? = null,
    val targetImage: ByteArray? = null,
    val targetIcons: List<ByteArray> = emptyList(),
    val instruction: String? = null,
    val availableKeys: List<String> = emptyList(),
    val maxDots: Int = 3,
)

data class AuthTokens(
    val accessToken: String,
    val refreshToken: String = "",
)

class ApiException(message: String) : RuntimeException(message)

class RollerCoinRepository(
    private val api: ApiClient = ApiClient(),
) {
    private var accessToken = ""
    private var refreshToken = ""
    private var uid = ""
    private var userAgent = DEFAULT_USER_AGENT
    private var socket: RollerCoinSocket? = null

    fun setCredentials(
        access: String,
        refresh: String = "",
        agent: String = DEFAULT_USER_AGENT,
    ) {
        accessToken = access.removePrefix("Bearer ").trim()
        refreshToken = refresh.trim()
        userAgent = agent.ifBlank { DEFAULT_USER_AGENT }
        uid = TokenUtils.userId(accessToken)
    }

    fun currentAccessToken(): String = accessToken
    fun currentRefreshToken(): String = refreshToken
    fun userId(): String = uid

    fun isTokenExpired(): Boolean = TokenUtils.isExpired(accessToken)

    fun captchaStatus(
        fingerprint: String = DEFAULT_FINGERPRINT,
        action: String = "auth",
        csrfToken: String = DEFAULT_CSRF_TOKEN,
    ): CaptchaChallenge {
        val url = "$ROLLERCOIN/api/auth/captcha-status" +
            "?fingerprint=${encode(fingerprint)}&action=${encode(action)}"
        val result = api.request(
            "GET",
            url,
            captchaHeaders(
                host = "rollercoin.com",
                csrf = csrfToken,
                referer = "$ROLLERCOIN/sign-in?welcome=true",
            ),
        )
        val data = successData(result, "captcha-status")
        val required = data.optBoolean("is_captcha_required", false)
        val maxDots = data.optInt("max_dots", 3)
        return CaptchaChallenge(
            challenge = data.optString("challenge").ifBlank { null },
            required = required,
            image = null,
            maxDots = maxDots,
        )
    }

    fun createCaptcha(challenge: String, maxDots: Int = 3): CaptchaChallenge {
        val result = api.request(
            "POST",
            "$CAPTCHA/create-captcha",
            captchaHeaders("captcha.rollercoin.com"),
            JSONObject().put("challenge", challenge).toString(),
        )
        val data = result.json?.optJSONObject("data")
            ?: throw ApiException("create-captcha: respons tidak valid (${result.code})")

        val keys = mutableListOf<String>()
        val it = data.keys()
        while (it.hasNext()) {
            keys.add(it.next())
        }

        // Main challenge image
        val image = decodeDataUri(data.optString("captcha_img"))
            ?: decodeDataUri(data.optString("image"))
            ?: decodeDataUri(data.optString("background"))
            ?: decodeDataUri(data.optString("captcha"))
            ?: throw ApiException("create-captcha: gambar CAPTCHA tidak ada (keys: $keys)")

        // Target banner/icons image if provided - RollerCoin explicitly sends 'thumb_img'!
        val targetImage = decodeDataUri(data.optString("thumb_img"))
            ?: decodeDataUri(data.optString("target_img"))
            ?: decodeDataUri(data.optString("icons_img"))
            ?: decodeDataUri(data.optString("tip_img"))
            ?: decodeDataUri(data.optString("example_img"))
            ?: decodeDataUri(data.optString("prompt_img"))
            ?: decodeDataUri(data.optString("header_img"))
            ?: decodeDataUri(data.optString("target_image"))

        // Multiple target icons if provided as array
        val targetIcons = mutableListOf<ByteArray>()
        val iconsArray = data.optJSONArray("icons")
            ?: data.optJSONArray("target_icons")
            ?: data.optJSONArray("targets")
            ?: data.optJSONArray("tasks")
            ?: data.optJSONArray("items")

        if (iconsArray != null) {
            for (i in 0 until iconsArray.length()) {
                val item = iconsArray.opt(i)
                when (item) {
                    is String -> decodeDataUri(item)?.let { targetIcons.add(it) }
                    is JSONObject -> {
                        val iconData = decodeDataUri(item.optString("img"))
                            ?: decodeDataUri(item.optString("image"))
                            ?: decodeDataUri(item.optString("icon"))
                            ?: decodeDataUri(item.optString("src"))
                            ?: decodeDataUri(item.optString("data"))
                        iconData?.let { targetIcons.add(it) }
                    }
                }
            }
        }

        val instruction = data.optString("instruction").ifBlank {
            data.optString("text").ifBlank {
                data.optString("tip").ifBlank {
                    data.optString("title").ifBlank { null }
                }
            }
        }

        return CaptchaChallenge(
            challenge = challenge,
            required = true,
            image = image,
            targetImage = targetImage,
            targetIcons = targetIcons,
            instruction = instruction,
            availableKeys = keys,
            maxDots = maxDots,
        )
    }

    fun validateCaptcha(challenge: String, points: String): Boolean {
        val result = api.request(
            "POST",
            "$CAPTCHA/validate-captcha",
            captchaHeaders("captcha.rollercoin.com"),
            JSONObject()
                .put("challenge", challenge)
                .put("points", points)
                .toString(),
        )
        val data = result.json ?: throw ApiException("validate-captcha: respons tidak valid")
        return data.optJSONObject("data")?.optBoolean("is_valid", false) == true
    }

    fun requestOtp(email: String, challenge: String?): OtpRequest {
        val payload = JSONObject()
            .put("mail", email)
            .put("registrationLanguage", "en")
            .put("challenge", challenge ?: JSONObject.NULL)
            .put("action", "auth")
            .put("referrer", "")
        val result = publicCall(
            "POST",
            "$ROLLERCOIN/api/auth/email-auth",
            payload.toString(),
        )
        val data = successData(result, "email-auth")
        val userId = data.optString("user_id")
        val codeId = data.optString("confirm_code_id")
        if (userId.isBlank() || codeId.isBlank()) {
            throw ApiException("OTP terkirim tetapi identitas konfirmasi tidak ada")
        }
        return OtpRequest(userId, codeId)
    }

    fun validateOtp(userId: String, codeId: String, code: String): AuthTokens {
        val result = publicCall(
            "POST",
            "$ROLLERCOIN/api/auth/validate-auth-code",
            JSONObject()
                .put("user_id", userId)
                .put("code_id", codeId)
                .put("code", code)
                .toString(),
        )
        val data = successData(result, "validate-auth-code")
        val access = data.optString("access_token")
        val refresh = data.optString("refresh_token")
        if (access.isBlank()) {
            throw ApiException("Login berhasil tetapi token tidak ditemukan")
        }
        setCredentials(access, refresh, userAgent)
        return AuthTokens(access, refresh)
    }

    fun refreshAccessToken(): AuthTokens {
        if (refreshToken.isBlank()) {
            throw ApiException("Refresh token tidak tersedia")
        }
        val result = publicCall(
            "POST",
            "$ROLLERCOIN/api/auth/refresh",
            JSONObject().put("refresh_token", refreshToken).toString(),
        )
        val data = successData(result, "refresh")
        val newAccess = data.optString("access_token")
        val newRefresh = data.optString("refresh_token", refreshToken)
        if (newAccess.isBlank()) throw ApiException("Gagal memperbarui token")
        setCredentials(newAccess, newRefresh, userAgent)
        return AuthTokens(newAccess, newRefresh)
    }

    fun loadDashboard(): Dashboard {
        requireAuthenticated()
        val profile = runCatching { profile() }.getOrNull()
        val pcConfig = runCatching { pcConfig() }.getOrNull()
        val currencies = runCatching { currencies() }.getOrDefault(emptyList())
        val activeSocket = ensureSocket()
        val games = activeSocket.games()
        val power = runCatching { activeSocket.power() }.getOrNull()
        return Dashboard(profile, power, pcConfig, currencies, games)
    }

    fun profile(): Profile {
        val data = successData(call("GET", "$ROLLERCOIN/api/profile/user-profile-data"), "profile")
        return Profile(
            name = data.optString("name", "N/A"),
            email = data.optString("email", "N/A"),
            id = data.optString("id", uid),
            active = !data.optBoolean("is_banned", false),
            premium = if (data.has("is_premium")) data.optBoolean("is_premium") else null,
            miners = if (data.has("user_miners_amount")) data.optInt("user_miners_amount") else null,
            maxPower = data.optInt("max_total_power", 0).takeIf { it > 0 },
            leagueId = data.optJSONArray("leagues_ids")?.optString(0, "N/A") ?: "N/A",
            registration = data.optString("registration", "N/A"),
            publicProfileLink = data.optString("public_profile_link", "N/A"),
        )
    }

    fun pcConfig(): PcConfig {
        val data = successData(call("GET", "$ROLLERCOIN/api/game/pc-config"), "pc-config")
        return PcConfig(
            name = data.optString("name", "N/A"),
            level = data.optInt("level"),
            maxLevel = data.optInt("max_level"),
            gamesToNextLevel = data.optInt("games_to_next_level"),
            powerHoldingDays = data.optInt("power_holding"),
            multiplier = data.optString("multiplier").ifBlank { null },
            expireDate = data.optString("expire_date").ifBlank { null },
        )
    }

    fun currencies(): List<WalletCurrency> {
        val res = call("GET", "$ROLLERCOIN/api/wallet/get-currencies-config")
        val data = successData(res, "get-currencies-config")
        val array = data.optJSONArray("currencies_config") ?: return emptyList()
        val result = mutableListOf<WalletCurrency>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val code = item.optString("code", "").uppercase()
            if (code.isBlank()) continue
            val toSmall = item.optDouble("to_small", 1.0)
            val precision = item.optInt("precision_to_balance", item.optInt("precision", 8))
            val name = item.optString("name", code)
            result.add(WalletCurrency(code = code, name = name, toSmall = toSmall, precision = precision))
        }
        return result
    }

    fun fetchGamesList(): List<GameState> {
        requireAuthenticated()
        return ensureSocket().games()
    }

    fun fetchPowerInfo(): PowerInfo? {
        requireAuthenticated()
        return runCatching { ensureSocket().power() }.getOrNull()
    }

    fun startGame(state: GameState): ActiveGame {
        requireAuthenticated()
        val captchaStatus = call(
            "GET",
            "$ROLLERCOIN/api/game/captcha-status/$uid",
        )
        if (captchaStatus.json?.optJSONObject("data")?.optBoolean("is_captcha_required") == true) {
            throw ApiException("Game CAPTCHA diperlukan di akun RollerCoin")
        }
        val token = encodeStartGame(state.definition.number, "")
        val start = ensureSocket().sendAndWait(
            JSONObject().put("cmd", "game_start_request").put("cmdval", token),
            "game_start_response",
            20,
        ) ?: throw ApiException("Tidak ada respons game_start")
        val id = start.optJSONObject("cmdval")?.optString("user_game_id").orEmpty()
        if (id.isBlank()) throw ApiException("user_game_id tidak ditemukan")
        val duration = GameCatalog.duration(state)
        return ActiveGame(
            userGameId = id,
            game = state,
            targetPower = GameCatalog.reward(state),
            totalDurationSeconds = duration,
            startTimeMs = System.currentTimeMillis(),
        )
    }

    fun finishGame(active: ActiveGame): FinishedGame {
        val duration = active.totalDurationSeconds
        val token = encodeEndGame(
            active.userGameId,
            active.game.definition.number,
            active.targetPower,
            active.game.definition.winStatus,
        )
        val end = ensureSocket().sendAndWait(
            JSONObject().put("cmd", "game_end_request").put("cmdval", token),
            "game_finished_accepted",
            20,
        ) ?: throw ApiException("game_finished_accepted tidak diterima")
        val values = end.optJSONObject("cmdval")
        return FinishedGame(
            gameNumber = active.game.definition.number,
            gameName = active.game.definition.name,
            actualPower = values?.optInt("power", active.targetPower) ?: active.targetPower,
            status = values?.optString("win_status", "accepted") ?: "accepted",
            durationSeconds = values?.optInt("duration", duration) ?: duration,
        )
    }

    fun disconnect() {
        socket?.close()
        socket = null
    }

    private fun encodeStartGame(gameNumber: Int, seccode: String): String {
        val encrypted = Crypto.encryptJson(
            JSONObject().put("game_number", gameNumber),
            uid,
        )
        val result = call(
            "POST",
            "$ROLLERCOIN/api/game/encode-start-game-data/$uid?seccode=${encode(seccode)}",
            JSONObject().put("data", encrypted).toString(),
        )
        return successData(result, "encode-start-game-data").optString("data")
            .ifBlank { result.json?.optString("data").orEmpty() }
            .ifBlank { throw ApiException("encode-start-game-data tidak menghasilkan token") }
    }

    private fun encodeEndGame(
        userGameId: String,
        gameNumber: Int,
        power: Int,
        winStatus: Int,
    ): String {
        val encrypted = Crypto.encryptJson(
            JSONObject()
                .put("power", power)
                .put("time", System.currentTimeMillis())
                .put("user_game_id", userGameId)
                .put("win_status", winStatus),
            uid,
        )
        val result = call(
            "POST",
            "$ROLLERCOIN/api/game/encode-data/$uid",
            JSONObject().put("data", encrypted).toString(),
        )
        return result.json?.optString("data").orEmpty()
            .ifBlank { throw ApiException("encode-data tidak menghasilkan token") }
    }

    private fun ensureSocket(): RollerCoinSocket {
        val current = socket
        if (current != null && current.isOpen()) return current
        var lastError: Throwable? = null
        for (attempt in 1..3) {
            try {
                if (isTokenExpired() && refreshToken.isNotBlank()) {
                    refreshAccessToken()
                }
                val newSocket = RollerCoinSocket(api, accessToken)
                newSocket.connect()
                socket = newSocket
                return newSocket
            } catch (error: Throwable) {
                lastError = error
                socket?.close()
                socket = null
                if (attempt < 3) Thread.sleep(2_500)
            }
        }
        throw ApiException(
            "Gagal menghubungkan WebSocket RollerCoin: " +
                (lastError?.message ?: "koneksi terputus"),
        )
    }

    private fun call(method: String, url: String, body: String? = null): HttpResult {
        requireAuthenticated()
        val result = api.request(method, url, authHeaders(), body)
        if (result.code !in 200..299) {
            throw ApiException("HTTP ${result.code}: ${result.body.take(180)}")
        }
        return result
    }

    private fun publicCall(method: String, url: String, body: String? = null): HttpResult {
        val result = api.request(method, url, publicHeaders(), body)
        if (result.code !in 200..299) {
            throw ApiException("HTTP ${result.code}: ${result.body.take(180)}")
        }
        return result
    }

    private fun requireAuthenticated() {
        if (uid.isBlank() || accessToken.isBlank()) {
            throw ApiException("Sesi login diperlukan sebelum membuka dashboard")
        }
    }

    private fun successData(result: HttpResult, label: String): JSONObject {
        val json = result.json ?: throw ApiException("$label: respons bukan JSON")
        if (!json.optBoolean("success", false)) {
            throw ApiException("$label gagal: ${result.body.take(180)}")
        }
        return json.optJSONObject("data") ?: JSONObject()
    }

    private fun authHeaders(): Map<String, String> = mapOf(
        "Authorization" to "Bearer $accessToken",
        "Content-Type" to "application/json",
        "Accept" to "application/json",
        "User-Agent" to userAgent,
        "Origin" to ROLLERCOIN,
        "Referer" to "$ROLLERCOIN/",
    )

    private fun publicHeaders(): Map<String, String> = mapOf(
        "Content-Type" to "application/json",
        "Accept" to "application/json",
        "User-Agent" to userAgent,
        "Origin" to ROLLERCOIN,
        "Referer" to "$ROLLERCOIN/",
    )

    private fun captchaHeaders(
        host: String,
        csrf: String? = null,
        referer: String = "$ROLLERCOIN/",
    ): Map<String, String> =
        buildMap {
            put("Host", host)
            put("User-Agent", userAgent)
            put("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7")
            put("X-Requested-With", "mark.via.gp")
            put("Accept", if (host == "rollercoin.com") "application/json" else "*/*")
            put("Content-Type", "application/json")
            put("Origin", ROLLERCOIN)
            put("Referer", referer)
            if (!csrf.isNullOrBlank()) put("csrf-token", csrf)
        }

    private fun decodeDataUri(value: String?): ByteArray? {
        if (value.isNullOrBlank()) return null
        val encoded = value.substringAfter("base64,").trim()
        if (encoded.isBlank()) return null
        return runCatching {
            android.util.Base64.decode(encoded, android.util.Base64.DEFAULT)
        }.recoverCatching {
            Base64.getDecoder().decode(encoded)
        }.getOrNull()
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private companion object {
        const val ROLLERCOIN = "https://rollercoin.com"
        const val CAPTCHA = "https://captcha.rollercoin.com/api"
        const val DEFAULT_FINGERPRINT = "a22669a4ca3b38745bdb9fb563085882"
        const val DEFAULT_CSRF_TOKEN = "a2c6d00e-49c7-479e-9965-1a3e0a0e"
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10; Redmi Note 7 Build/QKQ1.190910.002) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.7680.177 Mobile Safari/537.36"
    }
}

private class RollerCoinSocket(
    private val api: ApiClient,
    private val token: String,
) {
    private val incoming = LinkedBlockingQueue<String>()
    private val connected = CountDownLatch(1)
    private var socket: WebSocket? = null
    private var failure: String? = null

    fun connect() {
        val encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8.name())
        val request = Request.Builder()
            .url("wss://ws.rollercoin.com/cmd?token=$encodedToken")
            .header("Origin", "https://rollercoin.com")
            .header("User-Agent", "RollerCoin Mobile Android")
            .build()
        socket = api.openWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                connected.countDown()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                incoming.offer(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                failure = t.message ?: "WebSocket error"
                connected.countDown()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                failure = "WebSocket ditutup: $code $reason"
            }
        })
        if (!connected.await(20, TimeUnit.SECONDS)) {
            close()
            throw ApiException("WebSocket timeout saat menghubungkan")
        }
        failure?.let { throw ApiException(it) }
    }

    fun isOpen(): Boolean = socket != null && failure == null

    fun sendAndWait(command: JSONObject, expected: String, timeoutSeconds: Int): JSONObject? {
        val current = socket ?: throw ApiException("WebSocket belum terhubung")
        if (!current.send(command.toString())) throw ApiException("Gagal mengirim perintah WebSocket")
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds.toLong())
        while (System.nanoTime() < deadline) {
            val remaining = deadline - System.nanoTime()
            val raw = incoming.poll(
                remaining.coerceAtMost(TimeUnit.SECONDS.toNanos(1)),
                TimeUnit.NANOSECONDS,
            ) ?: continue
            val message = runCatching { JSONObject(raw) }.getOrNull() ?: continue
            val received = message.optString("cmd")
            if (received == expected) return message
            if (received == "notice_error" ||
                received == "system_error" ||
                received == "game_finished_rejected"
            ) {
                throw ApiException(
                    "Server WebSocket error: " +
                        (message.optJSONObject("cmderror")?.optString("code")
                            ?: message.optString("cmdval", "unknown")),
                )
            }
        }
        return null
    }

    fun games(): List<GameState> {
        val response = sendAndWait(
            JSONObject().put("cmd", "games_data_request"),
            "games_data_response",
            15,
        ) ?: throw ApiException("Data game tidak diterima dari server")
        val values = response.optJSONArray("cmdval") ?: JSONArray()
        val byNumber = mutableMapOf<Int, JSONObject>()
        for (index in 0 until values.length()) {
            values.optJSONObject(index)?.let { byNumber[it.optInt("game_number")] = it }
        }
        return GameCatalog.games.map { definition ->
            val value = byNumber[definition.number]
            GameState(
                definition = definition,
                cooldownSeconds = value?.optInt("cool_down", 0) ?: 0,
                level = value?.optJSONObject("level")?.optInt("level", 1) ?: 1,
            )
        }
    }

    fun power(): PowerInfo? {
        val current = socket ?: return null
        if (!current.send(JSONObject().put("cmd", "get_powers_info").toString())) {
            return null
        }
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < deadline) {
            val raw = incoming.poll(2, TimeUnit.SECONDS) ?: break
            val message = runCatching { JSONObject(raw) }.getOrNull() ?: continue
            if (message.optString("cmd") == "power") {
                val values = message.optJSONObject("cmdval") ?: return null
                return PowerInfo(values.optLong("total"), values.optLong("penalty"))
            }
        }
        return null
    }

    fun close() {
        socket?.close(1000, "user_disconnect")
        socket = null
    }
}

private object TokenUtils {
    fun userId(token: String): String = payload(token)?.optString("user_id").orEmpty()

    fun isExpired(token: String): Boolean {
        val expiresAt = payload(token)?.optLong("exp", 0L) ?: 0L
        return expiresAt > 0L && System.currentTimeMillis() / 1_000L >= expiresAt
    }

    private fun payload(token: String): JSONObject? = runCatching {
        val pieces = token.removePrefix("Bearer ").split('.')
        if (pieces.size < 2) return null
        val body = Base64.getUrlDecoder().decode(
            pieces[1].padEnd(((pieces[1].length + 3) / 4) * 4, '='),
        ).toString(StandardCharsets.UTF_8)
        JSONObject(body)
    }.getOrNull()
}
