package com.artykulacja.engine

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Wynik jednej głoski. score 0..1. heard = co usłyszał model zamiast (null = poprawnie). */
data class PhoneScore(
    val phone: Phone,
    val score: Float,
    val heard: String?,
    val startFrame: Int,
    val endFrame: Int,
)

data class WordScore(
    val text: String,
    val ipa: String,
    val phones: List<PhoneScore>,
    val score: Float,
    val known: Boolean,
    val startSec: Float,
    val endSec: Float,
)

data class Assessment(
    val words: List<WordScore>,
    val score: Float,
    val speechFound: Boolean,
    val heardText: String,
) {
    val scored get() = words.filter { it.known && it.phones.isNotEmpty() }
}

/** Przygotowany tekst do oceny: słowa z ich głoskami. */
data class Target(val text: String, val tokens: List<TextToken>, val words: List<Pair<TextToken, List<Phone>>>) {
    val allPhones: List<Phone> get() = words.flatMap { it.second }
}

class Scorer(private val rec: Recognizer) {

    /** Sekundy na ramkę wav2vec2. */
    val frameSec = 0.02f

    private val unitTokens = HashMap<String, IntArray?>()
    private fun tok(unit: String): IntArray? = unitTokens.getOrPut(unit) { rec.tokens(unit) }

    fun target(ph: Phonemizer, text: String, isolated: Boolean = false): Target {
        val tokens = ph.tokenize(text, isolated)
        val words = ArrayList<Pair<TextToken, List<Phone>>>()
        var wi = 0
        for (t in tokens) {
            if (!t.isWord) continue
            val phones = if (t.known && t.phon != null) Units.parse(t.phon, wi, t.text).filter { tok(it.unit) != null } else emptyList()
            words += t to phones
            wi++
        }
        return Target(text, tokens, words)
    }

    /** Ocena wymowy nagrania 16 kHz względem tekstu docelowego. */
    fun assess(audio16k: FloatArray, target: Target, strictness: Float = 1f): Assessment {
        val range = Dsp.speechRange(audio16k, 16000)
        val phones = target.allPhones
        if (range == null || phones.isEmpty()) {
            return Assessment(target.words.map { (t, p) ->
                WordScore(t.text, Units.displayIpa(p), p.map { PhoneScore(it, 0f, null, 0, 0) }, 0f, t.known, 0f, 0f)
            }, 0f, false, "")
        }
        val post = rec.posteriors(audio16k)
        return assessPosteriors(post, target, strictness)
    }

    fun assessPosteriors(post: Posteriors, target: Target, strictness: Float = 1f): Assessment {
        val phones = target.allPhones
        val seqParts = phones.map { tok(it.unit)!! }
        val seq = seqParts.flatMap { it.asList() }.toIntArray()
        val tokStart = IntArray(phones.size)
        run { var k = 0; for (i in phones.indices) { tokStart[i] = k; k += seqParts[i].size } }

        val ali = post.align(seq)
        val T = post.frames
        val spans = Array(phones.size) { i ->
            if (ali == null) intArrayOf(0, T - 1) else {
                val a = ali[tokStart[i]][0]
                val b = ali[tokStart[i] + seqParts[i].size - 1][1]
                intArrayOf(a, b)
            }
        }
        // rozszerzenie zakresów głosek o połowę przerwy do sąsiadów (do analizy prozodii i odtwarzania)
        val ext = Array(phones.size) { i ->
            val prevEnd = if (i > 0) spans[i - 1][1] else max(0, spans[i][0] - 12)
            val nextStart = if (i < phones.size - 1) spans[i + 1][0] else min(T - 1, spans[i][1] + 12)
            val a = (prevEnd + spans[i][0] + 1) / 2
            val b = (spans[i][1] + nextStart) / 2
            intArrayOf(min(a, spans[i][0]), max(b, spans[i][1]))
        }

        val scores = FloatArray(phones.size)
        val heard = arrayOfNulls<String>(phones.size)
        for (k in phones.indices) {
            val p = phones[k]
            val left = if (k > 0) seqParts[k - 1] else IntArray(0)
            val right = if (k < phones.size - 1) seqParts[k + 1] else IntArray(0)
            val t0 = (if (k > 0) ext[k - 1][0] else ext[k][0] - 4).coerceAtLeast(0)
            val t1 = (if (k < phones.size - 1) ext[k + 1][1] else ext[k][1] + 4).coerceAtMost(T - 1)

            fun lpOf(unitSeq: IntArray?): Float {
                if (unitSeq == null) return NEG
                return post.logProb(left + unitSeq + right, t0, t1)
            }

            var good = lpOf(seqParts[k])
            for (a in p.accept) good = lse(good, lpOf(tok(a)))
            var all = good
            var bestAlt: String? = null
            var bestAltLp = NEG
            val pool = if (p.vowel) Units.vowels + Units.extraVowels else Units.consonants
            for (alt in pool) {
                if (alt == p.unit || alt in p.accept) continue
                val lp = lpOf(tok(alt))
                all = lse(all, lp)
                if (lp > bestAltLp) { bestAltLp = lp; bestAlt = alt }
            }
            // pominięcie głoski
            val del = post.logProb(left + right, t0, t1)
            if (p.weak) {
                good = lse(good, del)
                all = lse(all, del)
            } else {
                val d = del + ln(0.5f)
                all = lse(all, d)
                if (d > bestAltLp) { bestAltLp = d; bestAlt = "∅" }
            }
            val prob = exp((good - all).coerceAtMost(0f))
            scores[k] = prob
            if (bestAltLp > good && prob < 0.5f) heard[k] = bestAlt
        }

        val words = ArrayList<WordScore>()
        var idx = 0
        for ((t, ph) in target.words) {
            val list = ArrayList<PhoneScore>()
            for (p in ph) {
                list += PhoneScore(p, calibrate(scores[idx], strictness), heard[idx], ext[idx][0], ext[idx][1])
                idx++
            }
            val ws = if (list.isEmpty()) 0f else {
                var num = 0f; var den = 0f
                for (s in list) {
                    val w = if (s.phone.weak) 0.4f else if (s.phone.vowel && s.phone.stress == 1) 1.5f else 1f
                    num += s.score * w; den += w
                }
                // najsłabsza głoska ciągnie słowo w dół
                val minS = list.filter { !it.phone.weak }.minOfOrNull { it.score } ?: (num / den)
                0.75f * (num / den) + 0.25f * minS
            }
            val st = list.firstOrNull()?.startFrame ?: 0
            val en = list.lastOrNull()?.endFrame ?: 0
            words += WordScore(t.text, Units.displayIpa(ph), list, ws, t.known, st * frameSec, (en + 1) * frameSec)
        }
        val scored = words.filter { it.known && it.phones.isNotEmpty() }
        val total = if (scored.isEmpty()) 0f else {
            val n = scored.sumOf { it.phones.size }
            scored.sumOf { (it.score * it.phones.size).toDouble() }.toFloat() / n
        }
        val heardText = post.greedy().joinToString("") { rec.inverse[it.first] ?: "" }
        return Assessment(words, total, true, heardText)
    }

    /**
     * Kalibracja prawdopodobieństwa na ocenę 0..1.
     * strictness: 0.8 = łagodnie, 1.0 = standard, 1.25 = wymagająco
     */
    fun calibrate(p: Float, strictness: Float): Float {
        val x = p.coerceIn(0f, 1f).toDouble().pow(0.45 * strictness)
        return x.toFloat()
    }

    /**
     * Która z dwóch wymów pasuje bardziej (pary minimalne).
     * Zwraca prawdopodobieństwo, że powiedziano A.
     */
    fun comparePair(audio16k: FloatArray, a: List<Phone>, b: List<Phone>): Float? {
        if (Dsp.speechRange(audio16k, 16000) == null) return null
        val post = rec.posteriors(audio16k)
        fun lp(ph: List<Phone>): Float {
            val seq = ph.flatMap { (tok(it.unit) ?: IntArray(0)).asList() }.toIntArray()
            return post.logProb(seq, 0, post.frames - 1)
        }
        val la = lp(a)
        val lb = lp(b)
        val m = max(la, lb)
        val ea = exp(la - m)
        val eb = exp(lb - m)
        return ea / (ea + eb)
    }
}
