package com.mojatv.app.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.mojatv.app.ui.MainActivity

/** Odtwarzanie radia w tle z powiadomieniem i sterowaniem z ekranu blokady. */
@androidx.annotation.OptIn(UnstableApi::class)
class RadioService : MediaSessionService() {

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = PlayerFactory.create(this, audioOnly = true)
        // RDS: wykonawca i tytuł utworu przysyłane przez stację
        player.addListener(object : androidx.media3.common.Player.Listener {
            override fun onMetadata(metadata: androidx.media3.common.Metadata) {
                radioTextFrom(metadata)?.let { RadioController.onRadioText(it) }
            }
        })
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setCallback(Callback())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.let {
            it.player.release()
            it.release()
        }
        session = null
        super.onDestroy()
    }

    /** Odtwarza adres przekazany przez aplikację (adres jest w requestMetadata). */
    private class Callback : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            val resolved = mediaItems.map { item ->
                val uri = item.localConfiguration?.uri ?: item.requestMetadata.mediaUri
                if (uri == null) {
                    item
                } else {
                    item.buildUpon()
                        .setUri(uri)
                        .apply { if (PlayerFactory.isHls(uri.toString())) setMimeType(MimeTypes.APPLICATION_M3U8) }
                        .build()
                }
            }.toMutableList()
            return Futures.immediateFuture(resolved)
        }
    }
}
