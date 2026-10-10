package com.mojatv.app.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.mojatv.app.data.PlayItem

/** Kolejka przekazywana do odtwarzacza (lista kanałów do przełączania, odcinki sezonu). */
object PlaybackQueue {
    var items: List<PlayItem> = emptyList()
    var index: Int = 0
    /** -1 = wznów od zapisanego miejsca, 0 = od początku. */
    var startPosition: Long = -1
}

fun Context.playVideo(items: List<PlayItem>, index: Int, startPosition: Long = -1) {
    if (items.isEmpty()) return
    RadioController.stop()
    PlaybackQueue.items = items
    PlaybackQueue.index = index.coerceIn(0, items.lastIndex)
    PlaybackQueue.startPosition = startPosition
    startActivity(
        Intent(this, PlayerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

fun Context.playVideo(item: PlayItem, startPosition: Long = -1) = playVideo(listOf(item), 0, startPosition)

/** Otwiera strumień w zewnętrznym odtwarzaczu (np. VLC, MX Player). */
fun Context.openExternal(item: PlayItem) {
    val type = if (item.kind == com.mojatv.app.data.ContentKind.RADIO) "audio/*" else "video/*"
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(Uri.parse(item.url), type)
        .putExtra("title", item.title)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(Intent.createChooser(intent, "Otwórz w…").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(this, "Brak zewnętrznego odtwarzacza (zainstaluj np. VLC)", Toast.LENGTH_LONG).show()
    }
}
