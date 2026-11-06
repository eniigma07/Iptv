package com.artykulacja.engine

/**
 * Jedna głoska do oceny.
 * @param ipa      zapis do wyświetlenia (IPA amerykańskie)
 * @param unit     jednostka rozpoznawania (klucz w inwentarzu modelu)
 * @param stress   1 = akcent główny, 2 = poboczny, 0 = bez akcentu, -1 = spółgłoska
 * @param accept   inne jednostki uznawane za poprawne (warianty wymowy)
 * @param weak     głoska zredukowana, której pominięcie jest dopuszczalne
 */
data class Phone(
    val ipa: String,
    val unit: String,
    val vowel: Boolean,
    val stress: Int,
    val accept: Set<String>,
    val weak: Boolean,
    val word: Int,
)

object Units {
    val consonants = listOf(
        "p", "b", "t", "d", "k", "ɡ", "f", "v", "θ", "ð", "s", "z", "ʃ", "ʒ", "h",
        "m", "n", "ŋ", "l", "ɹ", "w", "j", "tʃ", "dʒ"
    )
    val vowels = listOf("i", "ɪ", "ɛ", "æ", "ɑ", "ɔ", "ʌ", "ʊ", "u", "ɚ", "eɪ", "aɪ", "aʊ", "ɔɪ", "oʊ")

    /** Dodatkowe „polskie” warianty, które model potrafi rozpoznać (czyste e, o, a). */
    val extraVowels = listOf("e", "o", "a")

    /** Ciąg znaków modelu dla jednostki (afrykaty z łącznikiem ͡). */
    fun modelChars(unit: String): String = when (unit) {
        "tʃ" -> "t͡ʃ"
        "dʒ" -> "d͡ʒ"
        else -> unit
    }

    private val functionWords = setOf(
        "a", "an", "the", "to", "of", "for", "and", "or", "but", "at", "from", "as", "than", "that", "can",
        "could", "would", "should", "will", "was", "were", "have", "has", "had", "do", "does", "you", "your",
        "our", "we", "us", "them", "him", "her", "his", "some", "is", "are", "be", "been", "on", "in", "with",
        "by", "there", "their", "this", "it", "its", "my", "me", "i", "if", "so", "just", "what", "who", "how",
        "into", "onto", "about", "must", "shall", "am", "an", "not", "all", "up"
    )

    private val misakiVowels = "AIOWYaæɑɔəɛɜɪʊʌiuᵊᵻ"

    /** Zamienia wymowę misaki jednego słowa na listę głosek do oceny. */
    fun parse(phon: String, wordIndex: Int, wordText: String): List<Phone> {
        val out = ArrayList<Phone>()
        val fw = wordText.lowercase() in functionWords
        var pending = 0
        var i = 0
        val s = phon
        fun nextIsVowel(from: Int): Boolean {
            var j = from
            while (j < s.length && (s[j] == 'ˈ' || s[j] == 'ˌ')) j++
            return j < s.length && s[j] in misakiVowels
        }
        fun v(ipa: String, unit: String, accept: Set<String> = emptySet(), weak: Boolean = false) {
            val st = pending
            pending = 0
            var acc = accept
            var wk = weak
            if (fw) {
                acc = acc + setOf("ʌ", "ɪ", "ʊ", "ɚ", "ɛ", "ɑ", "i", "u", "æ", "ɔ")
                wk = true
            }
            if (unit == "ɑ") acc = acc + "ɔ"
            if (unit == "ɔ") acc = acc + "ɑ"
            out += Phone(ipa, unit, true, st, acc - unit, wk, wordIndex)
        }
        fun c(ipa: String, unit: String, accept: Set<String> = emptySet(), weak: Boolean = false) {
            out += Phone(ipa, unit, false, -1, accept, weak, wordIndex)
        }
        while (i < s.length) {
            val ch = s[i]
            val nx = if (i + 1 < s.length) s[i + 1] else ' '
            when (ch) {
                'ˈ' -> pending = 1
                'ˌ' -> pending = 2
                'A' -> v("eɪ", "eɪ", setOf("e"))
                'I' -> v("aɪ", "aɪ")
                'W' -> v("aʊ", "aʊ")
                'Y' -> v("ɔɪ", "ɔɪ")
                'O' -> v("oʊ", "oʊ")
                'ɜ' -> if (nx == 'ɹ') { v(if (pending == 1) "ɝ" else "ɚ", "ɚ"); i++ } else v("ɜ", "ɚ")
                'ə' -> if (nx == 'ɹ' && !nextIsVowel(i + 2)) { v("ɚ", "ɚ", setOf("ʌ")); i++ } else v("ə", "ʌ", setOf("ɪ", "ɛ"), weak = true)
                'ᵊ' -> v("ə", "ʌ", setOf("ɪ"), weak = true)
                'ɐ' -> v("ə", "ʌ", setOf("ɪ", "ɛ", "æ"), weak = true)
                'ᵻ' -> v("ɪ", "ɪ", setOf("ʌ"), weak = true)
                'i' -> v("i", "i", if (pending == 0) setOf("ɪ") else emptySet())
                'ɪ' -> v("ɪ", "ɪ", if (pending == 0) setOf("ʌ", "i") else emptySet())
                'u' -> v("u", "u", if (pending == 0) setOf("ʊ") else emptySet())
                'ʊ' -> v("ʊ", "ʊ", if (pending == 0) setOf("u") else emptySet())
                'ɛ', 'æ', 'ɑ', 'ɔ', 'ʌ' -> v(ch.toString(), ch.toString())
                'a' -> v("a", "ɑ")
                'ʤ' -> c("dʒ", "dʒ")
                'ʧ' -> c("tʃ", "tʃ")
                'ɾ' -> c("t", "t", setOf("d"))
                'ʔ', 'ʰ', 'ː' -> {}
                'x' -> c("k", "k")
                'ɡ', 'g' -> c("ɡ", "ɡ")
                'ŋ', 'θ', 'ð', 'ʃ', 'ʒ', 'ɹ', 'j', 'w', 'h', 'l', 'm', 'n', 'p', 'b', 't', 'd', 'k', 'f', 'v', 's', 'z' ->
                    c(ch.toString(), ch.toString())
                else -> {}
            }
            i++
        }
        // „h” i „r” po samogłosce w słowach funkcyjnych często znikają w mowie ciągłej (his, for, are)
        if (fw) for (k in out.indices) {
            val q = out[k]
            if (q.unit == "h" || (q.unit == "ɹ" && k > 0 && out[k - 1].vowel)) out[k] = q.copy(weak = true)
        }
        for (k in out.indices) {
            val q = out[k]
            val prev = out.getOrNull(k - 1)
            val next = out.getOrNull(k + 1)
            when {
                // poślizgi między samogłoskami (actually → ækʧuəli)
                (q.unit == "w" || q.unit == "j") && prev?.vowel == true && next?.vowel == true ->
                    out[k] = q.copy(weak = true)
                q.unit == "tʃ" && k > 0 -> out[k] = q.copy(accept = q.accept + "ʃ")
                q.unit == "oʊ" && (next?.unit == "l" || next?.unit == "ɹ") -> out[k] = q.copy(accept = q.accept + setOf("o", "ɔ"))
                q.unit == "ɛ" && next?.unit == "l" -> out[k] = q.copy(accept = q.accept + "æ")
                q.unit == "ð" && wordText.equals("with", true) -> out[k] = q.copy(accept = q.accept + "θ")
            }
        }
        // „t”/„d” w zbitkach na końcu (next steps, first time) bywa niesłyszalne
        if (out.size >= 2) {
            val last = out.last()
            val prev = out[out.size - 2]
            if ((last.unit == "t" || last.unit == "d") && !prev.vowel && prev.unit != "ɹ") {
                out[out.size - 1] = last.copy(weak = true)
            }
        }
        return out
    }

    /** Wyświetlany zapis słowa, np. /ˈθɪŋk/ z oznaczeniem akcentu przed sylabą. */
    fun displayIpa(phones: List<Phone>): String {
        val multi = phones.count { it.vowel } > 1
        val sb = StringBuilder()
        val starts = IntArray(phones.size)
        for ((i, p) in phones.withIndex()) {
            starts[i] = sb.length
            if (p.stress == 1 && multi) {
                // przesuń znak akcentu przed spółgłoskę nagłosu sylaby (jeśli jest przed nią samogłoska)
                var j = i
                if (i > 0 && !phones[i - 1].vowel && phones.subList(0, i - 1).any { it.vowel }) j = i - 1
                else if (phones.subList(0, i).none { it.vowel }) j = 0
                sb.insert(starts[j], 'ˈ')
                for (k in j..i) starts[k]++
            }
            sb.append(p.ipa)
        }
        return sb.toString()
    }
}
