package com.mojatv.app.cast

import android.content.Context
import android.net.wifi.WifiManager
import com.mojatv.app.data.ContentKind
import com.mojatv.app.data.Http
import com.mojatv.app.data.PlayItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.StringReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.TimeUnit

/** Telewizor (odbiornik DLNA / UPnP MediaRenderer). */
data class Renderer(
    val name: String,
    val location: String,
    val host: String,
    val avTransportUrl: String,
    val avTransportType: String,
)

data class CastPosition(val positionMs: Long, val durationMs: Long, val state: String?)

/**
 * Wysyłanie wideo na telewizor przez DLNA (UPnP AVTransport).
 * Telewizory Samsung obsługują to bez trybu programisty, gdy są włączone i w tej samej sieci.
 */
object Dlna {

    private val client: OkHttpClient = Http.client.newBuilder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    // ---------------- Wyszukiwanie ----------------

    /** Szuka odbiorników w sieci (SSDP). Zwraca listę bez duplikatów. */
    suspend fun discover(context: Context, timeoutMs: Int = 4000): List<Renderer> = withContext(Dispatchers.IO) {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lock = wifi?.createMulticastLock("mojatv-dlna")?.apply {
            setReferenceCounted(false)
            acquire()
        }
        val locations = LinkedHashSet<String>()
        try {
            DatagramSocket().use { socket ->
                socket.soTimeout = 500
                val group = InetAddress.getByName("239.255.255.250")
                val targets = listOf(
                    "urn:schemas-upnp-org:device:MediaRenderer:1",
                    "urn:schemas-upnp-org:service:AVTransport:1",
                )
                fun send() {
                    for (st in targets) {
                        val msg = "M-SEARCH * HTTP/1.1\r\n" +
                            "HOST: 239.255.255.250:1900\r\n" +
                            "MAN: \"ssdp:discover\"\r\n" +
                            "MX: 2\r\n" +
                            "ST: $st\r\n\r\n"
                        val bytes = msg.toByteArray()
                        socket.send(DatagramPacket(bytes, bytes.size, group, 1900))
                    }
                }
                send()
                val buf = ByteArray(4096)
                val end = System.currentTimeMillis() + timeoutMs
                var resent = false
                while (System.currentTimeMillis() < end) {
                    if (!resent && System.currentTimeMillis() > end - timeoutMs / 2) {
                        send()
                        resent = true
                    }
                    try {
                        val packet = DatagramPacket(buf, buf.size)
                        socket.receive(packet)
                        val text = String(packet.data, 0, packet.length)
                        text.lineSequence()
                            .firstOrNull { it.startsWith("location:", ignoreCase = true) }
                            ?.substringAfter(':')?.trim()
                            ?.let { locations.add(it) }
                    } catch (e: SocketTimeoutException) {
                        // czekamy dalej
                    }
                }
            }
        } catch (e: Exception) {
            // brak sieci multicast — zostaje wpisanie adresu ręcznie
        } finally {
            try { lock?.release() } catch (e: Exception) { /* */ }
        }
        locations.mapNotNull { runCatching { describe(it) }.getOrNull() }
            .distinctBy { it.avTransportUrl }
    }

    /** Ręczne dodanie telewizora po adresie IP (Samsung udostępnia opis na porcie 9197). */
    suspend fun fromIp(ip: String): Renderer = withContext(Dispatchers.IO) {
        val clean = ip.trim().removePrefix("http://").substringBefore('/').substringBefore(':')
        val candidates = listOf(
            "http://$clean:9197/dmr",
            "http://$clean:7676/smp_15_",
            "http://$clean:9197/description.xml",
        )
        var last: Exception? = null
        for (url in candidates) {
            try {
                return@withContext describe(url)
            } catch (e: Exception) {
                last = e
            }
        }
        throw IOException("Nie znaleziono telewizora pod adresem $clean. Sprawdź, czy jest włączony.", last)
    }

    /** Zapamiętany wcześniej telewizor (adres opisu urządzenia). */
    suspend fun fromLocation(location: String): Renderer = withContext(Dispatchers.IO) { describe(location) }

    /** Pobiera opis urządzenia i wyciąga adres sterowania AVTransport. */
    private fun describe(location: String): Renderer {
        val xml = client.newCall(Request.Builder().url(location).build()).execute().use {
            if (!it.isSuccessful) throw IOException("HTTP ${it.code}")
            it.body?.string() ?: ""
        }
        val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }.newPullParser()
        parser.setInput(StringReader(xml))
        var name: String? = null
        var urlBase: String? = null
        var serviceType: String? = null
        var controlUrl: String? = null
        var avType: String? = null
        var avControl: String? = null
        var tag: String? = null
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    tag = parser.name.substringAfter(':')
                    if (tag == "service") {
                        serviceType = null
                        controlUrl = null
                    }
                }
                XmlPullParser.TEXT -> {
                    val t = parser.text?.trim().orEmpty()
                    if (t.isNotEmpty()) when (tag) {
                        "friendlyName" -> if (name == null) name = t
                        "URLBase" -> urlBase = t
                        "serviceType" -> serviceType = t
                        "controlURL" -> controlUrl = t
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name.substringAfter(':') == "service" &&
                        serviceType?.contains("AVTransport") == true && avControl == null
                    ) {
                        avType = serviceType
                        avControl = controlUrl
                    }
                    tag = null
                }
            }
            event = parser.next()
        }
        val control = avControl ?: throw IOException("Urządzenie nie odtwarza wideo z sieci")
        val base = URL(urlBase ?: location)
        return Renderer(
            name = name ?: base.host,
            location = location,
            host = base.host,
            avTransportUrl = URL(base, control).toString(),
            avTransportType = avType ?: "urn:schemas-upnp-org:service:AVTransport:1",
        )
    }

    // ---------------- Sterowanie ----------------

    /** Wysyła materiał na telewizor i uruchamia odtwarzanie. */
    suspend fun play(r: Renderer, item: PlayItem, startMs: Long = 0) = withContext(Dispatchers.IO) {
        val url = resolveRedirects(item.url)
        try { soap(r, "Stop", "") } catch (e: Exception) { /* nic nie grało */ }
        soap(
            r, "SetAVTransportURI",
            "<CurrentURI>${esc(url)}</CurrentURI><CurrentURIMetaData>${esc(didl(item, url))}</CurrentURIMetaData>",
        )
        kotlinx.coroutines.delay(400)
        try {
            soap(r, "Play", "<Speed>1</Speed>")
        } catch (e: Exception) {
            kotlinx.coroutines.delay(1200)
            soap(r, "Play", "<Speed>1</Speed>")
        }
        if (startMs > 5_000 && !item.isLive) {
            // przewijanie działa dopiero, gdy telewizor zacznie odtwarzać
            for (i in 0 until 10) {
                kotlinx.coroutines.delay(1000)
                val st = runCatching { state(r) }.getOrNull()
                if (st == "PLAYING") break
            }
            runCatching { seek(r, startMs) }
        }
    }

    suspend fun pause(r: Renderer) = withContext(Dispatchers.IO) { soap(r, "Pause", "") }
    suspend fun resume(r: Renderer) = withContext(Dispatchers.IO) { soap(r, "Play", "<Speed>1</Speed>") }
    suspend fun stop(r: Renderer) = withContext(Dispatchers.IO) { soap(r, "Stop", "") }

    suspend fun seek(r: Renderer, ms: Long) = withContext(Dispatchers.IO) {
        soap(r, "Seek", "<Unit>REL_TIME</Unit><Target>${hms(ms)}</Target>")
    }

    suspend fun position(r: Renderer): CastPosition = withContext(Dispatchers.IO) {
        val pos = soap(r, "GetPositionInfo", "")
        val st = runCatching { state(r) }.getOrNull()
        CastPosition(
            positionMs = parseHms(tagValue(pos, "RelTime")),
            durationMs = parseHms(tagValue(pos, "TrackDuration")),
            state = st,
        )
    }

    private fun state(r: Renderer): String? =
        tagValue(soap(r, "GetTransportInfo", ""), "CurrentTransportState")

    private fun soap(r: Renderer, action: String, args: String): String {
        val body = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
            "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
            "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body>" +
            "<u:$action xmlns:u=\"${r.avTransportType}\"><InstanceID>0</InstanceID>$args</u:$action>" +
            "</s:Body></s:Envelope>"
        val request = Request.Builder()
            .url(r.avTransportUrl)
            .post(body.toRequestBody("text/xml; charset=\"utf-8\"".toMediaType()))
            .header("SOAPACTION", "\"${r.avTransportType}#$action\"")
            .build()
        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val err = tagValue(text, "errorDescription") ?: tagValue(text, "faultstring")
                throw IOException("Telewizor odrzucił polecenie $action" + (err?.let { ": $it" } ?: " (HTTP ${resp.code})"))
            }
            return text
        }
    }

    /**
     * Serwery IPTV często przekierowują strumień na inny adres. Telewizor nie zawsze sobie z tym radzi,
     * więc podajemy mu adres docelowy (zapytanie HEAD — nie zajmuje połączenia na strumień).
     */
    private fun resolveRedirects(url: String): String = try {
        val req = Request.Builder().url(url).head().build()
        client.newBuilder().readTimeout(5, TimeUnit.SECONDS).build().newCall(req).execute().use { resp ->
            val finalUrl = resp.request.url.toString()
            if (resp.isSuccessful && finalUrl != url) finalUrl else url
        }
    } catch (e: Exception) {
        url
    }

    // ---------------- Pomocnicze ----------------

    private fun mimeOf(item: PlayItem, url: String): String {
        val path = url.substringBefore('?').lowercase()
        return when {
            item.kind == ContentKind.RADIO -> when {
                path.endsWith(".aac") -> "audio/aac"
                path.endsWith(".m3u8") -> "application/vnd.apple.mpegurl"
                else -> "audio/mpeg"
            }
            path.endsWith(".m3u8") -> "application/vnd.apple.mpegurl"
            path.endsWith(".mp4") || path.endsWith(".m4v") -> "video/mp4"
            path.endsWith(".mkv") -> "video/x-matroska"
            path.endsWith(".avi") -> "video/x-msvideo"
            path.endsWith(".mov") -> "video/quicktime"
            else -> "video/mp2t"
        }
    }

    private fun didl(item: PlayItem, url: String): String {
        val cls = if (item.kind == ContentKind.RADIO) "object.item.audioItem.musicTrack" else "object.item.videoItem"
        val title = if (item.subtitle != null && !item.isLive) "${item.title} – ${item.subtitle}" else item.title
        return "<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" " +
            "xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\">" +
            "<item id=\"0\" parentID=\"-1\" restricted=\"1\"><dc:title>${esc(title)}</dc:title>" +
            "<upnp:class>$cls</upnp:class>" +
            "<res protocolInfo=\"http-get:*:${mimeOf(item, url)}:*\">${esc(url)}</res></item></DIDL-Lite>"
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")

    private fun tagValue(xml: String, tag: String): String? =
        Regex("<(?:\\w+:)?$tag[^>]*>([^<]*)</(?:\\w+:)?$tag>").find(xml)?.groupValues?.get(1)?.trim()

    private fun hms(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
    }

    private fun parseHms(v: String?): Long {
        if (v.isNullOrBlank() || v.startsWith("NOT_")) return 0
        val parts = v.substringBefore('.').split(":").mapNotNull { it.toLongOrNull() }
        if (parts.size != 3) return 0
        return (parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000
    }
}
