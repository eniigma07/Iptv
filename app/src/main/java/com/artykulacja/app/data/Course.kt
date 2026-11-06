package com.artykulacja.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class SoundInfo(val key: String, val name: String, val example: String, val how: String)

data class CourseUnit(
    val id: String,
    val title: String,
    val hook: String,
    val target: String,
    val lesson: List<String>,
    val words: List<String>,
    val pairs: List<Pair<String, String>>,
    val sentences: List<String>,
)

data class StressWord(val word: String, val syllables: List<String>, val stress: Int)

data class PhraseCategory(val name: String, val items: List<String>)

data class VoiceInfo(val id: String, val name: String, val desc: String, val top: Boolean) {
    val female get() = id.startsWith("af")
}

class Course(
    val sounds: Map<String, SoundInfo>,
    val subs: Map<String, String>,
    val units: List<CourseUnit>,
    val stress: List<StressWord>,
    val phrases: List<PhraseCategory>,
    val placement: List<String>,
    val voices: List<VoiceInfo>,
) {
    fun unit(id: String) = units.firstOrNull { it.id == id }

    /** Wskazówka dla głoski (klucz jednostki rozpoznawania). */
    fun sound(unit: String): SoundInfo? = sounds[unit] ?: when (unit) {
        "ʌ" -> sounds["ʌ"]
        else -> null
    }

    fun subTip(target: String, heard: String): String? = subs["$target>$heard"]

    /** Lekcja, która ćwiczy daną głoskę. */
    fun unitForSound(unit: String): CourseUnit? {
        val id = when (unit) {
            "θ" -> "th1"; "ð" -> "th2"; "ɪ", "i" -> "ih"; "æ", "ɛ" -> "ae"; "ʌ", "ɑ", "ɔ" -> "uh"
            "d", "z", "b", "ɡ" -> "voice"; "ŋ" -> "ng"; "ɹ", "ɚ" -> "r"; "w", "v" -> "wv"
            "oʊ", "eɪ" -> "diph"; "ʊ", "u" -> "uu"
            else -> null
        } ?: return null
        return unit(id)
    }

    companion object {
        private fun JSONArray.strings() = List(length()) { getString(it) }

        fun load(ctx: Context): Course {
            val o = JSONObject(ctx.assets.open("course.json").bufferedReader().use { it.readText() })
            val sounds = HashMap<String, SoundInfo>()
            o.getJSONObject("sounds").let { s ->
                for (k in s.keys()) {
                    val v = s.getJSONObject(k)
                    val key = if (k == "ʌ_schwa") "ə" else k
                    sounds[key] = SoundInfo(key, v.getString("name"), v.getString("ex"), v.getString("how"))
                }
            }
            val subs = HashMap<String, String>()
            o.getJSONObject("subs").let { s -> for (k in s.keys()) subs[k] = s.getString(k) }
            val units = o.getJSONArray("units").let { a ->
                List(a.length()) { i ->
                    val u = a.getJSONObject(i)
                    val pairs = u.getJSONArray("pairs").let { p -> List(p.length()) { j -> p.getJSONArray(j).let { it.getString(0) to it.getString(1) } } }
                    CourseUnit(
                        u.getString("id"), u.getString("title"), u.getString("hook"),
                        u.getString("target").let { if (it == "ʌ_schwa") "ə" else it },
                        u.getJSONArray("lesson").strings(), u.getJSONArray("words").strings().distinct(), pairs,
                        u.getJSONArray("sentences").strings(),
                    )
                }
            }
            val stress = o.getJSONArray("stress").let { a ->
                List(a.length()) { i -> a.getJSONObject(i).let { StressWord(it.getString("word"), it.getJSONArray("syl").strings(), it.getInt("stress")) } }
            }
            val phrases = o.getJSONArray("phrases").let { a ->
                List(a.length()) { i -> a.getJSONObject(i).let { PhraseCategory(it.getString("cat"), it.getJSONArray("items").strings()) } }
            }
            val voices = o.getJSONArray("voices").let { a ->
                List(a.length()) { i -> a.getJSONObject(i).let { VoiceInfo(it.getString("id"), it.getString("name"), it.getString("desc"), it.getBoolean("top")) } }
            }
            return Course(sounds, subs, units, stress, phrases, o.getJSONArray("placement").strings(), voices)
        }
    }
}
