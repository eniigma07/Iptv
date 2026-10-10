package com.mojatv.app.cast

import com.mojatv.app.data.Http
import okhttp3.Request
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/**
 * Pośrednik w telefonie: telewizor pobiera strumień z telefonu, a telefon z serwera IPTV.
 * Dzięki temu serwer widzi zawsze aplikację MojaTV (ta sama „przeglądarka”, przekierowania
 * obsłużone), a telewizor dostaje nagłówki DLNA, których wymagają telewizory Samsung.
 */
object StreamProxy {

    private data class Target(val url: String, val mime: String, val live: Boolean)

    private val targets = ConcurrentHashMap<String, Target>()
    @Volatile private var server: ServerSocket? = null

    /** Zwraca adres, pod którym telewizor pobierze strumień przez telefon, albo null (brak sieci lokalnej). */
    fun urlFor(upstream: String, mime: String, live: Boolean): String? {
        val ip = localIp() ?: return null
        val port = ensureStarted()
        val token = UUID.randomUUID().toString().replace("-", "").take(16)
        if (targets.size > 20) targets.clear()
        targets[token] = Target(upstream, mime, live)
        val ext = when {
            mime.contains("mp2t") -> "ts"
            mime.contains("mp4") -> "mp4"
            mime.contains("matroska") -> "mkv"
            mime.startsWith("audio/") -> "mp3"
            else -> "bin"
        }
        return "http://$ip:$port/s/$token.$ext"
    }

    fun contentFeatures(live: Boolean): String =
        if (live) "DLNA.ORG_OP=00;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000"
        else "DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000"

    @Synchronized
    private fun ensureStarted(): Int {
        server?.let { if (!it.isClosed) return it.localPort }
        val s = ServerSocket(0)
        server = s
        thread(isDaemon = true, name = "mojatv-proxy") {
            while (!s.isClosed) {
                val client = try { s.accept() } catch (e: Exception) { break }
                thread(isDaemon = true, name = "mojatv-proxy-conn") { handle(client) }
            }
        }
        return s.localPort
    }

    private fun handle(socket: Socket) {
        socket.use { sock ->
            try {
                sock.soTimeout = 30_000
                val reader = BufferedReader(InputStreamReader(sock.getInputStream(), Charsets.ISO_8859_1))
                val requestLine = reader.readLine() ?: return
                val headers = HashMap<String, String>()
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                    val i = line.indexOf(':')
                    if (i > 0) headers[line.substring(0, i).trim().lowercase()] = line.substring(i + 1).trim()
                }
                val parts = requestLine.split(" ")
                val method = parts.getOrNull(0) ?: "GET"
                val path = parts.getOrNull(1) ?: "/"
                val token = path.removePrefix("/s/").substringBefore('.').substringBefore('?')
                val target = targets[token]
                val out = sock.getOutputStream()
                if (target == null) {
                    writeHead(out, "404 Not Found", listOf("Content-Length" to "0"))
                    return
                }
                if (method == "HEAD") headResponse(out, target) else getResponse(out, target, headers["range"])
            } catch (e: Exception) {
                // telewizor przerwał połączenie (zmiana kanału, stop) — to normalne
            }
        }
    }

    private fun dlnaHeaders(t: Target) = listOf(
        "Content-Type" to t.mime,
        "transferMode.dlna.org" to "Streaming",
        "contentFeatures.dlna.org" to contentFeatures(t.live),
        "Connection" to "close",
    )

    private fun headResponse(out: OutputStream, t: Target) {
        if (t.live) {
            writeHead(out, "200 OK", dlnaHeaders(t))
            return
        }
        // rozmiar pliku z odpowiedzi na żądanie pierwszego bajtu
        var total: String? = null
        try {
            val req = Request.Builder().url(t.url).header("Range", "bytes=0-0").build()
            Http.client.newCall(req).execute().use { r ->
                total = r.header("Content-Range")?.substringAfter('/')?.takeIf { it != "*" }
                    ?: r.header("Content-Length")?.takeIf { r.code == 200 }
            }
        } catch (e: Exception) { /* bez rozmiaru */ }
        val h = dlnaHeaders(t).toMutableList()
        total?.let { h += "Content-Length" to it; h += "Accept-Ranges" to "bytes" }
        writeHead(out, "200 OK", h)
    }

    private fun getResponse(out: OutputStream, t: Target, range: String?) {
        val builder = Request.Builder().url(t.url)
        if (range != null && !t.live) builder.header("Range", range)
        Http.client.newCall(builder.build()).execute().use { r ->
            if (!r.isSuccessful) {
                writeHead(out, "502 Bad Gateway", listOf("Content-Length" to "0", "X-Upstream-Status" to r.code.toString()))
                return
            }
            val h = dlnaHeaders(t).toMutableList()
            if (!t.live) {
                r.header("Content-Length")?.let { h += "Content-Length" to it }
                r.header("Content-Range")?.let { h += "Content-Range" to it }
                if (r.header("Accept-Ranges") != null || r.code == 206) h += "Accept-Ranges" to "bytes"
            }
            writeHead(out, if (r.code == 206) "206 Partial Content" else "200 OK", h)
            val input = r.body?.byteStream() ?: return
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
            }
            out.flush()
        }
    }

    private fun writeHead(out: OutputStream, status: String, headers: List<Pair<String, String>>) {
        val sb = StringBuilder("HTTP/1.1 ").append(status).append("\r\n")
        headers.forEach { (k, v) -> sb.append(k).append(": ").append(v).append("\r\n") }
        sb.append("\r\n")
        out.write(sb.toString().toByteArray(Charsets.ISO_8859_1))
        out.flush()
    }

    /** Adres telefonu w sieci domowej (Wi‑Fi). */
    private fun localIp(): String? = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .sortedBy { if (it.name.startsWith("wlan")) 0 else 1 }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }
            ?.hostAddress
    } catch (e: Exception) {
        null
    }
}
