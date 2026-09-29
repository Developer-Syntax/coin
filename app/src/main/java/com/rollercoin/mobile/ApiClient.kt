package com.rollercoin.mobile

import okhttp3.JavaNetCookieJar
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.net.CookieManager
import java.net.CookiePolicy
import java.util.concurrent.TimeUnit

data class HttpResult(
    val code: Int,
    val body: String,
) {
    val json get() = runCatching { org.json.JSONObject(body) }.getOrNull()
}

class ApiClient {
    private val cookieManager = CookieManager(null, CookiePolicy.ACCEPT_ALL)
    private val client = OkHttpClient.Builder()
        .cookieJar(JavaNetCookieJar(cookieManager))
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun request(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String? = null,
    ): HttpResult {
        val requestBuilder = Request.Builder().url(url)
        headers.forEach { (name, value) -> requestBuilder.header(name, value) }
        if (method == "POST") {
            requestBuilder.post(
                (body ?: "").toRequestBody("application/json".toMediaType()),
            )
        } else {
            requestBuilder.get()
        }
        client.newCall(requestBuilder.build()).execute().use { response ->
            return HttpResult(response.code, response.body?.string().orEmpty())
        }
    }

    fun openWebSocket(
        request: Request,
        listener: WebSocketListener,
    ): WebSocket = client.newWebSocket(request, listener)
}
