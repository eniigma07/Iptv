package com.mojatv.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Ustawienia, ulubione, historia i postęp oglądania (lokalnie na telefonie). */
class Storage(context: Context) {
    private val prefs = context.getSharedPreferences("mojatv", Context.MODE_PRIVATE)

    // ---- Źródło ----
    var source: SourceConfig?
        get() = prefs.getString("source", null)?.let { SourceConfig.fromJson(it) }
        set(value) {
            prefs.edit().putString("source", value?.toJson()).apply()
        }

    // ---- Ustawienia ----
    var liveFormat: String
        get() = prefs.getString("liveFormat", "ts") ?: "ts"
        set(v) = prefs.edit().putString("liveFormat", v).apply()

    var epgEnabled: Boolean
        get() = prefs.getBoolean("epgEnabled", true)
        set(v) = prefs.edit().putBoolean("epgEnabled", v).apply()

    var hideAdult: Boolean
        get() = prefs.getBoolean("hideAdult", false)
        set(v) = prefs.edit().putBoolean("hideAdult", v).apply()

    var userAgent: String
        get() = prefs.getString("userAgent", Http.DEFAULT_USER_AGENT) ?: Http.DEFAULT_USER_AGENT
        set(v) = prefs.edit().putString("userAgent", v).apply()

    // ---- korekta godzin programu TV ----
    /** "auto" = godziny jak podaje serwer, "pl" = godziny w programie to czas polski. */
    var epgMode: String
        get() = prefs.getString("epgMode", "auto") ?: "auto"
        set(v) = prefs.edit().putString("epgMode", v).apply()

    var epgShiftMinutes: Int
        get() = prefs.getInt("epgShift", 0)
        set(v) = prefs.edit().putInt("epgShift", v).apply()

    var epgShowPolish: Boolean
        get() = prefs.getBoolean("epgShowPolish", false)
        set(v) = prefs.edit().putBoolean("epgShowPolish", v).apply()

    // ---- nagrywanie ----
    var recPadBefore: Int
        get() = prefs.getInt("recPadBefore", 2)
        set(v) = prefs.edit().putInt("recPadBefore", v).apply()

    var recPadAfter: Int
        get() = prefs.getInt("recPadAfter", 10)
        set(v) = prefs.edit().putInt("recPadAfter", v).apply()

    /** Nagrania zmniejszane w trakcie nagrywania (720p, ok. 600 MB/godz.). */
    var recSmall: Boolean
        get() = prefs.getBoolean("recSmall", true)
        set(v) = prefs.edit().putBoolean("recSmall", v).apply()

    /** Ostatnio używany telewizor do wysyłania obrazu (adres opisu DLNA). */
    var castLocation: String?
        get() = prefs.getString("castLocation", null)
        set(v) = prefs.edit().putString("castLocation", v).apply()

    var radioCountry: String
        get() = prefs.getString("radioCountry", "PL") ?: "PL"
        set(v) = prefs.edit().putString("radioCountry", v).apply()

    /** Kategorie wybrane ręcznie do zakładki Show ("movie:ID" / "series:ID"). null = automatycznie. */
    var showCategories: Set<String>?
        get() = if (prefs.contains("showCategories")) prefs.getStringSet("showCategories", emptySet())?.toSet() else null
        set(v) {
            if (v == null) prefs.edit().remove("showCategories").apply()
            else prefs.edit().putStringSet("showCategories", HashSet(v)).apply()
        }

    // ---- Ulubione ----
    private val _favorites = MutableStateFlow(loadItems("favorites"))
    val favorites: StateFlow<List<PlayItem>> = _favorites

    fun isFavorite(key: String) = _favorites.value.any { it.key == key }

    fun toggleFavorite(item: PlayItem) {
        val list = _favorites.value.toMutableList()
        if (list.removeAll { it.key == item.key }.not()) list.add(0, item)
        _favorites.value = list
        saveItems("favorites", list)
    }

    // ---- Ostatnio oglądane kanały ----
    private val _recent = MutableStateFlow(loadItems("recent"))
    val recent: StateFlow<List<PlayItem>> = _recent

    fun addRecent(item: PlayItem) {
        if (!item.isLive) return
        val list = _recent.value.filterNot { it.key == item.key }.toMutableList()
        list.add(0, item)
        val trimmed = list.take(30)
        _recent.value = trimmed
        saveItems("recent", trimmed)
    }

    // ---- Postęp oglądania (filmy, odcinki) ----
    private val _progress = MutableStateFlow(loadProgress())
    val progress: StateFlow<Map<String, WatchProgress>> = _progress

    fun saveProgress(item: PlayItem, position: Long, duration: Long) {
        if (item.isLive || duration <= 0) return
        val map = _progress.value.toMutableMap()
        map[item.key] = WatchProgress(item, position, duration, System.currentTimeMillis())
        // ograniczamy historię do 300 pozycji
        val trimmed = map.values.sortedByDescending { it.updatedAt }.take(300).associateBy { it.item.key }
        _progress.value = trimmed
        persistProgress(trimmed)
    }

    fun removeProgress(key: String) {
        val map = _progress.value.toMutableMap()
        map.remove(key)
        _progress.value = map
        persistProgress(map)
    }

    fun clearHistory() {
        _progress.value = emptyMap()
        _recent.value = emptyList()
        prefs.edit().remove("progress").remove("recent").apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
        _favorites.value = emptyList()
        _recent.value = emptyList()
        _progress.value = emptyMap()
    }

    // ---- Zapis JSON ----
    private fun loadItems(name: String): List<PlayItem> = try {
        val arr = JSONArray(prefs.getString(name, "[]"))
        (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let { PlayItem.fromJson(it) } }
    } catch (e: Exception) {
        emptyList()
    }

    private fun saveItems(name: String, items: List<PlayItem>) {
        val arr = JSONArray()
        items.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(name, arr.toString()).apply()
    }

    private fun loadProgress(): Map<String, WatchProgress> = try {
        val arr = JSONArray(prefs.getString("progress", "[]"))
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val item = o.optJSONObject("item")?.let { PlayItem.fromJson(it) } ?: return@mapNotNull null
            WatchProgress(item, o.optLong("pos"), o.optLong("dur"), o.optLong("at"))
        }.associateBy { it.item.key }
    } catch (e: Exception) {
        emptyMap()
    }

    private fun persistProgress(map: Map<String, WatchProgress>) {
        val arr = JSONArray()
        map.values.forEach {
            arr.put(
                JSONObject()
                    .put("item", it.item.toJson())
                    .put("pos", it.position)
                    .put("dur", it.duration)
                    .put("at", it.updatedAt)
            )
        }
        prefs.edit().putString("progress", arr.toString()).apply()
    }
}
