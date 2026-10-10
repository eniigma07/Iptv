package com.mojatv.app.rec

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.mojatv.app.MojaTvApp
import com.mojatv.app.R
import com.mojatv.app.data.Http
import com.mojatv.app.ui.MainActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Nagrywanie w tle: zapisuje strumień kanału (MPEG‑TS albo HLS) do pliku do zaplanowanej godziny.
 * Działa przy zamkniętej aplikacji i wygaszonym ekranie; przy zerwaniu połączenia łączy się ponownie.
 */
class RecordingService : Service() {

    companion object {
        const val ACTION_START = "com.mojatv.app.rec.START"
        const val ACTION_STOP = "com.mojatv.app.rec.STOP"
        const val EXTRA_ID = "id"
        private const val CHANNEL_ID = "recording"
        private const val CHANNEL_DONE = "recording_done"
        private const val NOTIF_ID = 4711
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    private val store get() = MojaTvApp.instance.recordings
    private val client by lazy {
        Http.client.newBuilder().readTimeout(30, TimeUnit.SECONDS).build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannels()
        goForeground()
        val id = intent?.getStringExtra(EXTRA_ID)
        when (intent?.action) {
            ACTION_STOP -> if (id != null) jobs[id]?.cancel()
            else -> if (id != null) start(id)
        }
        if (jobs.isEmpty()) finishIfIdle()
        return START_NOT_STICKY
    }

    private fun start(id: String) {
        if (jobs.containsKey(id)) return
        val rec = store.get(id) ?: return
        if (rec.status == RecStatus.CANCELLED || rec.status == RecStatus.DONE) return
        if (rec.end <= System.currentTimeMillis()) {
            store.update(id) { it.copy(status = if (it.bytes > 0) RecStatus.DONE else RecStatus.FAILED) }
            return
        }
        acquireLocks()
        val job = scope.launch { record(rec) }
        jobs[id] = job
        job.invokeOnCompletion {
            jobs.remove(id)
            updateNotification()
            if (jobs.isEmpty()) finishIfIdle()
        }
        updateNotification()
    }

    private suspend fun record(rec: Recording) {
        val file = rec.filePath?.let { File(it) } ?: store.fileFor(rec)
        store.update(rec.id) { it.copy(status = RecStatus.RECORDING, filePath = file.absolutePath, error = null) }
        var written = if (file.exists()) file.length() else 0L
        var lastError: String? = null
        var cancelled = false
        try {
            val isHls = rec.channel.url.substringBefore('?').lowercase().endsWith(".m3u8")
            while (System.currentTimeMillis() < rec.end) {
                currentCoroutineContext().ensureActive()
                try {
                    written += if (isHls) recordHls(rec, file) else recordTs(rec, file)
                    lastError = null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastError = e.message ?: "Błąd połączenia"
                }
                store.update(rec.id) { it.copy(bytes = written) }
                if (System.currentTimeMillis() < rec.end) delay(3000) // ponowne połączenie
            }
        } catch (e: CancellationException) {
            cancelled = true
        } finally {
            val ok = written > 0
            store.update(rec.id) {
                it.copy(
                    status = when {
                        ok -> RecStatus.DONE
                        cancelled -> RecStatus.CANCELLED
                        else -> RecStatus.FAILED
                    },
                    bytes = written,
                    error = if (ok) null else (lastError ?: if (cancelled) null else "Nie udało się pobrać strumienia"),
                )
            }
            notifyFinished(rec, ok, lastError)
        }
    }

    /** Zapisuje strumień MPEG‑TS do pliku (dopisuje). Zwraca liczbę zapisanych bajtów. */
    private suspend fun recordTs(rec: Recording, file: File): Long {
        val call = client.newCall(Request.Builder().url(rec.channel.url).build())
        val job = currentCoroutineContext()[Job]
        val handle = job?.invokeOnCompletion { call.cancel() }
        var count = 0L
        try {
            call.execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("Serwer zwrócił błąd HTTP ${resp.code}")
                val input = resp.body?.byteStream() ?: throw IOException("Pusta odpowiedź")
                FileOutputStream(file, true).use { out ->
                    val buf = ByteArray(64 * 1024)
                    var lastUpdate = System.currentTimeMillis()
                    while (System.currentTimeMillis() < rec.end && currentCoroutineContext().isActive) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        count += n
                        if (System.currentTimeMillis() - lastUpdate > 15_000) {
                            lastUpdate = System.currentTimeMillis()
                            val total = file.length()
                            store.update(rec.id) { it.copy(bytes = total) }
                            updateNotification()
                        }
                    }
                }
            }
        } finally {
            handle?.dispose()
        }
        return count
    }

    /** Nagrywanie HLS: pobiera kolejne fragmenty z listy odtwarzania i dokleja je do pliku. */
    private suspend fun recordHls(rec: Recording, file: File): Long {
        var playlistUrl = rec.channel.url
        val seen = LinkedHashSet<String>()
        var count = 0L
        FileOutputStream(file, true).use { out ->
            while (System.currentTimeMillis() < rec.end) {
                currentCoroutineContext().ensureActive()
                val (text, finalUrl) = fetchText(playlistUrl)
                if (text.contains("#EXT-X-STREAM-INF")) {
                    // lista wariantów — wybieramy najwyższą jakość
                    playlistUrl = bestVariant(text, finalUrl) ?: throw IOException("Pusta lista HLS")
                    continue
                }
                val target = Regex("#EXT-X-TARGETDURATION:(\\d+)").find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 6
                val segments = text.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
                for (seg in segments) {
                    if (System.currentTimeMillis() >= rec.end) break
                    val segUrl = URL(URL(finalUrl), seg).toString()
                    if (!seen.add(segUrl)) continue
                    client.newCall(Request.Builder().url(segUrl).build()).execute().use { r ->
                        if (r.isSuccessful) {
                            r.body?.byteStream()?.use { input ->
                                count += input.copyTo(out)
                            }
                        }
                    }
                }
                while (seen.size > 300) seen.remove(seen.first())
                store.update(rec.id) { it.copy(bytes = file.length()) }
                updateNotification()
                if (text.contains("#EXT-X-ENDLIST")) break
                delay((target * 1000 / 2).coerceIn(1000, 10_000))
            }
        }
        return count
    }

    private fun fetchText(url: String): Pair<String, String> =
        client.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Serwer zwrócił błąd HTTP ${r.code}")
            (r.body?.string() ?: "") to r.request.url.toString()
        }

    private fun bestVariant(master: String, base: String): String? {
        val lines = master.lines()
        var best: String? = null
        var bestBw = -1L
        for (i in lines.indices) {
            val l = lines[i]
            if (l.startsWith("#EXT-X-STREAM-INF")) {
                val bw = Regex("BANDWIDTH=(\\d+)").find(l)?.groupValues?.get(1)?.toLongOrNull() ?: 0
                val uri = lines.drop(i + 1).firstOrNull { it.isNotBlank() && !it.startsWith("#") }?.trim() ?: continue
                if (bw > bestBw) {
                    bestBw = bw
                    best = URL(URL(base), uri).toString()
                }
            }
        }
        return best
    }

    // ---------------- Powiadomienia i blokady ----------------

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Nagrywanie", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_DONE, "Nagrania gotowe", NotificationManager.IMPORTANCE_DEFAULT))
    }

    private fun buildNotification(): Notification {
        val active = jobs.keys.mapNotNull { store.get(it) }
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_rec)
            .setOngoing(true)
            .setContentIntent(openApp)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        if (active.isEmpty()) {
            b.setContentTitle("MojaTV").setContentText("Przygotowanie nagrania…")
        } else {
            val first = active.first()
            val mb = first.bytes / (1024 * 1024)
            b.setContentTitle(if (active.size == 1) "Nagrywanie: ${first.title}" else "Nagrywanie (${active.size})")
                .setContentText("${first.channel.title} · $mb MB")
            active.take(2).forEach { r ->
                val stop = PendingIntent.getService(
                    this, r.id.hashCode(),
                    Intent(this, RecordingService::class.java).setAction(ACTION_STOP).putExtra(EXTRA_ID, r.id),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                b.addAction(0, if (active.size == 1) "Zatrzymaj" else "Zatrzymaj: ${r.title.take(20)}", stop)
            }
        }
        return b.build()
    }

    private fun goForeground() {
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        else if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        else 0
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(), type)
    }

    @SuppressLint("MissingPermission")
    private fun updateNotification() {
        try {
            NotificationManagerCompat.from(this).notify(NOTIF_ID, buildNotification())
        } catch (e: Exception) { /* brak zgody na powiadomienia */ }
    }

    @SuppressLint("MissingPermission")
    private fun notifyFinished(rec: Recording, ok: Boolean, error: String?) {
        val n = NotificationCompat.Builder(this, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_rec)
            .setContentTitle(if (ok) "Nagranie gotowe: ${rec.title}" else "Nagranie nieudane: ${rec.title}")
            .setContentText(if (ok) rec.channel.title else (error ?: rec.channel.title))
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 1, Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            )
            .build()
        try {
            NotificationManagerCompat.from(this).notify(rec.id.hashCode(), n)
        } catch (e: Exception) { /* */ }
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireLocks() {
        if (wakeLock == null) {
            wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MojaTV:recording").apply {
                    setReferenceCounted(false)
                    acquire()
                }
        }
        if (wifiLock == null) {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "MojaTV:recording").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    private fun releaseLocks() {
        try { wakeLock?.release() } catch (e: Exception) { /* */ }
        try { wifiLock?.release() } catch (e: Exception) { /* */ }
        wakeLock = null
        wifiLock = null
    }

    private fun finishIfIdle() {
        releaseLocks()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        releaseLocks()
        super.onDestroy()
    }
}
