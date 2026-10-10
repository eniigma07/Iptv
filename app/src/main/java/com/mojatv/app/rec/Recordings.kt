package com.mojatv.app.rec

import android.content.Context
import android.os.Environment
import com.mojatv.app.data.PlayItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class RecStatus { SCHEDULED, RECORDING, DONE, FAILED, CANCELLED }

data class Recording(
    val id: String,
    val channel: PlayItem,
    val title: String,
    /** Prawdziwy moment startu i końca (z zapasem), w milisekundach. */
    val start: Long,
    val end: Long,
    val status: RecStatus,
    val filePath: String? = null,
    val bytes: Long = 0,
    val error: String? = null,
    /** Po zakończeniu: plik w folderze Filmy/MojaTV (content://…), widoczny w Galerii i Moich plikach. */
    val contentUri: String? = null,
) {
    /** Adres do odtwarzania: Filmy/MojaTV albo plik w folderze aplikacji. */
    val playUri: String?
        get() = contentUri ?: filePath?.let { android.net.Uri.fromFile(File(it)).toString() }

    val active: Boolean get() = status == RecStatus.SCHEDULED || status == RecStatus.RECORDING

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("channel", channel.toJson())
        .put("title", title)
        .put("start", start)
        .put("end", end)
        .put("status", status.name)
        .put("filePath", filePath ?: JSONObject.NULL)
        .put("bytes", bytes)
        .put("error", error ?: JSONObject.NULL)
        .put("contentUri", contentUri ?: JSONObject.NULL)

    companion object {
        fun fromJson(o: JSONObject): Recording? = try {
            Recording(
                id = o.getString("id"),
                channel = PlayItem.fromJson(o.getJSONObject("channel")) ?: throw IllegalStateException("brak kanału"),
                title = o.getString("title"),
                start = o.getLong("start"),
                end = o.getLong("end"),
                status = RecStatus.valueOf(o.getString("status")),
                filePath = o.optString("filePath").takeIf { it.isNotBlank() && it != "null" },
                bytes = o.optLong("bytes"),
                error = o.optString("error").takeIf { it.isNotBlank() && it != "null" },
                contentUri = o.optString("contentUri").takeIf { it.isNotBlank() && it != "null" },
            )
        } catch (e: Exception) {
            null
        }
    }
}

/** Lista nagrań zapisana w telefonie. */
class RecordingStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("mojatv_rec", Context.MODE_PRIVATE)
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<Recording>> = _items

    fun get(id: String): Recording? = _items.value.firstOrNull { it.id == id }

    @Synchronized
    fun add(r: Recording) {
        save(_items.value + r)
    }

    @Synchronized
    fun update(id: String, change: (Recording) -> Recording) {
        save(_items.value.map { if (it.id == id) change(it) else it })
    }

    @Synchronized
    fun remove(id: String) {
        save(_items.value.filterNot { it.id == id })
    }

    fun newId(): String = UUID.randomUUID().toString().take(12)

    /** Folder na nagrania (pamięć telefonu, katalog aplikacji — bez dodatkowych uprawnień). */
    fun folder(): File {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
        return File(base, "Nagrania").apply { mkdirs() }
    }

    fun fileFor(r: Recording): File {
        val stamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date(r.start))
        val safe = (r.channel.title + " - " + r.title).replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80)
        val ext = if (r.channel.url.substringBefore('?').lowercase().endsWith(".m3u8")) "ts" else "ts"
        return File(folder(), "${stamp}_$safe.$ext")
    }

    /** Wolne miejsce w folderze nagrań (bajty). */
    fun freeSpace(): Long = folder().usableSpace

    private fun load(): List<Recording> = try {
        val arr = JSONArray(prefs.getString("items", "[]"))
        (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let { o -> Recording.fromJson(o) } }
    } catch (e: Exception) {
        emptyList()
    }

    private fun save(list: List<Recording>) {
        val sorted = list.sortedBy { it.start }
        _items.value = sorted
        val arr = JSONArray()
        sorted.forEach { arr.put(it.toJson()) }
        prefs.edit().putString("items", arr.toString()).apply()
    }
}
