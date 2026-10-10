package com.mojatv.app.player

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.mojatv.app.data.PlayItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class RadioState(
    val item: PlayItem,
    val isPlaying: Boolean,
    val isBuffering: Boolean,
    val streamTitle: String?,
    val error: String?,
)

/** Łączy ekran Radia z usługą odtwarzania w tle. */
object RadioController {
    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var pending: (() -> Unit)? = null
    private var currentItem: PlayItem? = null

    private val _state = MutableStateFlow<RadioState?>(null)
    val state: StateFlow<RadioState?> = _state

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = refresh()
        override fun onPlaybackStateChanged(playbackState: Int) = refresh()
        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) = refresh()
        override fun onPlayerError(error: PlaybackException) {
            val item = currentItem ?: return
            _state.value = RadioState(item, false, false, null, "Nie można odtworzyć tej stacji")
        }
    }

    private fun connect(context: Context, then: () -> Unit) {
        controller?.let { then(); return }
        pending = then
        if (future != null) return
        val app = context.applicationContext
        val token = SessionToken(app, ComponentName(app, RadioService::class.java))
        val f = MediaController.Builder(app, token).buildAsync()
        future = f
        f.addListener({
            try {
                val c = f.get()
                c.addListener(listener)
                controller = c
                pending?.invoke()
                pending = null
            } catch (e: Exception) {
                future = null
            }
        }, ContextCompat.getMainExecutor(app))
    }

    fun play(context: Context, item: PlayItem) {
        currentItem = item
        _state.value = RadioState(item, false, true, null, null)
        connect(context) {
            controller?.let { c ->
                c.setMediaItem(PlayerFactory.mediaItem(item))
                c.prepare()
                c.play()
            }
        }
    }

    fun togglePause() {
        val c = controller ?: return
        if (c.isPlaying) {
            c.pause()
        } else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    fun stop() {
        controller?.let { c ->
            c.stop()
            c.clearMediaItems()
        }
        currentItem = null
        _state.value = null
    }

    private fun refresh() {
        val c = controller ?: return
        val item = currentItem ?: return
        val title = c.mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() && it != item.title }
        _state.value = RadioState(
            item = item,
            isPlaying = c.isPlaying,
            isBuffering = c.playbackState == Player.STATE_BUFFERING,
            streamTitle = title,
            error = null,
        )
    }
}
