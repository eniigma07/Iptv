package com.artykulacja.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

object Dsp {

    /** Zmiana częstotliwości próbkowania (okienkowany sinc, jakość wystarczająca dla mowy). */
    fun resample(x: FloatArray, from: Int, to: Int): FloatArray {
        if (from == to) return x.copyOf()
        val ratio = to.toDouble() / from
        val n = floor(x.size * ratio).toInt()
        val out = FloatArray(n)
        val cutoff = min(1.0, ratio) * 0.95
        val half = 24
        for (i in 0 until n) {
            val t = i / ratio
            val c = floor(t).toInt()
            var acc = 0.0
            var wsum = 0.0
            for (k in c - half + 1..c + half) {
                if (k < 0 || k >= x.size) continue
                val d = t - k
                val arg = PI * d * cutoff
                val sinc = if (abs(arg) < 1e-9) 1.0 else sin(arg) / arg
                val win = 0.5 + 0.5 * cos(PI * d / half)
                val w = sinc * win
                acc += x[k] * w
                wsum += w
            }
            out[i] = if (wsum != 0.0) (acc / wsum).toFloat() else 0f
        }
        return out
    }

    fun rms(x: FloatArray, from: Int = 0, to: Int = x.size): Float {
        var s = 0.0
        val a = from.coerceIn(0, x.size)
        val b = to.coerceIn(a, x.size)
        for (i in a until b) s += x[i] * x[i]
        return if (b > a) sqrt(s / (b - a)).toFloat() else 0f
    }

    fun db(v: Float) = (20 * log10(max(v, 1e-6f).toDouble())).toFloat()

    /** Normalizacja głośności do zadanego szczytu (bez przesterowania). */
    fun normalizePeak(x: FloatArray, peak: Float = 0.9f): FloatArray {
        var m = 0f
        for (v in x) m = max(m, abs(v))
        if (m < 1e-4f) return x.copyOf()
        val g = peak / m
        return FloatArray(x.size) { x[it] * g }
    }

    /**
     * Wykrycie fragmentu z mową (energia w ramkach 20 ms).
     * Zwraca zakres próbek [start, end) albo null, jeśli to cisza.
     */
    fun speechRange(x: FloatArray, sr: Int): IntRange? {
        val hop = sr / 50
        val frames = x.size / hop
        if (frames < 3) return null
        val e = FloatArray(frames) { db(rms(x, it * hop, (it + 1) * hop)) }
        val sorted = e.sorted()
        val noise = sorted[(frames * 0.1).toInt()]
        val loud = sorted[(frames * 0.95).toInt().coerceAtMost(frames - 1)]
        if (loud < -45f || loud - noise < 10f) return null
        val thr = max(noise + 0.35f * (loud - noise), -50f)
        var a = e.indexOfFirst { it > thr }
        var b = e.indexOfLast { it > thr }
        if (a < 0 || b < a) return null
        a = max(0, a - 8)
        b = min(frames - 1, b + 10)
        return (a * hop) until min(x.size, (b + 1) * hop)
    }

    /**
     * Wysokość głosu (YIN). Zwraca F0 w Hz co 10 ms (0 = brak dźwięczności).
     */
    fun pitch(x: FloatArray, sr: Int, fmin: Float = 70f, fmax: Float = 420f): FloatArray {
        val hop = sr / 100
        val win = (sr * 0.04).toInt()
        val tauMin = (sr / fmax).toInt()
        val tauMax = min((sr / fmin).toInt(), win - 1)
        val frames = max(0, (x.size - win - tauMax) / hop)
        val f0 = FloatArray(frames)
        val d = FloatArray(tauMax + 1)
        val gate = db(rms(x)) - 25f
        for (f in 0 until frames) {
            val off = f * hop
            if (db(rms(x, off, off + win)) < gate) continue
            for (tau in 1..tauMax) {
                var s = 0f
                for (j in 0 until win) {
                    val diff = x[off + j] - x[off + j + tau]
                    s += diff * diff
                }
                d[tau] = s
            }
            var run = 0f
            var best = -1
            d[0] = 1f
            val cm = FloatArray(tauMax + 1)
            cm[0] = 1f
            for (tau in 1..tauMax) {
                run += d[tau]
                cm[tau] = if (run > 0) d[tau] * tau / run else 1f
            }
            for (tau in tauMin..tauMax) {
                if (cm[tau] < 0.15f) {
                    var t = tau
                    while (t + 1 <= tauMax && cm[t + 1] < cm[t]) t++
                    best = t; break
                }
            }
            if (best < 0) continue
            // interpolacja paraboliczna
            val a = if (best > 1) cm[best - 1] else cm[best]
            val b = cm[best]
            val c = if (best < tauMax) cm[best + 1] else cm[best]
            val den = a - 2 * b + c
            val shift = if (abs(den) > 1e-9f) 0.5f * (a - c) / den else 0f
            f0[f] = sr / (best + shift)
        }
        // usunięcie pojedynczych skoków oktawowych (mediana z 5)
        val out = f0.copyOf()
        for (i in 2 until frames - 2) {
            if (f0[i] == 0f) continue
            val w = floatArrayOf(f0[i - 2], f0[i - 1], f0[i], f0[i + 1], f0[i + 2]).filter { it > 0 }.sorted()
            if (w.size >= 3) {
                val med = w[w.size / 2]
                if (abs(ln(f0[i] / med)) > 0.35) out[i] = med
            }
        }
        return out
    }

    /** F0 w półtonach względem mediany mówcy (NaN = bezdźwięczne). */
    fun semitones(f0: FloatArray): FloatArray {
        val voiced = f0.filter { it > 0 }.sorted()
        if (voiced.isEmpty()) return FloatArray(f0.size) { Float.NaN }
        val med = voiced[voiced.size / 2]
        return FloatArray(f0.size) { if (f0[it] > 0) (12 * ln(f0[it] / med) / ln(2f)) else Float.NaN }
    }
}
