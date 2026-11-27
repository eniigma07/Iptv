package com.mojatv.app.pair

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.mojatv.app.data.SourceConfig
import com.mojatv.app.data.SourceType
import com.mojatv.app.data.XtreamApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.SecureRandom
import kotlin.concurrent.thread

/**
 * Serwer w telewizorze (Fire TV), przez który telefon przesyła dane logowania.
 *
 * Działa tylko, gdy na telewizorze jest otwarty ekran „Zaloguj telefonem”.
 * Telefon w tej samej sieci Wi‑Fi:
 *  - skanuje kod QR aparatem → otwiera się strona z formularzem, albo
 *  - w aplikacji MojaTV wybiera „Wyślij profil na telewizor” (telewizor znajdowany automatycznie).
 * Każde przesłanie wymaga 4-cyfrowego kodu PIN widocznego na ekranie telewizora.
 */
class PairServer(
    private val context: Context,
    /** Wywoływane w wątku serwera, gdy telefon prześle poprawne dane z dobrym PIN-em. */
    private val onProfile: (SourceConfig, String?) -> Unit,
) {
    sealed class Status {
        data object Waiting : Status()
        data class Loading(val message: String) : Status()
        data object Done : Status()
        data class Failed(val message: String) : Status()
    }

    private val random = SecureRandom()
    private val _pin = MutableStateFlow(newPin())
    val pin: StateFlow<String> = _pin

    private val _status = MutableStateFlow<Status>(Status.Waiting)
    val status: StateFlow<Status> = _status

    private val _address = MutableStateFlow<String?>(null)
    /** Adres strony dla telefonu, np. http://192.168.1.20:8765 (null = brak sieci). */
    val address: StateFlow<String?> = _address

    @Volatile private var server: ServerSocket? = null
    @Volatile private var failedAttempts = 0
    private var nsdListener: NsdManager.RegistrationListener? = null

    fun setStatus(s: Status) {
        _status.value = s
    }

    fun start() {
        if (server != null) return
        val s = try {
            ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(PORT)) }
        } catch (e: Exception) {
            ServerSocket(0)
        }
        server = s
        val ip = localIp()
        _address.value = ip?.let { "http://$it:${s.localPort}" }
        thread(isDaemon = true, name = "mojatv-pair") {
            while (!s.isClosed) {
                val client = try { s.accept() } catch (e: Exception) { break }
                thread(isDaemon = true, name = "mojatv-pair-conn") { handle(client) }
            }
        }
        registerNsd(s.localPort)
    }

    fun stop() {
        runCatching { server?.close() }
        server = null
        nsdListener?.let { l ->
            runCatching { (context.getSystemService(Context.NSD_SERVICE) as NsdManager).unregisterService(l) }
        }
        nsdListener = null
    }

    /** Link w kodzie QR — od razu z PIN-em, żeby na telefonie nic nie wpisywać. */
    fun qrUrl(): String? = _address.value?.let { "$it/?pin=${_pin.value}" }

    // ---------------- ogłaszanie w sieci (telefon sam znajduje telewizor) ----------------

    private fun registerNsd(port: Int) {
        try {
            val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
            val info = NsdServiceInfo().apply {
                serviceName = "MojaTV " + com.mojatv.app.ui.Device.name
                serviceType = SERVICE_TYPE
                setPort(port)
            }
            val listener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(info: NsdServiceInfo) {}
                override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
                override fun onServiceUnregistered(info: NsdServiceInfo) {}
                override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
            }
            nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
            nsdListener = listener
        } catch (e: Exception) {
            // bez ogłaszania nadal działa kod QR i wpisanie adresu
        }
    }

    // ---------------- HTTP ----------------

    private class Req(val method: String, val path: String, val query: Map<String, String>, val body: ByteArray)

    private fun handle(socket: Socket) {
        socket.use { sock ->
            try {
                sock.soTimeout = 15_000
                val input = BufferedInputStream(sock.getInputStream())
                val req = readRequest(input) ?: return
                val out = sock.getOutputStream()
                when {
                    req.method == "OPTIONS" -> respond(out, "204 No Content", "text/plain", ByteArray(0))
                    req.path == "/" || req.path == "/index.html" -> {
                        val html = context.assets.open("pair.html").use { it.readBytes() }
                        respond(out, "200 OK", "text/html; charset=utf-8", html)
                    }
                    req.path == "/api/info" -> json(out, "200 OK", JSONObject()
                        .put("app", "MojaTV")
                        .put("device", com.mojatv.app.ui.Device.name))
                    req.path == "/api/status" -> json(out, "200 OK", statusJson())
                    req.path == "/api/profile" && req.method == "POST" -> receiveProfile(out, req)
                    else -> respond(out, "404 Not Found", "text/plain", "Not found".toByteArray())
                }
            } catch (e: Exception) {
                // telefon przerwał połączenie
            }
        }
    }

    private fun receiveProfile(out: OutputStream, req: Req) {
        val o = try {
            JSONObject(String(req.body, Charsets.UTF_8))
        } catch (e: Exception) {
            return json(out, "400 Bad Request", err("Nieprawidłowe dane."))
        }
        if (_status.value is Status.Loading) {
            return json(out, "409 Conflict", err("Telewizor właśnie wczytuje poprzedni profil. Poczekaj chwilę."))
        }
        val pin = o.optString("pin").trim()
        if (pin != _pin.value) {
            failedAttempts++
            if (failedAttempts >= 5) {
                // za dużo prób — nowy PIN na ekranie telewizora
                failedAttempts = 0
                _pin.value = newPin()
                return json(out, "403 Forbidden", err("Za dużo błędnych prób. Na telewizorze jest nowy kod PIN."))
            }
            return json(out, "403 Forbidden", err("Zły kod PIN. Sprawdź kod na ekranie telewizora."))
        }
        failedAttempts = 0

        val epg = o.optString("epgUrl").trim()
        val cfg = when (o.optString("type").lowercase()) {
            "m3u" -> {
                val url = o.optString("m3uUrl").trim()
                if (!url.startsWith("http", ignoreCase = true)) {
                    return json(out, "400 Bad Request", err("Podaj pełny link do playlisty (http…)."))
                }
                SourceConfig(SourceType.M3U, m3uUrl = url, epgUrl = epg)
            }
            else -> {
                val server = o.optString("server").trim()
                val user = o.optString("username").trim()
                val pass = o.optString("password").trim()
                XtreamApi.credentialsFromUrl(server)?.copy(epgUrl = epg)
                    ?: run {
                        if (server.isBlank() || user.isBlank()) {
                            return json(out, "400 Bad Request", err("Uzupełnij adres serwera i login."))
                        }
                        SourceConfig(SourceType.XTREAM, server, user, pass, epgUrl = epg)
                    }
            }
        }
        val name = o.optString("name").trim().takeIf { it.isNotEmpty() }
        _status.value = Status.Loading("Telewizor sprawdza dane i wczytuje kanały…")
        json(out, "200 OK", JSONObject().put("ok", true))
        onProfile(cfg, name)
    }

    private fun statusJson(): JSONObject = when (val s = _status.value) {
        Status.Waiting -> JSONObject().put("state", "waiting")
        is Status.Loading -> JSONObject().put("state", "loading").put("message", s.message)
        Status.Done -> JSONObject().put("state", "done").put("message", "Gotowe! Telewizor jest zalogowany.")
        is Status.Failed -> JSONObject().put("state", "error").put("message", s.message)
    }

    private fun err(msg: String) = JSONObject().put("ok", false).put("error", msg)

    private fun json(out: OutputStream, status: String, o: JSONObject) =
        respond(out, status, "application/json; charset=utf-8", o.toString().toByteArray(Charsets.UTF_8))

    private fun respond(out: OutputStream, status: String, type: String, body: ByteArray) {
        val head = StringBuilder("HTTP/1.1 ").append(status).append("\r\n")
            .append("Content-Type: ").append(type).append("\r\n")
            .append("Content-Length: ").append(body.size).append("\r\n")
            .append("Cache-Control: no-store\r\n")
            .append("Access-Control-Allow-Origin: *\r\n")
            .append("Access-Control-Allow-Headers: Content-Type\r\n")
            .append("Connection: close\r\n\r\n")
        out.write(head.toString().toByteArray(Charsets.ISO_8859_1))
        out.write(body)
        out.flush()
    }

    private fun readRequest(input: InputStream): Req? {
        val requestLine = readLine(input) ?: return null
        var length = 0
        while (true) {
            val line = readLine(input) ?: break
            if (line.isEmpty()) break
            val i = line.indexOf(':')
            if (i > 0 && line.substring(0, i).trim().equals("content-length", ignoreCase = true)) {
                length = line.substring(i + 1).trim().toIntOrNull() ?: 0
            }
        }
        if (length > MAX_BODY) return null
        val body = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(body, read, length - read)
            if (n < 0) break
            read += n
        }
        val parts = requestLine.split(" ")
        val target = parts.getOrNull(1) ?: "/"
        val path = target.substringBefore('?')
        val query = target.substringAfter('?', "").split('&').filter { it.contains('=') }.associate {
            URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8")
        }
        return Req(parts.getOrNull(0)?.uppercase() ?: "GET", path, query, body)
    }

    private fun readLine(input: InputStream): String? {
        val buf = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b < 0) return if (buf.size() == 0) null else buf.toString("ISO-8859-1")
            if (b == '\n'.code) break
            if (b != '\r'.code) buf.write(b)
            if (buf.size() > 8192) return null
        }
        return buf.toString("ISO-8859-1")
    }

    private fun newPin(): String = (1000 + random.nextInt(9000)).toString()

    companion object {
        const val PORT = 8765
        const val SERVICE_TYPE = "_mojatv._tcp."
        private const val MAX_BODY = 64 * 1024

        /** Adres urządzenia w sieci domowej (Wi‑Fi albo kabel). */
        fun localIp(): String? = try {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .sortedBy { if (it.name.startsWith("wlan") || it.name.startsWith("eth")) 0 else 1 }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { it.isSiteLocalAddress }
                ?.hostAddress
        } catch (e: Exception) {
            null
        }
    }
}
