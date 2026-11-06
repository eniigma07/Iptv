package com.artykulacja.engine

import org.junit.Test
import java.io.File
import java.util.Locale

/** Sprawdzenie całego silnika na prawdziwych modelach (uruchamiane w CI). */
class EngineCheck {

    private fun f(x: Float) = String.format(Locale.US, "%.2f", x)

    @Test
    fun fullPipeline() {
        val dirName = System.getenv("MODELS_DIR")
        if (dirName.isNullOrEmpty()) { println("MODELS_DIR nie ustawione — pomijam"); return }
        val dir = File(dirName)
        val root = File(System.getProperty("user.dir")).parentFile
        val lex = Lexicon.load(File(root, "app/src/main/assets/lexicon.dat").inputStream())
        val ph = Phonemizer(lex)
        val tts = Kokoro(dir, Kokoro.loadVocab(File(root, "app/src/main/assets/kokoro_vocab.json").readText()))
        val rec = Recognizer(dir)
        val sc = Scorer(rec)
        println("Głosy: ${tts.voiceNames.sorted()}")

        fun say(text: String, voice: String = "af_heart", speed: Float = 1f): FloatArray {
            val toks = ph.tokenize(text)
            val t0 = System.nanoTime()
            var phs = ph.kokoroString(toks)
            if (phs.isNotEmpty() && phs.last() !in ".?!") phs += "."
            val a = tts.synth(phs, voice, speed)
            val ms = (System.nanoTime() - t0) / 1e6
            val a16 = Dsp.resample(a, 24000, 16000)
            println("   [TTS ${ms.toInt()} ms, ${f(a.size / 24000f)} s]  ${ph.kokoroString(toks)}")
            return a16
        }
        fun sayPh(phon: String, voice: String = "af_heart"): FloatArray =
            Dsp.resample(tts.synth(if (phon.last() in ".?!") phon else "$phon.", voice, 1f), 24000, 16000)

        fun report(label: String, audio: FloatArray, target: String, isolated: Boolean = false): Assessment {
            val tg = sc.target(ph, target, isolated)
            val t0 = System.nanoTime()
            val a = sc.assess(audio, tg)
            val ms = (System.nanoTime() - t0) / 1e6
            println("== $label | cel: \"$target\" | WYNIK ${(a.score * 100).toInt()} | ${ms.toInt()} ms | słyszę: ${a.heardText}")
            for (w in a.words) {
                val ps = w.phones.joinToString(" ") { p ->
                    p.phone.ipa + "=" + (p.score * 100).toInt() + (p.heard?.let { "(→$it)" } ?: "") + (if (p.phone.weak) "w" else "")
                }
                println("   ${w.text.padEnd(14)} /${w.ipa}/ ${(w.score * 100).toInt()}  $ps")
            }
            return a
        }

        println("\n######## 1. WZORZEC (głos syntetyczny = native) ########")
        val sentences = listOf(
            "I think this is the right strategy for the third quarter.",
            "We need to review the budget with the whole team.",
            "The manager asked for a clear plan and a realistic timeline.",
            "Let's focus on the things we can actually deliver this month.",
            "Our stakeholders want a status update on Thursday.",
            "Very well, let's move forward with the proposal.",
        )
        val native = ArrayList<Float>()
        for (s in sentences) native += report("native", say(s), s).score
        for (v in listOf("am_michael", "af_nicole", "af_bella")) native += report("native $v", say(sentences[0], v), sentences[0]).score
        println("ŚREDNIA native: ${f(native.average().toFloat())}")

        println("\n######## 2. BŁĘDY (powiedziane ≠ cel) ########")
        val wrong = listOf(
            "sˈɪŋk" to "think", "tˈæŋk" to "thank", "fɹˈi" to "three", "dˈA" to "they", "zˈɛn" to "then",
            "ʃˈip" to "ship", "ʃˈɪp" to "sheep", "bˈɛd" to "bad", "bˈæt" to "bad", "vˈɛst" to "west",
            "sˈɪn" to "sing", "kˈæt" to "cut", "pɹˈɔʤɛkt" to "project", "ɡˈo" to "go", "mˈɛnəʤəɹ" to "manager",
        )
        for ((said, tgt) in wrong) report("powiedziano /$said/", sayPh(said), tgt, isolated = true)
        println("-- poprawne słowa w izolacji:")
        for (w in listOf("think", "they", "ship", "sheep", "bad", "west", "sing", "cut", "project", "go", "manager", "three", "thanks", "very", "world", "focus")) {
            report("ok", say(w), w, isolated = true)
        }
        println("-- zdanie z polskim akcentem:")
        report("polski akcent", sayPh("ˈI sˈɪŋk dɪs ɪz də ɹˈIt stɹˈɛtɛɡi fɔɹ də tˈɜɹd kvˈɔɹtəɹ."),
            "I think this is the right strategy for the third quarter.")
        report("inne zdanie", say("Can you send me the report tomorrow?"), sentences[1])

        println("\n######## 3. PARY MINIMALNE ########")
        for ((a, b) in listOf("ship" to "sheep", "think" to "sink", "west" to "vest", "bad" to "bed", "sing" to "sin")) {
            val pa = sc.target(ph, a, true).allPhones
            val pb = sc.target(ph, b, true).allPhones
            println("   powiedziano '$a': P($a)=${f(sc.comparePair(say(a), pa, pb) ?: -1f)}   powiedziano '$b': P($a)=${f(sc.comparePair(say(b), pa, pb) ?: -1f)}")
        }

        println("\n######## 4. AKCENT WYRAZOWY (wzorzec heart, „użytkownik” michael / zły akcent) ########")
        var ok = 0; var n = 0; var okBad = 0; var nBad = 0
        fun stressOf(audio: FloatArray, word: String): Prosody.StressResult? {
            val a = sc.assess(audio, sc.target(ph, word, true))
            return Prosody.stress(audio, a.words[0], Prosody.contour(audio))
        }
        val badStress = mapOf(
            "development" to "dˈɛvələpmənt.", "analysis" to "ˈænəlɪsɪs.", "deliverable" to "dˈɛlɪvəɹəbəl.",
            "opportunity" to "ˈɑpɚtunəti.", "presentation" to "pɹˈɛzəntAʃən.", "economy" to "ˈikɑnəmi.",
            "committee" to "kˈɑmɪti.", "photographer" to "fˈOtəɡɹæfəɹ.", "success" to "sˈʌksɛs.",
            "strategy" to "stɹətˈɛʤi.", "budget" to "bʌʤˈɛt.", "implementation" to "ˈɪmpləməntAʃən.",
        )
        for ((w, wrongPh) in badStress) {
            val model = stressOf(say(w, "af_heart"), w)
            val good = stressOf(say(w, "am_michael"), w)
            val bad = stressOf(sayPh(wrongPh, "am_michael"), w)
            if (good != null) { n++; if (Prosody.stressMatches(good, model)) ok++ }
            if (bad != null) { nBad++; if (!Prosody.stressMatches(bad, model)) okBad++ }
            println("   ${w.padEnd(16)} słownik=${model?.expected} wzorzec=${model?.detected} dobry=${good?.detected}(${good?.let { Prosody.stressMatches(it, model) }}) zły=${bad?.detected}(${bad?.let { Prosody.stressMatches(it, model) }})")

        }
        println("AKCENT: poprawny uznany $ok / $n, błędny wykryty $okBad / $nBad")

        println("\n######## 4b. POJEDYNCZE SŁOWA: warianty syntezy ########")
        for (w in listOf("think", "ship", "cut", "go", "focus", "they", "west", "budget")) {
            val tg = sc.target(ph, w, true)
            val p = ph.kokoroString(ph.tokenize(w, true))
            val variants = linkedMapOf(
                "A '$p.'" to "$p.",
                "B '$p!'" to "$p!",
                "C '$p, $p.'" to "$p, $p.",
                "D '…$p.'" to "… $p.",
            )
            val line = StringBuilder("   ${w.padEnd(8)}")
            for ((label, v) in variants) {
                val audio = Dsp.resample(tts.synth(v, "af_heart", 1f), 24000, 16000)
                val a = sc.assess(audio, tg)
                line.append("  $label=${(a.score * 100).toInt()}[${a.heardText}]")
            }
            // E: w zdaniu nośnym, ocena słowa w kontekście
            val carrier = "The next word is $w."
            val audio = say(carrier)
            val a = sc.assess(audio, sc.target(ph, carrier))
            line.append("  E carrier=${(a.words.last().score * 100).toInt()} (${f(a.words.last().startSec)}-${f(a.words.last().endSec)}s)")
            println(line)
        }

        println("\n######## 5. INTONACJA ########")
        val s = sentences[3]
        val tgt = sc.target(ph, s)
        val aA = say(s, "af_heart"); val aB = say(s, "am_michael"); val aC = sayPh(
            ph.kokoroString(ph.tokenize(s)).replace(Regex("[.,?!]"), "").replace("ˈ", "").replace("ˌ", ""), "af_heart")
        val asA = sc.assess(aA, tgt); val asB = sc.assess(aB, tgt); val asC = sc.assess(aC, tgt)
        val cA = Prosody.wordContour(Prosody.contour(aA), asA.words)
        val cB = Prosody.wordContour(Prosody.contour(aB), asB.words)
        val cC = Prosody.wordContour(Prosody.contour(aC), asC.words)
        println("   heart vs michael: ${Prosody.similarity(cA, cB)}   heart vs płaski (bez akcentów): ${Prosody.similarity(cA, cC)}")

        println("\n######## 6. LICZBY I SŁOWA SPOZA SŁOWNIKA ########")
        for (t in listOf("We grew by 25 percent in 2025.", "Omantel and Tuatara signed the deal.")) {
            val toks = ph.tokenize(t)
            println("   $t -> ${toks.joinToString(" ") { (if (it.known) "" else "?") + (it.phon ?: it.text) }}")
        }
    }
}
