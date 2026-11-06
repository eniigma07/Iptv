package com.artykulacja.engine

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Prozodia: akcent wyrazowy i intonacja. */
object Prosody {

    data class Syllable(val phone: PhoneScore, val prominence: Float)

    data class StressResult(
        val syllables: List<Syllable>,
        val expected: Int,     // indeks sylaby z akcentem głównym (wg słownika)
        val detected: Int,     // indeks sylaby najbardziej wyróżnionej w nagraniu
    ) {
        val correct get() = expected == detected
    }

    /** Krzywa wysokości (półtony) w ramkach 10 ms. */
    fun contour(audio16k: FloatArray): FloatArray {
        val a8 = Dsp.resample(audio16k, 16000, 8000)
        return Dsp.semitones(Dsp.pitch(a8, 8000))
    }

    /** Analiza akcentu w słowie na podstawie długości, głośności i wysokości samogłosek. */
    fun stress(audio16k: FloatArray, word: WordScore, pitchSt: FloatArray, frameSec: Float = 0.02f): StressResult? {
        val vowels = word.phones.filter { it.phone.vowel }
        if (vowels.size < 2) return null
        val expected = vowels.indexOfFirst { it.phone.stress == 1 }.let { if (it < 0) 0 else it }
        val dur = FloatArray(vowels.size)
        val en = FloatArray(vowels.size)
        val f0 = FloatArray(vowels.size)
        for ((i, v) in vowels.withIndex()) {
            val a = (v.startFrame * frameSec * 16000).toInt()
            val b = ((v.endFrame + 1) * frameSec * 16000).toInt()
            dur[i] = (v.endFrame - v.startFrame + 1).toFloat()
            en[i] = Dsp.db(Dsp.rms(audio16k, a, b))
            val pa = (v.startFrame * frameSec * 100).toInt().coerceIn(0, max(0, pitchSt.size - 1))
            val pb = ((v.endFrame + 1) * frameSec * 100).toInt().coerceIn(pa, pitchSt.size)
            val vals = (pa until pb).map { pitchSt[it] }.filter { !it.isNaN() }.sorted()
            f0[i] = if (vals.isEmpty()) Float.NaN else vals[vals.size / 2]
        }
        val fFill = f0.filter { !it.isNaN() }.let { if (it.isEmpty()) 0f else it.average().toFloat() }
        for (i in f0.indices) if (f0[i].isNaN()) f0[i] = fFill - 1f
        // ostatnia sylaba bywa wydłużona — lekka korekta
        dur[dur.size - 1] *= 0.8f
        val zd = z(dur); val ze = z(en); val zf = z(f0)
        val prom = FloatArray(vowels.size) { 0.35f * zd[it] + 0.4f * ze[it] + 0.25f * zf[it] }
        val mn = prom.min(); val mx = prom.max()
        val norm = prom.map { if (mx > mn) (it - mn) / (mx - mn) else 0.5f }
        val detected = norm.indices.maxBy { norm[it] }
        return StressResult(vowels.mapIndexed { i, v -> Syllable(v, norm[i]) }, expected, detected)
    }

    private fun z(x: FloatArray): FloatArray {
        val m = x.average().toFloat()
        val sd = sqrt(x.map { (it - m) * (it - m) }.average().toFloat()).takeIf { it > 1e-4f } ?: 1f
        return FloatArray(x.size) { (x[it] - m) / sd }
    }

    /**
     * Krzywa intonacji próbkowana w stałej liczbie punktów na słowo (do porównania i rysowania).
     * Zwraca tablicę [słowa × perWord] w półtonach (NaN = bezdźwięczne).
     */
    fun wordContour(pitchSt: FloatArray, words: List<WordScore>, perWord: Int = 10): FloatArray {
        val out = FloatArray(words.size * perWord) { Float.NaN }
        for ((wi, w) in words.withIndex()) {
            val a = (w.startSec * 100).toInt()
            val b = max(a + 1, (w.endSec * 100).toInt())
            for (k in 0 until perWord) {
                val c = a + (b - a) * (k + 0.5f) / perWord
                val lo = (c - 3).toInt().coerceAtLeast(0)
                val hi = min(pitchSt.size, (c + 3).toInt())
                val vals = (lo until hi).map { pitchSt[it] }.filter { !it.isNaN() }
                if (vals.isNotEmpty()) out[wi * perWord + k] = vals.average().toFloat()
            }
        }
        // wypełnienie krótkich luk
        var last = Float.NaN
        var gap = 0
        for (i in out.indices) {
            if (out[i].isNaN()) gap++ else {
                if (!last.isNaN() && gap in 1..4) for (j in 1..gap) out[i - j] = out[i] + (last - out[i]) * j / (gap + 1)
                last = out[i]; gap = 0
            }
        }
        return out
    }

    /** Podobieństwo dwóch krzywych intonacji 0..1 (korelacja kształtu). */
    fun similarity(a: FloatArray, b: FloatArray): Float? {
        val pairs = a.indices.filter { it < b.size && !a[it].isNaN() && !b[it].isNaN() }
        if (pairs.size < 6) return null
        val ma = pairs.map { a[it] }.average()
        val mb = pairs.map { b[it] }.average()
        var num = 0.0; var da = 0.0; var db = 0.0
        for (i in pairs) {
            val x = a[i] - ma; val y = b[i] - mb
            num += x * y; da += x * x; db += y * y
        }
        if (da < 1e-6 || db < 1e-6) return 0.5f
        val r = num / sqrt(da * db)
        // rozpiętość: zbyt płaska melodia (typowe dla Polaków) obniża wynik
        val rangeA = sqrt(da / pairs.size)
        val rangeB = sqrt(db / pairs.size)
        val flat = (rangeB / rangeA).coerceIn(0.0, 1.0)
        val s = ((r + 1) / 2) * (0.75 + 0.25 * flat)
        return s.toFloat().coerceIn(0f, 1f)
    }
}
