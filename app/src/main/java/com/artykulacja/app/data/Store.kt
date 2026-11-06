package com.artykulacja.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/** Ustawienia użytkownika (SharedPreferences, obserwowalne w Compose). */
class Settings(ctx: Context) {
    private val p = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private var _name by mutableStateOf(p.getString("name", "") ?: "")
    val name get() = _name
    private var _voice by mutableStateOf(p.getString("voice", "af_heart") ?: "af_heart")
    val voice get() = _voice
    private var _mixVoice by mutableStateOf(p.getString("mixVoice", "") ?: "")
    val mixVoice get() = _mixVoice
    private var _mixRatio by mutableStateOf(p.getFloat("mixRatio", 0.3f))
    val mixRatio get() = _mixRatio
    private var _speed by mutableStateOf(p.getFloat("speed", 1.0f))
    val speed get() = _speed
    private var _slowSpeed by mutableStateOf(p.getFloat("slowSpeed", 0.72f))
    val slowSpeed get() = _slowSpeed
    private var _autoPlay by mutableStateOf(p.getBoolean("autoPlay", true))
    val autoPlay get() = _autoPlay
    private var _showIpa by mutableStateOf(p.getBoolean("showIpa", true))
    val showIpa get() = _showIpa
    private var _strictness by mutableStateOf(p.getFloat("strictness", 1.0f))
    val strictness get() = _strictness
    private var _dailyGoal by mutableIntStateOf(p.getInt("dailyGoal", 20))
    val dailyGoal get() = _dailyGoal
    private var _reminder by mutableStateOf(p.getBoolean("reminder", false))
    val reminder get() = _reminder
    private var _reminderHour by mutableIntStateOf(p.getInt("reminderHour", 19))
    val reminderHour get() = _reminderHour
    private var _reminderMinute by mutableIntStateOf(p.getInt("reminderMinute", 30))
    val reminderMinute get() = _reminderMinute
    private var _onboarded by mutableStateOf(p.getBoolean("onboarded", false))
    val onboarded get() = _onboarded

    /** Specyfikacja głosu dla silnika (z mieszaniem). */
    val voiceSpec: String
        get() = if (mixVoice.isNotEmpty() && mixVoice != voice && mixRatio > 0.01f)
            "$voice:${1 - mixRatio},$mixVoice:$mixRatio" else voice

    fun update(block: Settings.() -> Unit) { block() }

    fun setName(v: String) { _name = v; p.edit().putString("name", v).apply() }
    fun setVoice(v: String) { _voice = v; p.edit().putString("voice", v).apply() }
    fun setMix(v: String, r: Float) { _mixVoice = v; _mixRatio = r; p.edit().putString("mixVoice", v).putFloat("mixRatio", r).apply() }
    fun setSpeed(v: Float) { _speed = v; p.edit().putFloat("speed", v).apply() }
    fun setSlowSpeed(v: Float) { _slowSpeed = v; p.edit().putFloat("slowSpeed", v).apply() }
    fun setAutoPlay(v: Boolean) { _autoPlay = v; p.edit().putBoolean("autoPlay", v).apply() }
    fun setShowIpa(v: Boolean) { _showIpa = v; p.edit().putBoolean("showIpa", v).apply() }
    fun setStrictness(v: Float) { _strictness = v; p.edit().putFloat("strictness", v).apply() }
    fun setDailyGoal(v: Int) { _dailyGoal = v; p.edit().putInt("dailyGoal", v).apply() }
    fun setReminder(on: Boolean, h: Int = reminderHour, m: Int = reminderMinute) {
        _reminder = on; _reminderHour = h; _reminderMinute = m
        p.edit().putBoolean("reminder", on).putInt("reminderHour", h).putInt("reminderMinute", m).apply()
    }
    fun setOnboarded(v: Boolean) { _onboarded = v; p.edit().putBoolean("onboarded", v).apply() }
}

data class Attempt(val day: String, val time: Long, val text: String, val score: Int, val source: String)

/** Postępy: opanowanie głosek, seria dni, historia prób. */
class Store(ctx: Context) {
    private val file = File(ctx.filesDir, "progress.json")

    /** Opanowanie głoski: średnia krocząca 0..100 i liczba wystąpień. */
    val mastery = HashMap<String, Pair<Float, Int>>()
    val days = HashMap<String, IntArray>()          // dzień → [próby, sekundy]
    val attempts = ArrayList<Attempt>()
    val unitDone = HashMap<String, Int>()           // id lekcji → liczba ukończonych sesji
    val myWords = ArrayList<String>()
    var placementDone = false
        private set
    var placementScore = 0
        private set

    /** Licznik zmian — Compose obserwuje go, by odświeżyć widoki. */
    var version by mutableIntStateOf(0)
        private set

    init { load() }

    private fun today() = LocalDate.now().toString()

    @Synchronized
    fun recordAttempt(text: String, score: Int, phones: List<Pair<String, Float>>, source: String) {
        for ((key, s) in phones) {
            val (m, n) = mastery[key] ?: (50f to 0)
            val alpha = if (n < 5) 0.35f else 0.15f
            mastery[key] = (m + alpha * (s * 100 - m)) to (n + 1)
        }
        val d = days.getOrPut(today()) { IntArray(2) }
        d[0]++
        attempts += Attempt(today(), System.currentTimeMillis(), text, score, source)
        if (attempts.size > 1500) attempts.subList(0, attempts.size - 1500).clear()
        save()
    }

    @Synchronized
    fun addSeconds(sec: Int) {
        if (sec <= 0) return
        val d = days.getOrPut(today()) { IntArray(2) }
        d[1] += sec
        save()
    }

    @Synchronized
    fun completeUnit(id: String) {
        unitDone[id] = (unitDone[id] ?: 0) + 1
        save()
    }

    @Synchronized
    fun setPlacement(score: Int) {
        placementDone = true; placementScore = score; save()
    }

    @Synchronized
    fun addWord(w: String) {
        val t = w.trim()
        if (t.isNotEmpty() && t !in myWords) { myWords.add(0, t); save() }
    }

    @Synchronized
    fun removeWord(w: String) { myWords.remove(w); save() }

    @Synchronized
    fun reset() {
        mastery.clear(); days.clear(); attempts.clear(); unitDone.clear(); placementDone = false; placementScore = 0
        save()
    }

    fun todayAttempts() = days[today()]?.get(0) ?: 0
    fun todaySeconds() = days[today()]?.get(1) ?: 0

    /** Liczba kolejnych dni z ćwiczeniem (dzisiaj liczy się, jeśli już ćwiczono; inaczej od wczoraj). */
    fun streak(): Int {
        var d = LocalDate.now()
        if ((days[d.toString()]?.get(0) ?: 0) == 0) d = d.minusDays(1)
        var n = 0
        while ((days[d.toString()]?.get(0) ?: 0) > 0) { n++; d = d.minusDays(1) }
        return n
    }

    fun bestStreak(): Int {
        val sorted = days.filter { it.value[0] > 0 }.keys.map { LocalDate.parse(it) }.sorted()
        var best = 0; var cur = 0; var prev: LocalDate? = null
        for (d in sorted) {
            cur = if (prev != null && prev.plusDays(1) == d) cur + 1 else 1
            best = maxOf(best, cur); prev = d
        }
        return best
    }

    /** Najsłabsze głoski (min. 3 wystąpienia). */
    fun weakest(n: Int = 5): List<Pair<String, Float>> =
        mastery.filter { it.value.second >= 3 }.map { it.key to it.value.first }.sortedBy { it.second }.take(n)

    /** Średni wynik z ostatnich dni (do wykresu). */
    fun dailyAverages(lastDays: Int = 14): List<Pair<LocalDate, Float?>> {
        val by = attempts.groupBy { it.day }
        val today = LocalDate.now()
        return (lastDays - 1 downTo 0).map { k ->
            val d = today.minusDays(k.toLong())
            d to by[d.toString()]?.map { it.score }?.average()?.toFloat()
        }
    }

    fun recentAverage(n: Int = 30): Int? = attempts.takeLast(n).takeIf { it.isNotEmpty() }?.map { it.score }?.average()?.toInt()

    private fun load() {
        if (!file.exists()) return
        runCatching {
            val o = JSONObject(file.readText())
            o.optJSONObject("mastery")?.let { m ->
                for (k in m.keys()) m.getJSONArray(k).let { mastery[k] = it.getDouble(0).toFloat() to it.getInt(1) }
            }
            o.optJSONObject("days")?.let { m ->
                for (k in m.keys()) m.getJSONArray(k).let { days[k] = intArrayOf(it.getInt(0), it.getInt(1)) }
            }
            o.optJSONArray("attempts")?.let { a ->
                for (i in 0 until a.length()) a.getJSONArray(i).let {
                    attempts += Attempt(it.getString(0), it.getLong(1), it.getString(2), it.getInt(3), it.optString(4, ""))
                }
            }
            o.optJSONObject("units")?.let { m -> for (k in m.keys()) unitDone[k] = m.getInt(k) }
            o.optJSONArray("myWords")?.let { a -> for (i in 0 until a.length()) myWords += a.getString(i) }
            placementDone = o.optBoolean("placementDone", false)
            placementScore = o.optInt("placementScore", 0)
        }
    }

    private fun save() {
        val o = JSONObject()
        o.put("mastery", JSONObject().apply { for ((k, v) in mastery) put(k, JSONArray().put(v.first.toDouble()).put(v.second)) })
        o.put("days", JSONObject().apply { for ((k, v) in days) put(k, JSONArray().put(v[0]).put(v[1])) })
        o.put("attempts", JSONArray().apply {
            for (a in attempts) put(JSONArray().put(a.day).put(a.time).put(a.text).put(a.score).put(a.source))
        })
        o.put("units", JSONObject().apply { for ((k, v) in unitDone) put(k, v) })
        o.put("myWords", JSONArray().apply { for (w in myWords) put(w) })
        o.put("placementDone", placementDone)
        o.put("placementScore", placementScore)
        val tmp = File(file.parentFile, "progress.tmp")
        tmp.writeText(o.toString())
        tmp.renameTo(file)
        version++
    }
}
