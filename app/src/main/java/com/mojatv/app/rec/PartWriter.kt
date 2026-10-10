package com.mojatv.app.rec

import java.io.File
import java.io.FileOutputStream

/**
 * Zapis nagrania w kawałkach p000.ts, p001.ts… co [partMs].
 * Cięcie zawsze na granicy pakietu MPEG‑TS (188 bajtów), żeby każdy kawałek dał się odczytać osobno.
 */
class PartWriter(
    private val dir: File,
    private var index: Int,
    private val partMs: Long,
    private val onClosed: (File) -> Unit,
) {
    private var out: FileOutputStream? = null
    private var file: File? = null
    private var opened = 0L
    private var streamPos = 0L

    /** Nowe połączenie z serwerem — strumień zaczyna się od początku pakietu. */
    fun newConnection() {
        streamPos = 0
    }

    fun write(buf: ByteArray, off: Int, len: Int) {
        var o = off
        var l = len
        if (out != null && System.currentTimeMillis() - opened >= partMs) {
            val k = ((188 - streamPos % 188) % 188).toInt()
            if (k <= l) {
                append(buf, o, k)
                o += k
                l -= k
                close()
            }
        }
        append(buf, o, l)
    }

    /** Między fragmentami HLS: zamyka kawałek, jeśli minął jego czas. */
    fun rotateIfDue() {
        if (out != null && System.currentTimeMillis() - opened >= partMs) close()
    }

    private fun append(buf: ByteArray, off: Int, len: Int) {
        if (len <= 0) return
        if (out == null) {
            dir.mkdirs()
            val f = File(dir, "p%03d.ts".format(index))
            file = f
            out = FileOutputStream(f, true)
            opened = System.currentTimeMillis()
        }
        out!!.write(buf, off, len)
        streamPos += len
    }

    fun close() {
        val o = out ?: return
        runCatching { o.close() }
        out = null
        val f = file
        file = null
        index++
        if (f == null) return
        // pozostałości po zerwanym połączeniu (kilka sekund) nie mają sensu jako osobny kawałek
        if (f.length() < 64 * 1024) f.delete() else onClosed(f)
    }
}
