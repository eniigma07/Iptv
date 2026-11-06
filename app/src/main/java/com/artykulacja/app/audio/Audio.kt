package com.artykulacja.app.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/** Nagrywanie 16 kHz mono z automatycznym zatrzymaniem po ciszy. */
class Recorder {
    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level
    @Volatile private var stopRequested = false

    fun stop() { stopRequested = true }

    /**
     * Nagrywa do zatrzymania ręcznego, ciszy po mowie (autoStop) albo limitu czasu.
     * Zwraca próbki float [-1, 1].
     */
    @SuppressLint("MissingPermission")
    suspend fun record(maxSec: Float = 20f, autoStop: Boolean = true, silenceSec: Float = 1.1f): FloatArray =
        withContext(Dispatchers.IO) {
            stopRequested = false
            val sr = 16000
            val minBuf = AudioRecord.getMinBufferSize(sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val rec = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION, sr,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, max(minBuf, sr)
            )
            val out = ShortArrayBuilder(sr * 8)
            val chunk = ShortArray(sr / 50) // 20 ms
            var noise = -60f
            var speechStarted = false
            var silentFrames = 0
            var frames = 0
            try {
                rec.startRecording()
                while (coroutineContext.isActive && !stopRequested) {
                    val n = rec.read(chunk, 0, chunk.size)
                    if (n <= 0) continue
                    out.add(chunk, n)
                    frames++
                    var s = 0.0
                    for (i in 0 until n) s += chunk[i] * chunk[i].toDouble()
                    val rms = sqrt(s / n) / 32768.0
                    val db = (20 * log10(max(rms, 1e-6))).toFloat()
                    _level.value = ((db + 60f) / 50f).coerceIn(0f, 1f)
                    if (frames < 10) noise = if (frames == 1) db else noise * 0.8f + db * 0.2f
                    val thr = max(noise + 12f, -48f)
                    if (db > thr) { speechStarted = true; silentFrames = 0 } else if (speechStarted) silentFrames++
                    if (autoStop && speechStarted && silentFrames * 0.02f >= silenceSec) break
                    if (frames * 0.02f >= maxSec) break
                    // nikt nie mówi przez 8 s — kończymy
                    if (autoStop && !speechStarted && frames * 0.02f > 8f) break
                }
            } finally {
                runCatching { rec.stop() }
                rec.release()
                _level.value = 0f
            }
            val shorts = out.toArray()
            FloatArray(shorts.size) { shorts[it] / 32768f }
        }

    private class ShortArrayBuilder(cap: Int) {
        private var a = ShortArray(cap)
        private var n = 0
        fun add(src: ShortArray, len: Int) {
            if (n + len > a.size) a = a.copyOf(max(a.size * 2, n + len))
            System.arraycopy(src, 0, a, n, len)
            n += len
        }
        fun toArray() = a.copyOf(n)
    }
}

/** Odtwarzanie próbek float. Jedno odtwarzanie naraz. */
class Player {
    private var track: AudioTrack? = null
    private val _playing = MutableStateFlow<String?>(null)
    /** Etykieta tego, co aktualnie gra (np. „model”, „user”), albo null. */
    val playing: StateFlow<String?> = _playing

    @Synchronized
    fun stop() {
        track?.let { runCatching { it.pause(); it.flush(); it.stop() }; it.release() }
        track = null
        _playing.value = null
    }

    /** Odtwarza i czeka do końca. */
    suspend fun play(samples: FloatArray, sampleRate: Int, label: String = "", from: Int = 0, to: Int = samples.size) {
        stop()
        val a = from.coerceIn(0, samples.size)
        val b = to.coerceIn(a, samples.size)
        if (b - a < 10) return
        val pad = sampleRate / 20
        val data = FloatArray(b - a + pad)
        System.arraycopy(samples, a, data, 0, b - a)
        // łagodne wejście/wyjście, żeby nie trzaskało
        val fade = minOf(sampleRate / 200, (b - a) / 4)
        for (i in 0 until fade) {
            val g = i / fade.toFloat()
            data[i] *= g
            data[b - a - 1 - i] *= g
        }
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(data.size * 4)
            .build()
        synchronized(this) { track = t }
        t.write(data, 0, data.size, AudioTrack.WRITE_BLOCKING)
        _playing.value = label
        t.play()
        val durMs = data.size * 1000L / sampleRate
        val start = System.currentTimeMillis()
        try {
            while (coroutineContext.isActive && System.currentTimeMillis() - start < durMs + 50) {
                if (track !== t) return
                delay(30)
            }
        } finally {
            if (track === t) stop()
        }
    }

    /** Krótki sygnał przed nagrywaniem (tryb ciągły). */
    suspend fun beep() {
        val sr = 24000
        val n = sr / 9
        val s = FloatArray(n) { i ->
            val env = minOf(1f, i / 300f, (n - i) / 600f)
            (kotlin.math.sin(2 * Math.PI * 880 * i / sr).toFloat() * 0.25f * env)
        }
        play(s, sr, "beep")
    }
}
