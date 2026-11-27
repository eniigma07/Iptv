package com.mojatv.app.pair

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.mojatv.app.data.Http
import com.mojatv.app.data.SourceConfig
import com.mojatv.app.data.SourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/** Telefon: szuka w sieci Wi‑Fi telewizorów z otwartym ekranem „Zaloguj telefonem”. */
class TvFinder(context: Context) {

    data class FoundTv(val name: String, val info: NsdServiceInfo)

    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val _found = MutableStateFlow<List<FoundTv>>(emptyList())
    val found: StateFlow<List<FoundTv>> = _found
    private var listener: NsdManager.DiscoveryListener? = null

    fun start() {
        if (listener != null) return
        val l = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(info: NsdServiceInfo) {
                val name = info.serviceName ?: return
                if (_found.value.none { it.name == name }) _found.value = _found.value + FoundTv(name, info)
            }

            override fun onServiceLost(info: NsdServiceInfo) {
                _found.value = _found.value.filterNot { it.name == info.serviceName }
            }

            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
        try {
            nsd.discoverServices(PairServer.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, l)
            listener = l
        } catch (e: Exception) {
            // wyszukiwanie niedostępne — zostaje wpisanie adresu
        }
    }

    fun stop() {
        listener?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        listener = null
    }

    /** Zwraca adres http://IP:port telewizora albo null. */
    @Suppress("DEPRECATION")
    suspend fun resolve(tv: FoundTv): String? = suspendCancellableCoroutine { cont ->
        try {
            nsd.resolveService(tv.info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                    if (cont.isActive) cont.resume(null)
                }

                override fun onServiceResolved(info: NsdServiceInfo) {
                    val host = info.host?.hostAddress
                    val url = if (host != null) {
                        val h = if (host.contains(':')) "[${host.substringBefore('%')}]" else host
                        "http://$h:${info.port}"
                    } else null
                    if (cont.isActive) cont.resume(url)
                }
            })
        } catch (e: Exception) {
            if (cont.isActive) cont.resume(null)
        }
    }

    companion object {
        private val client by lazy {
            Http.client.newBuilder()
                .connectTimeout(6, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .build()
        }

        /** „192.168.1.20” → „http://192.168.1.20:8765”. */
        fun normalizeAddress(input: String): String? {
            var a = input.trim().trimEnd('/')
            if (a.isEmpty()) return null
            if (!a.startsWith("http://", ignoreCase = true) && !a.startsWith("https://", ignoreCase = true)) a = "http://$a"
            val hostPart = a.substringAfter("://").substringBefore('/')
            if (!hostPart.contains(':')) a = a.replaceFirst(hostPart, "$hostPart:${PairServer.PORT}")
            return a.substringBefore("/?")
        }

        /** Wysyła profil. Zwraca null, gdy telewizor przyjął dane, albo opis błędu. */
        suspend fun send(baseUrl: String, cfg: SourceConfig, name: String?, pin: String): String? =
            withContext(Dispatchers.IO) {
                val body = JSONObject()
                    .put("type", if (cfg.type == SourceType.M3U) "m3u" else "xtream")
                    .put("server", cfg.server)
                    .put("username", cfg.username)
                    .put("password", cfg.password)
                    .put("m3uUrl", cfg.m3uUrl)
                    .put("epgUrl", cfg.epgUrl)
                    .put("name", name ?: "")
                    .put("pin", pin.trim())
                    .toString()
                    .toRequestBody("application/json; charset=utf-8".toMediaType())
                try {
                    client.newCall(Request.Builder().url("$baseUrl/api/profile").post(body).build()).execute().use { r ->
                        val o = runCatching { JSONObject(r.body?.string().orEmpty()) }.getOrNull()
                        if (o?.optBoolean("ok") == true) null
                        else o?.optString("error")?.takeIf { it.isNotBlank() } ?: "Telewizor odrzucił dane (${r.code})."
                    }
                } catch (e: Exception) {
                    "Brak połączenia z telewizorem. Sprawdź, czy jest w tej samej sieci Wi‑Fi i ma otwarty ekran logowania telefonem."
                }
            }

        /** Stan na telewizorze: waiting / loading / done / error + komunikat. */
        suspend fun status(baseUrl: String): Pair<String, String?>? = withContext(Dispatchers.IO) {
            try {
                client.newCall(Request.Builder().url("$baseUrl/api/status").build()).execute().use { r ->
                    val o = JSONObject(r.body?.string().orEmpty())
                    o.optString("state") to o.optString("message").takeIf { it.isNotBlank() }
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}
