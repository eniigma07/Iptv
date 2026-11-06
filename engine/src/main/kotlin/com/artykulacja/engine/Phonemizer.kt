package com.artykulacja.engine

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream

/** Słownik wymowy amerykańskiej (notacja misaki, z akcentami ˈ ˌ). */
class Lexicon private constructor(private val map: HashMap<String, Array<String>>) {

    /** [0] = domyślna, [1] = akcentowana (w izolacji, opcjonalna), [2] = forma czasownika (opcjonalna) */
    fun entry(w: String): Array<String>? = map[w] ?: map[w.lowercase()] ?: map[w.lowercase().replaceFirstChar { it.uppercase() }]

    fun has(w: String) = entry(w) != null

    companion object {
        fun load(gz: InputStream): Lexicon {
            val m = HashMap<String, Array<String>>(200_000)
            BufferedReader(InputStreamReader(GZIPInputStream(gz), Charsets.UTF_8), 1 shl 16).use { r ->
                while (true) {
                    val line = r.readLine() ?: break
                    val parts = line.split('\t')
                    if (parts.size >= 2) m[parts[0]] = parts.subList(1, parts.size).toTypedArray()
                }
            }
            return Lexicon(m)
        }
    }
}

/** Jedno słowo lub znak interpunkcyjny w tekście. */
data class TextToken(val text: String, val phon: String?, val isWord: Boolean, val known: Boolean)

class Phonemizer(private val lex: Lexicon) {

    private val tokenRe = Regex("[A-Za-z]+(?:'[A-Za-z]+)?|[0-9]+(?:[.,][0-9]+)?|[.,!?;:]")

    private val verbTriggers = setOf(
        "to", "will", "can", "could", "should", "would", "must", "let's", "please", "we", "i", "you", "they",
        "don't", "didn't", "won't", "might", "may", "and"
    )

    fun tokenize(text: String, isolated: Boolean = false): List<TextToken> {
        val raw = tokenRe.findAll(text.replace('’', '\'').replace('‘', '\'')).map { it.value }.toList()
        val out = ArrayList<TextToken>()
        val wordsOnly = raw.count { it[0].isLetterOrDigit() }
        var prev: String? = null
        for (t in raw) {
            if (!t[0].isLetterOrDigit()) {
                out += TextToken(t, null, false, true); prev = null; continue
            }
            if (t[0].isDigit()) {
                for (w in Numbers.toWords(t).split(' ')) {
                    val p = word(w, stressed = false) ?: guess(w)
                    out += TextToken(w, p, true, true)
                }
                prev = t; continue
            }
            val verb = prev != null && prev.lowercase() in verbTriggers
            val p = word(t, stressed = isolated || wordsOnly == 1, verb = verb)
            out += if (p != null) TextToken(t, p, true, true) else TextToken(t, guess(t), true, false)
            prev = t
        }
        return out
    }

    /** Ciąg fonemów dla Kokoro (interpunkcja zachowana, bo steruje intonacją). */
    fun kokoroString(tokens: List<TextToken>): String {
        val sb = StringBuilder()
        for (t in tokens) {
            if (t.isWord) {
                if (sb.isNotEmpty() && sb.last() != ' ') sb.append(' ')
                sb.append(t.phon ?: "")
            } else {
                sb.append(t.text)
                sb.append(' ')
            }
        }
        return sb.toString().trim()
    }

    fun word(w: String, stressed: Boolean = false, verb: Boolean = false): String? {
        lex.entry(w)?.let { return pick(it, stressed, verb) }
        val l = w.lowercase()
        fun base(b: String): String? = lex.entry(b)?.let { pick(it, true, false) }
        if (l.endsWith("'s") && l.length > 2) base(l.dropLast(2))?.let { return plural(it) }
        if (l.endsWith("s'")) base(l.dropLast(1))?.let { return it }
        if (l.endsWith("n't")) base(l.dropLast(3))?.let { return it + "ᵊnt" }
        if (l.endsWith("'re")) base(l.dropLast(3))?.let { return it + "ɹ" }
        if (l.endsWith("'ll")) base(l.dropLast(3))?.let { return it + "l" }
        if (l.endsWith("'ve")) base(l.dropLast(3))?.let { return it + "v" }
        if (l.endsWith("'d")) base(l.dropLast(2))?.let { return it + "d" }
        if (l.endsWith("ies") && l.length > 4) base(l.dropLast(3) + "y")?.let { return plural(it) }
        if (l.endsWith("es") && l.length > 3) base(l.dropLast(2))?.let { return plural(it) }
        if (l.endsWith("s") && l.length > 2) base(l.dropLast(1))?.let { return plural(it) }
        if (l.endsWith("ied") && l.length > 4) base(l.dropLast(3) + "y")?.let { return past(it) }
        if (l.endsWith("ed") && l.length > 3) {
            base(l.dropLast(2))?.let { return past(it) }
            base(l.dropLast(1))?.let { return past(it) }
            if (l.length > 4 && l[l.length - 3] == l[l.length - 4]) base(l.dropLast(3))?.let { return past(it) }
        }
        if (l.endsWith("ing") && l.length > 4) {
            base(l.dropLast(3))?.let { return it + "ɪŋ" }
            base(l.dropLast(3) + "e")?.let { return it + "ɪŋ" }
            if (l.length > 5 && l[l.length - 4] == l[l.length - 5]) base(l.dropLast(4))?.let { return it + "ɪŋ" }
        }
        if (l.endsWith("ly") && l.length > 3) base(l.dropLast(2))?.let { return it + "li" }
        if (l.endsWith("er") && l.length > 3) base(l.dropLast(2))?.let { return it + "əɹ" }
        if (l.endsWith("ment") && l.length > 5) base(l.dropLast(4))?.let { return it + "mᵊnt" }
        if (l.endsWith("ness") && l.length > 5) base(l.dropLast(4))?.let { return it + "nəs" }
        // złożenia: dwa słowa ze słownika
        for (i in 3..l.length - 3) {
            val a = lex.entry(l.substring(0, i)) ?: continue
            val b = lex.entry(l.substring(i)) ?: continue
            return pick(a, true, false) + pick(b, false, false).replace('ˈ', 'ˌ')
        }
        return null
    }

    private fun pick(e: Array<String>, stressed: Boolean, verb: Boolean): String {
        if (verb && e.size > 2 && e[2].isNotEmpty()) return e[2]
        if (stressed && e.size > 1 && e[1].isNotEmpty()) return e[1]
        return e[0]
    }

    private fun lastSound(p: String) = p.trimEnd('ˈ', 'ˌ').lastOrNull() ?: ' '

    private fun plural(b: String): String = when (lastSound(b)) {
        's', 'z', 'ʃ', 'ʒ', 'ʧ', 'ʤ' -> b + "ᵻz"
        'p', 't', 'k', 'f', 'θ' -> b + "s"
        else -> b + "z"
    }

    private fun past(b: String): String = when (lastSound(b)) {
        't', 'd', 'ɾ' -> b + "ᵻd"
        'p', 'k', 'f', 'θ', 's', 'ʃ', 'ʧ' -> b + "t"
        else -> b + "d"
    }

    /** Prosta wymowa z pisowni dla słów spoza słownika (tylko do odsłuchu, bez oceny). */
    fun guess(word: String): String {
        val w = word.lowercase().filter { it in 'a'..'z' }
        if (w.isEmpty()) return ""
        val sb = StringBuilder()
        var i = 0
        var stressed = false
        fun vowel(s: String) {
            if (!stressed) { sb.append('ˈ'); stressed = true }
            sb.append(s)
        }
        val silentE = w.length > 2 && w.endsWith("e") && w[w.length - 2] !in "aeiou"
        val n = if (silentE) w.length - 1 else w.length
        while (i < n) {
            val two = if (i + 1 < n) w.substring(i, i + 2) else ""
            when (two) {
                "th" -> { sb.append("θ"); i += 2; continue }
                "sh" -> { sb.append("ʃ"); i += 2; continue }
                "ch" -> { sb.append("ʧ"); i += 2; continue }
                "ph" -> { sb.append("f"); i += 2; continue }
                "ng" -> { sb.append("ŋ"); i += 2; continue }
                "ck" -> { sb.append("k"); i += 2; continue }
                "qu" -> { sb.append("kw"); i += 2; continue }
                "ee", "ea" -> { vowel("i"); i += 2; continue }
                "oo" -> { vowel("u"); i += 2; continue }
                "ou", "ow" -> { vowel("W"); i += 2; continue }
                "ai", "ay", "ei", "ey" -> { vowel("A"); i += 2; continue }
                "oa" -> { vowel("O"); i += 2; continue }
                "oi", "oy" -> { vowel("Y"); i += 2; continue }
                "au", "aw" -> { vowel("ɔ"); i += 2; continue }
                "er", "ir", "ur" -> { vowel("ɜɹ"); i += 2; continue }
                "ar" -> { vowel("ɑɹ"); i += 2; continue }
                "or" -> { vowel("ɔɹ"); i += 2; continue }
            }
            val c = w[i]
            val next = if (i + 1 < n) w[i + 1] else ' '
            when (c) {
                'a' -> vowel(if (silentE && i == n - 2) "A" else "æ")
                'e' -> vowel("ɛ")
                'i' -> vowel(if (silentE && i == n - 2) "I" else "ɪ")
                'o' -> vowel(if (silentE && i == n - 2) "O" else "ɑ")
                'u' -> vowel(if (silentE && i == n - 2) "u" else "ʌ")
                'y' -> if (i == 0) sb.append("j") else vowel(if (i == n - 1 && n > 2) "i" else "I")
                'c' -> sb.append(if (next in "eiy") "s" else "k")
                'g' -> sb.append(if (next in "eiy") "ʤ" else "ɡ")
                'j' -> sb.append("ʤ")
                'q' -> sb.append("k")
                'x' -> sb.append("ks")
                'r' -> sb.append("ɹ")
                else -> if (i == 0 || w[i - 1] != c) sb.append(c)
            }
            i++
        }
        return sb.toString()
    }
}

object Numbers {
    private val ones = arrayOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
        "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen"
    )
    private val tens = arrayOf("", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")

    fun toWords(s: String): String {
        val clean = s.replace(",", "")
        if (clean.contains('.')) {
            val (a, b) = clean.split('.', limit = 2)
            return toWords(a) + " point " + b.map { ones[it - '0'] }.joinToString(" ")
        }
        val n = clean.toLongOrNull() ?: return s
        if (n in 1900..2099 && clean.length == 4 && n % 100 != 0L && n !in 2000..2009) {
            return under100((n / 100).toInt()) + " " + under100((n % 100).toInt())
        }
        return big(n)
    }

    private fun under100(n: Int): String =
        if (n < 20) ones[n] else tens[n / 10] + if (n % 10 != 0) " " + ones[n % 10] else ""

    private fun big(n: Long): String {
        if (n < 100) return under100(n.toInt())
        if (n < 1000) return ones[(n / 100).toInt()] + " hundred" + if (n % 100 != 0L) " " + under100((n % 100).toInt()) else ""
        if (n < 1_000_000) return big(n / 1000) + " thousand" + if (n % 1000 != 0L) " " + big(n % 1000) else ""
        if (n < 1_000_000_000) return big(n / 1_000_000) + " million" + if (n % 1_000_000 != 0L) " " + big(n % 1_000_000) else ""
        return big(n / 1_000_000_000) + " billion" + if (n % 1_000_000_000 != 0L) " " + big(n % 1_000_000_000) else ""
    }
}
