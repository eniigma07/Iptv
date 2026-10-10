package com.mojatv.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

/** Serwer odpowiedział kodem błędu HTTP (np. 403, 404). */
class HttpStatusException(val code: Int) : IOException("Serwer zwrócił błąd HTTP $code")

object Http {
    const val DEFAULT_USER_AGENT = "VLC/3.0.20 LibVLC/3.0.20"
    const val CHROME_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Mobile Safari/537.36"

    /** Nazwy, pod którymi aplikacja próbuje się przedstawić, gdy serwer ją blokuje. */
    val FALLBACK_USER_AGENTS = listOf(DEFAULT_USER_AGENT, "IPTVSmartersPro", "okhttp/4.12.0", CHROME_USER_AGENT)

    @Volatile
    var userAgent: String = DEFAULT_USER_AGENT

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder().header("User-Agent", userAgent).build()
            )
        }
        .build()

    suspend fun getString(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw HttpStatusException(response.code)
            response.body?.string() ?: ""
        }
    }

    /**
     * Otwiera strumień (np. dużej playlisty lub EPG) i przekazuje go do [block].
     * Pliki .gz są rozpakowywane automatycznie.
     */
    suspend fun <T> withStream(url: String, block: (InputStream) -> T): T = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw HttpStatusException(response.code)
            val body = response.body ?: throw IOException("Pusta odpowiedź serwera")
            block(maybeGunzip(body.byteStream()))
        }
    }

    fun maybeGunzip(input: InputStream): InputStream {
        val buffered = BufferedInputStream(input, 64 * 1024)
        buffered.mark(4)
        val b1 = buffered.read()
        val b2 = buffered.read()
        buffered.reset()
        return if (b1 == 0x1f && b2 == 0x8b) BufferedInputStream(GZIPInputStream(buffered), 64 * 1024) else buffered
    }
}
