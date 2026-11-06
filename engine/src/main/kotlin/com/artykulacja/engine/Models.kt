package com.artykulacja.engine

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlin.math.sqrt

private fun jsonObjectInts(text: String): Map<String, Int> {
    // prosty parser {"a": 1, ...} — bez zależności od bibliotek JSON
    val out = HashMap<String, Int>()
    val re = Regex("\"((?:\\\\.|[^\"\\\\])*)\"\\s*:\\s*(-?\\d+)")
    for (m in re.findAll(text)) {
        val k = m.groupValues[1].replace("\\\"", "\"").replace(Regex("\\\\u([0-9a-fA-F]{4})")) {
            it.groupValues[1].toInt(16).toChar().toString()
        }.replace("\\\\", "\\")
        out[k] = m.groupValues[2].toInt()
    }
    return out
}

private fun sessionOptions(threads: Int) = OrtSession.SessionOptions().apply {
    setIntraOpNumThreads(threads)
    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
}

/** Rozpoznawanie głosek (wav2vec2, L2-ARCTIC). Wejście: 16 kHz mono. */
class Recognizer(dir: File, threads: Int = 4) : AutoCloseable {
    private val env = OrtEnvironment.getEnvironment()
    private val session = env.createSession(File(dir, "phoneme.onnx").path, sessionOptions(threads))
    val vocab: Map<String, Int> = jsonObjectInts(File(dir, "phoneme_vocab.json").readText())
    val blank = vocab["[PAD]"] ?: 0
    private val silence = listOfNotNull(vocab[" "], vocab["[UNK]"], vocab["ˌ"], vocab["|"]).toIntArray()
    val inverse: Map<Int, String> = vocab.entries.associate { it.value to it.key }

    /** Sekwencja tokenów modelu dla jednostki (np. „dʒ” → d ͡ ʒ). null, jeśli model jej nie zna. */
    fun tokens(unit: String): IntArray? {
        val chars = Units.modelChars(unit)
        val out = IntArray(chars.length)
        for ((i, c) in chars.withIndex()) out[i] = vocab[c.toString()] ?: return null
        return out
    }

    @Synchronized
    fun posteriors(audio16k: FloatArray): Posteriors {
        var mean = 0.0
        for (v in audio16k) mean += v
        mean /= audio16k.size.coerceAtLeast(1)
        var varr = 0.0
        for (v in audio16k) varr += (v - mean) * (v - mean)
        val std = sqrt(varr / audio16k.size.coerceAtLeast(1) + 1e-7)
        val norm = FloatArray(audio16k.size) { ((audio16k[it] - mean) / std).toFloat() }
        OnnxTensor.createTensor(env, FloatBuffer.wrap(norm), longArrayOf(1, norm.size.toLong())).use { input ->
            session.run(mapOf("input_values" to input)).use { res ->
                @Suppress("UNCHECKED_CAST")
                val logits = (res[0].value as Array<Array<FloatArray>>)[0]
                return Posteriors.fromLogits(logits, blank, silence)
            }
        }
    }

    override fun close() = session.close()
}

/** Głos wzorcowy Kokoro-82M. Wyjście: 24 kHz mono. */
class Kokoro(dir: File, private val vocab: Map<String, Int>, threads: Int = 4) : AutoCloseable {
    private val env = OrtEnvironment.getEnvironment()
    private val session = env.createSession(File(dir, "kokoro.onnx").path, sessionOptions(threads))
    private val voices: Map<String, Array<FloatArray>>

    init {
        val idx = File(dir, "voices.json").readText()
        val bytes = File(dir, "voices.bin").readBytes()
        val fb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        val m = HashMap<String, Array<FloatArray>>()
        val re = Regex("\\{[^}]*\"name\"\\s*:\\s*\"([^\"]+)\"[^}]*\"offset\"\\s*:\\s*(\\d+)[^}]*\"rows\"\\s*:\\s*(\\d+)[^}]*\\}")
        for (mt in re.findAll(idx)) {
            val name = mt.groupValues[1]
            val off = mt.groupValues[2].toLong() / 4
            val rows = mt.groupValues[3].toInt()
            m[name] = Array(rows) { r ->
                FloatArray(256) { c -> fb.get((off + r * 256L + c).toInt()) }
            }
        }
        voices = m
    }

    val voiceNames: Set<String> get() = voices.keys

    /** Specyfikacja głosu: „af_heart” albo mieszanka „af_heart:0.7,af_bella:0.3”. */
    private fun style(spec: String, nTokens: Int): FloatArray {
        val parts = spec.split(',').mapNotNull {
            val p = it.trim().split(':')
            val v = voices[p[0]] ?: return@mapNotNull null
            v to (p.getOrNull(1)?.toFloatOrNull() ?: 1f)
        }.ifEmpty { listOf((voices["af_heart"] ?: voices.values.first()) to 1f) }
        val total = parts.sumOf { it.second.toDouble() }.toFloat().takeIf { it > 0 } ?: 1f
        val out = FloatArray(256)
        for ((v, w) in parts) {
            val row = v[(nTokens - 1).coerceIn(0, v.size - 1)]
            for (i in 0 until 256) out[i] += row[i] * w / total
        }
        return out
    }

    /** Synteza ciągu fonemów misaki. speed: 1.0 = normalnie, 0.75 = wolniej. */
    @Synchronized
    fun synth(phonemes: String, voice: String, speed: Float = 1f): FloatArray {
        val ids = phonemes.mapNotNull { vocab[it.toString()] }.take(500)
        if (ids.isEmpty()) return FloatArray(0)
        val tokens = LongArray(ids.size + 2)
        for (i in ids.indices) tokens[i + 1] = ids[i].toLong()
        val st = style(voice, ids.size)
        OnnxTensor.createTensor(env, LongBuffer.wrap(tokens), longArrayOf(1, tokens.size.toLong())).use { t ->
            OnnxTensor.createTensor(env, FloatBuffer.wrap(st), longArrayOf(1, 256)).use { s ->
                OnnxTensor.createTensor(env, FloatBuffer.wrap(floatArrayOf(speed)), longArrayOf(1)).use { sp ->
                    session.run(mapOf("input_ids" to t, "style" to s, "speed" to sp)).use { res ->
                        val v = res[0].value
                        @Suppress("UNCHECKED_CAST")
                        return when (v) {
                            is Array<*> -> (v as Array<FloatArray>)[0]
                            is FloatArray -> v
                            else -> FloatArray(0)
                        }
                    }
                }
            }
        }
    }

    override fun close() = session.close()

    companion object {
        const val SAMPLE_RATE = 24000
        fun loadVocab(json: String): Map<String, Int> = jsonObjectInts(json)
    }
}
