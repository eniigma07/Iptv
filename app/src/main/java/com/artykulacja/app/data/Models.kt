package com.artykulacja.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/** Pobieranie i weryfikacja pakietu modeli offline (jednorazowo). */
class ModelStore(ctx: Context) {
    val dir = File(ctx.filesDir, "models").apply { mkdirs() }
    private val base = "https://github.com/eniigma07/Iptv/releases/download/artykulacja-models/"
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    data class Progress(val done: Long, val total: Long, val file: String, val error: String? = null, val finished: Boolean = false)

    private val _progress = MutableStateFlow(Progress(0, 0, ""))
    val progress: StateFlow<Progress> = _progress

    private val readyMark = File(dir, ".ready")

    fun isReady() = readyMark.exists() && listOf("kokoro.onnx", "phoneme.onnx", "voices.bin", "voices.json", "phoneme_vocab.json").all { File(dir, it).exists() }

    fun sizeOnDisk(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0

    fun delete() {
        dir.listFiles()?.forEach { it.delete() }
    }

    suspend fun download() = withContext(Dispatchers.IO) {
        try {
            val manifest = JSONObject(fetchText(base + "manifest.json"))
            val files = manifest.getJSONArray("files").let { a -> List(a.length()) { a.getJSONObject(it) } }
            val total = files.sumOf { it.getLong("size") }
            var doneBefore = 0L
            for (f in files) {
                val name = f.getString("name")
                val size = f.getLong("size")
                val sha = f.getString("sha256")
                val out = File(dir, name)
                if (out.exists() && out.length() == size && File(dir, "$name.ok").exists()) {
                    doneBefore += size
                    _progress.value = Progress(doneBefore, total, name)
                    continue
                }
                val part = File(dir, "$name.part")
                var attempt = 0
                while (true) {
                    try {
                        fetchFile(base + name, part, size) { got -> _progress.value = Progress(doneBefore + got, total, name) }
                        break
                    } catch (e: Exception) {
                        coroutineContext.ensureActive()
                        if (++attempt >= 6) throw e
                        kotlinx.coroutines.delay(2000L * attempt)
                    }
                }
                _progress.value = Progress(doneBefore + size, total, "Sprawdzam $name")
                if (sha256(part) != sha) {
                    part.delete()
                    throw IllegalStateException("Plik $name jest uszkodzony — spróbuj ponownie.")
                }
                out.delete()
                part.renameTo(out)
                File(dir, "$name.ok").writeText("1")
                doneBefore += size
            }
            readyMark.writeText(manifest.optInt("version", 1).toString())
            _progress.value = Progress(total, total, "", finished = true)
        } catch (e: Exception) {
            _progress.value = _progress.value.copy(error = e.message ?: e.toString())
        }
    }

    private fun fetchText(url: String): String {
        client.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) throw IllegalStateException("Serwer odpowiedział ${r.code}")
            return r.body!!.string()
        }
    }

    private suspend fun fetchFile(url: String, part: File, size: Long, onProgress: (Long) -> Unit) {
        val have = if (part.exists()) part.length() else 0L
        if (have >= size) return
        val req = Request.Builder().url(url).apply { if (have > 0) header("Range", "bytes=$have-") }.build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IllegalStateException("Serwer odpowiedział ${r.code}")
            val append = r.code == 206
            RandomAccessFile(part, "rw").use { raf ->
                if (append) raf.seek(have) else raf.setLength(0)
                var got = if (append) have else 0L
                val buf = ByteArray(1 shl 16)
                val input = r.body!!.byteStream()
                var lastReport = 0L
                while (true) {
                    coroutineContext.ensureActive()
                    val n = input.read(buf)
                    if (n < 0) break
                    raf.write(buf, 0, n)
                    got += n
                    if (got - lastReport > 512 * 1024) { onProgress(got); lastReport = got }
                }
                onProgress(got)
                if (got < size) throw IllegalStateException("Przerwane pobieranie")
            }
        }
    }

    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { s ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = s.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
