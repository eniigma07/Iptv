package com.artykulacja.app.speech

import android.content.Context
import com.artykulacja.app.data.Settings
import com.artykulacja.engine.Assessment
import com.artykulacja.engine.Dsp
import com.artykulacja.engine.Kokoro
import com.artykulacja.engine.Lexicon
import com.artykulacja.engine.Phonemizer
import com.artykulacja.engine.Prosody
import com.artykulacja.engine.Recognizer
import com.artykulacja.engine.Scorer
import com.artykulacja.engine.Target
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Wynik jednej próby z pełną analizą. */
class Practice(
    val text: String,
    val assessment: Assessment,
    val audio16k: FloatArray,
    val userContour: FloatArray?,
    val modelContour: FloatArray?,
    val intonation: Float?,
    val stress: Map<Int, Prosody.StressResult>,
    val wpm: Int?,
    val modelStress: Map<Int, Prosody.StressResult> = emptyMap(),
) {
    val score get() = (assessment.score * 100).toInt()

    /** Czy akcent w słowie i zgadza się ze wzorcem (null = nie mierzono). */
    fun stressOk(i: Int): Boolean? = stress[i]?.let { Prosody.stressMatches(it, modelStress[i]) }
}

/** Wzorzec: nagranie syntetyczne z analizą (do porównań intonacji i akcentu). */
class ModelClip(val audio24k: FloatArray, val audio16k: FloatArray, val assessment: Assessment?, val contour: FloatArray?)

class Speech(private val ctx: Context, private val modelDir: File, private val settings: Settings) {

    sealed interface State {
        data object Idle : State
        data object Loading : State
        data object Ready : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    lateinit var phonemizer: Phonemizer
        private set
    private lateinit var tts: Kokoro
    private lateinit var rec: Recognizer
    lateinit var scorer: Scorer
        private set

    private val lock = Mutex()
    private val ttsCache = object : LinkedHashMap<String, FloatArray>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FloatArray>?) = size > 80
    }
    private val clipCache = object : LinkedHashMap<String, ModelClip>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ModelClip>?) = size > 30
    }

    suspend fun init() = withContext(Dispatchers.Default) {
        if (_state.value == State.Ready || _state.value == State.Loading) return@withContext
        _state.value = State.Loading
        try {
            val lex = ctx.assets.open("lexicon.tsv.gz").use { Lexicon.load(it) }
            phonemizer = Phonemizer(lex)
            val vocab = Kokoro.loadVocab(ctx.assets.open("kokoro_vocab.json").bufferedReader().use { it.readText() })
            val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)
            tts = Kokoro(modelDir, vocab, threads)
            rec = Recognizer(modelDir, threads)
            scorer = Scorer(rec)
            _state.value = State.Ready
            // rozgrzanie (pierwsze uruchomienie modeli jest wolniejsze)
            runCatching { tts.synth("hˈɛlO", settings.voiceSpec, 1f) }
            runCatching { rec.posteriors(FloatArray(16000)) }
        } catch (t: Throwable) {
            _state.value = State.Failed(t.message ?: t.toString())
        }
    }

    suspend fun awaitReady(): Boolean {
        val s = state.first { it == State.Ready || it is State.Failed }
        return s == State.Ready
    }

    fun target(text: String, isolated: Boolean = false): Target = scorer.target(phonemizer, text, isolated)

    private fun ttsKey(text: String, voice: String, speed: Float, isolated: Boolean) = "$voice|$speed|$isolated|$text"

    /** Synteza tekstu głosem wzorcowym (24 kHz). */
    suspend fun speak(text: String, slow: Boolean = false, isolated: Boolean = false, voice: String = settings.voiceSpec): FloatArray =
        withContext(Dispatchers.Default) {
            val speed = if (slow) settings.slowSpeed else settings.speed
            val key = ttsKey(text, voice, speed, isolated)
            synchronized(ttsCache) { ttsCache[key] }?.let { return@withContext it }
            val audio = lock.withLock {
                val toks = phonemizer.tokenize(text, isolated)
                var phs = phonemizer.kokoroString(toks)
                // Kokoro potrzebuje znaku końca zdania, inaczej krótkie wypowiedzi brzmią nienaturalnie
                if (phs.isNotEmpty() && phs.last() !in ".?!") phs += "."
                // długie teksty — zdaniami
                val sentences = splitSentences(phs)
                val parts = sentences.map { tts.synth(it, voice, speed) }
                val gap = FloatArray((Kokoro.SAMPLE_RATE * 0.25).toInt())
                parts.flatMapIndexed { i, p -> if (i == 0) p.asList() else gap.asList() + p.asList() }.toFloatArray()
            }
            val norm = Dsp.normalizePeak(audio, 0.92f)
            synchronized(ttsCache) { ttsCache[key] = norm }
            norm
        }

    /** Synteza z podanych fonemów (próbki głosów). */
    suspend fun speakPhonemes(phon: String, voice: String): FloatArray = withContext(Dispatchers.Default) {
        lock.withLock { Dsp.normalizePeak(tts.synth(phon, voice, settings.speed), 0.92f) }
    }

    fun phonemes(text: String) = phonemizer.kokoroString(phonemizer.tokenize(text))

    private fun splitSentences(ph: String): List<String> {
        if (ph.length < 380) return listOf(ph)
        val out = ArrayList<String>()
        val sb = StringBuilder()
        for (c in ph) {
            sb.append(c)
            if ((c == '.' || c == '?' || c == '!' || (c == ',' && sb.length > 250)) && sb.length > 20) {
                out += sb.toString().trim(); sb.clear()
            }
        }
        if (sb.isNotBlank()) out += sb.toString().trim()
        return out
    }

    /** Wzorzec z analizą (do intonacji i akcentu). */
    suspend fun modelClip(text: String, isolated: Boolean): ModelClip = withContext(Dispatchers.Default) {
        val key = ttsKey(text, settings.voiceSpec, settings.speed, isolated)
        synchronized(clipCache) { clipCache[key] }?.let { return@withContext it }
        val a24 = speak(text, false, isolated)
        val a16 = Dsp.resample(a24, 24000, 16000)
        val clip = lock.withLock {
            val tg = target(text, isolated)
            val asm = runCatching { scorer.assess(a16, tg) }.getOrNull()
            val contour = asm?.let { Prosody.wordContour(Prosody.contour(a16), it.words) }
            ModelClip(a24, a16, asm, contour)
        }
        synchronized(clipCache) { clipCache[key] = clip }
        clip
    }

    /** Pełna ocena nagrania. */
    suspend fun assess(audio16k: FloatArray, text: String, isolated: Boolean): Practice = withContext(Dispatchers.Default) {
        val tg = target(text, isolated)
        val asm = lock.withLock { scorer.assess(audio16k, tg, settings.strictness) }
        if (!asm.speechFound) return@withContext Practice(text, asm, audio16k, null, null, null, emptyMap(), null)
        val pitch = Prosody.contour(audio16k)
        val userContour = Prosody.wordContour(pitch, asm.words)
        val stress = HashMap<Int, Prosody.StressResult>()
        for ((i, w) in asm.words.withIndex()) {
            if (w.known && w.phones.count { it.phone.vowel } >= 2) Prosody.stress(audio16k, w, pitch)?.let { stress[i] = it }
        }
        val wordCount = asm.words.size
        var model: ModelClip? = null
        var inton: Float? = null
        val modelStress = HashMap<Int, Prosody.StressResult>()
        if (wordCount >= 3 || stress.isNotEmpty()) {
            model = runCatching { modelClip(text, isolated) }.getOrNull()
            if (wordCount >= 3) inton = model?.contour?.let { Prosody.similarity(it, userContour) }
            val ma = model?.assessment
            if (model != null && ma != null && stress.isNotEmpty()) {
                val mp = Prosody.contour(model.audio16k)
                for (i in stress.keys) ma.words.getOrNull(i)?.let { mw -> Prosody.stress(model.audio16k, mw, mp)?.let { modelStress[i] = it } }
            }
        }
        val dur = (asm.words.lastOrNull()?.endSec ?: 0f) - (asm.words.firstOrNull()?.startSec ?: 0f)
        val wpm = if (wordCount >= 4 && dur > 0.5f) (wordCount / dur * 60).toInt() else null
        Practice(text, asm, audio16k, userContour, model?.contour, inton, stress, wpm, modelStress)
    }

    suspend fun comparePair(audio16k: FloatArray, a: String, b: String): Float? = withContext(Dispatchers.Default) {
        val pa = target(a, true).allPhones
        val pb = target(b, true).allPhones
        lock.withLock { scorer.comparePair(audio16k, pa, pb) }
    }
}
