package com.artykulacja.engine

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max

internal const val NEG = -1e30f

internal fun lse(a: Float, b: Float): Float {
    if (a <= NEG) return b
    if (b <= NEG) return a
    return if (a > b) a + ln(1f + exp(b - a)) else b + ln(1f + exp(a - b))
}

/**
 * Log-prawdopodobieństwa ramek modelu CTC (T × V), z tokenami „ciszy” (spacja, [UNK], ˌ)
 * dołączonymi do tokenu pustego.
 */
class Posteriors(val lp: Array<FloatArray>, val blank: Int) {
    val frames get() = lp.size

    /** log P(seq | ramki [t0, t1]) — algorytm forward CTC. */
    fun logProb(seq: IntArray, t0: Int, t1: Int): Float {
        val a0 = t0.coerceIn(0, frames - 1)
        val a1 = t1.coerceIn(a0, frames - 1)
        val L = seq.size
        if (L == 0) {
            var s = 0f
            for (t in a0..a1) s += lp[t][blank]
            return s
        }
        val S = 2 * L + 1
        val ext = IntArray(S) { if (it % 2 == 0) blank else seq[it / 2] }
        var prev = FloatArray(S) { NEG }
        var cur = FloatArray(S)
        prev[0] = lp[a0][blank]
        prev[1] = lp[a0][ext[1]]
        for (t in a0 + 1..a1) {
            val row = lp[t]
            for (s in 0 until S) {
                var v = prev[s]
                if (s > 0) v = lse(v, prev[s - 1])
                if (s > 1 && ext[s] != blank && ext[s] != ext[s - 2]) v = lse(v, prev[s - 2])
                cur[s] = if (v <= NEG) NEG else v + row[ext[s]]
            }
            val tmp = prev; prev = cur; cur = tmp
        }
        return lse(prev[S - 1], prev[S - 2])
    }

    /**
     * Wymuszone dopasowanie (Viterbi). Zwraca dla każdego tokenu sekwencji
     * zakres ramek [first, last], w których był emitowany.
     */
    fun align(seq: IntArray): Array<IntArray>? {
        val T = frames
        val L = seq.size
        if (L == 0 || T == 0) return null
        val S = 2 * L + 1
        val ext = IntArray(S) { if (it % 2 == 0) blank else seq[it / 2] }
        val back = Array(T) { ByteArray(S) }
        var prev = FloatArray(S) { NEG }
        var cur = FloatArray(S)
        prev[0] = lp[0][blank]
        prev[1] = lp[0][ext[1]]
        for (t in 1 until T) {
            val row = lp[t]
            for (s in 0 until S) {
                var best = prev[s]
                var bp: Byte = 0
                if (s > 0 && prev[s - 1] > best) { best = prev[s - 1]; bp = 1 }
                if (s > 1 && ext[s] != blank && ext[s] != ext[s - 2] && prev[s - 2] > best) { best = prev[s - 2]; bp = 2 }
                cur[s] = if (best <= NEG) NEG else best + row[ext[s]]
                back[t][s] = bp
            }
            val tmp = prev; prev = cur; cur = tmp
        }
        var s = if (prev[S - 1] >= prev[S - 2]) S - 1 else S - 2
        if (prev[s] <= NEG) return null
        val first = IntArray(L) { -1 }
        val last = IntArray(L) { -1 }
        for (t in T - 1 downTo 0) {
            if (s % 2 == 1) {
                val k = s / 2
                if (last[k] < 0) last[k] = t
                first[k] = t
            }
            s -= back[t][s].toInt()
        }
        // tokeny bez ramek (nie powinno się zdarzyć) — wypełnij sąsiadami
        for (k in 0 until L) if (first[k] < 0) {
            val p = if (k > 0) last[k - 1] else 0
            first[k] = max(p, 0); last[k] = first[k]
        }
        return Array(L) { intArrayOf(first[it], last[it]) }
    }

    /** Zachłanne dekodowanie (do podglądu, co usłyszał model). */
    fun greedy(): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>()
        var prev = -1
        for (t in 0 until frames) {
            val row = lp[t]
            var bi = 0
            for (v in row.indices) if (row[v] > row[bi]) bi = v
            if (bi != prev && bi != blank) out += bi to t
            prev = bi
        }
        return out
    }

    companion object {
        /** Logity → log-softmax, z połączeniem tokenów ciszy w token pusty. */
        fun fromLogits(logits: Array<FloatArray>, blank: Int, silence: IntArray): Posteriors {
            val V = logits.firstOrNull()?.size ?: 0
            val lp = Array(logits.size) { t ->
                val row = logits[t]
                var m = Float.NEGATIVE_INFINITY
                for (v in row) if (v > m) m = v
                var z = 0f
                for (v in row) z += exp(v - m)
                val lz = m + ln(z)
                val out = FloatArray(V) { row[it] - lz }
                var b = out[blank]
                for (s in silence) if (s != blank) { b = lse(b, out[s]); out[s] = NEG }
                out[blank] = b
                out
            }
            return Posteriors(lp, blank)
        }
    }
}
