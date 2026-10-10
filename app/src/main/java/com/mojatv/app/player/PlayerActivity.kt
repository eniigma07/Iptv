package com.mojatv.app.player

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Rational
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.mojatv.app.MojaTvApp
import com.mojatv.app.data.EpgProgram
import com.mojatv.app.data.PlayItem
import com.mojatv.app.ui.theme.MojaTvTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@androidx.annotation.OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {

    private val repo get() = MojaTvApp.repo
    private lateinit var player: ExoPlayer

    private var items: List<PlayItem> = emptyList()
    private var index by mutableIntStateOf(0)
    private var error by mutableStateOf<String?>(null)
    private var controlsVisible by mutableStateOf(true)
    private var inPip by mutableStateOf(false)
    private var programs by mutableStateOf<List<EpgProgram>>(emptyList())
    private var resizeMode by mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT)

    private var triedHls = false
    private var liveRetries = 0
    private var epgJob: Job? = null
    private var retryJob: Job? = null

    private val current: PlayItem? get() = items.getOrNull(index)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        player = PlayerFactory.create(this)
        player.addListener(listener)

        setContent { MojaTvTheme { PlayerScreen() } }

        startFromQueue()
        startProgressSaver()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        saveProgress()
        startFromQueue()
    }

    private fun startFromQueue() {
        items = PlaybackQueue.items
        if (items.isEmpty()) {
            finish()
            return
        }
        play(PlaybackQueue.index, PlaybackQueue.startPosition)
    }

    private fun play(i: Int, startAt: Long = -1) {
        val item = items.getOrNull(i) ?: return
        index = i
        error = null
        triedHls = false
        liveRetries = 0
        retryJob?.cancel()
        repo.storage.addRecent(item)

        val start = when {
            item.isLive -> 0L
            startAt >= 0 -> startAt
            else -> repo.storage.progress.value[item.key]?.takeIf { !it.finished }?.position ?: 0L
        }
        player.setMediaItem(PlayerFactory.mediaItem(item), start)
        player.prepare()
        player.playWhenReady = true
        loadPrograms(item)
    }

    private fun loadPrograms(item: PlayItem) {
        epgJob?.cancel()
        programs = emptyList()
        if (!item.isLive) return
        epgJob = lifecycleScope.launch {
            programs = repo.programsFor(item)
        }
    }

    private val listener = object : Player.Listener {
        override fun onPlayerError(e: PlaybackException) {
            val item = current ?: return
            val parsingError = e.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
                e.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ||
                e.errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED

            when {
                // Strumień bez rozszerzenia .m3u8, który okazuje się być HLS
                parsingError && !triedHls && !PlayerFactory.isHls(item.url) -> {
                    triedHls = true
                    player.setMediaItem(PlayerFactory.mediaItem(item, forceHls = true))
                    player.prepare()
                }
                e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> {
                    player.seekToDefaultPosition()
                    player.prepare()
                }
                // Na żywo: kilka prób ponownego połączenia
                item.isLive && liveRetries < 3 -> {
                    liveRetries++
                    retryJob = lifecycleScope.launch {
                        delay(1500L * liveRetries)
                        player.prepare()
                        player.playWhenReady = true
                    }
                }
                else -> error = describe(e)
            }
        }

        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_READY) liveRetries = 0
            if (state == Player.STATE_ENDED) {
                val item = current ?: return
                if (!item.isLive) {
                    val d = player.duration
                    if (d > 0) repo.storage.saveProgress(item, d, d)
                    if (index < items.lastIndex) play(index + 1, 0L)
                }
            }
        }
    }

    private fun describe(e: PlaybackException): String = when (e.errorCode) {
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
            "Serwer odrzucił połączenie (np. limit połączeń, wygasłe konto albo kanał niedostępny)."
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
            "Brak połączenia z serwerem."
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES ->
            "Telefon nie obsługuje formatu tego materiału. Spróbuj zewnętrznego odtwarzacza (VLC)."
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ->
            "Nieobsługiwany format strumienia. Spróbuj zmienić format Live w Ustawieniach albo użyj VLC."
        else -> "Nie udało się odtworzyć (${e.errorCodeName})."
    }

    private fun saveProgress() {
        val item = current ?: return
        if (item.isLive) return
        val d = player.duration
        if (d > 0) repo.storage.saveProgress(item, player.currentPosition, d)
    }

    private fun startProgressSaver() {
        lifecycleScope.launch {
            while (isActive) {
                delay(10_000)
                if (player.isPlaying) saveProgress()
            }
        }
    }

    // ---------------- Obraz w obrazie ----------------

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (player.isPlaying) enterPip()
    }

    private fun enterPip() {
        try {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
            )
        } catch (e: Exception) {
            // urządzenie bez PiP
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
    }

    override fun onStop() {
        super.onStop()
        saveProgress()
        player.pause()
    }

    override fun onStart() {
        super.onStart()
        hideSystemBars()
    }

    override fun onDestroy() {
        saveProgress()
        player.removeListener(listener)
        player.release()
        super.onDestroy()
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    // ---------------- Interfejs ----------------

    @Composable
    private fun PlayerScreen() {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = this@PlayerActivity.player
                        keepScreenOn = true
                        setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                        setShowNextButton(false)
                        setShowPreviousButton(false)
                        controllerShowTimeoutMs = 4000
                        setControllerVisibilityListener(
                            PlayerView.ControllerVisibilityListener { v -> controlsVisible = v == View.VISIBLE }
                        )
                    }
                },
                update = { view ->
                    view.useController = !inPip
                    view.resizeMode = resizeMode
                },
            )

            val item = current
            if (item != null && controlsVisible && !inPip) {
                TopBar(item)
                if (items.size > 1) {
                    ZapButton(Icons.Filled.SkipPrevious, Modifier.align(Alignment.CenterStart), index > 0) {
                        saveProgress(); play(index - 1, if (item.isLive) -1L else 0L)
                    }
                    ZapButton(Icons.Filled.SkipNext, Modifier.align(Alignment.CenterEnd), index < items.lastIndex) {
                        saveProgress(); play(index + 1, if (item.isLive) -1L else 0L)
                    }
                }
            }

            val err = error
            if (err != null && item != null && !inPip) {
                Column(
                    Modifier.align(Alignment.Center).background(Color(0xCC000000)).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(err, color = Color.White, fontSize = 15.sp)
                    Spacer(Modifier.size(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { play(index) }) { Text("Spróbuj ponownie") }
                        OutlinedButton(onClick = { openExternal(item) }) { Text("Otwórz w VLC") }
                    }
                }
            }
        }
    }

    @Composable
    private fun TopBar(item: PlayItem) {
        val now = System.currentTimeMillis()
        val nowProgram = programs.firstOrNull { it.start <= now && it.end > now }
        val nextProgram = programs.firstOrNull { it.start > now }
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())

        Row(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            IconButton(onClick = { finish() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wstecz", tint = Color.White)
            }
            Column(Modifier.weight(1f).padding(top = 6.dp)) {
                Text(
                    item.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                item.subtitle?.takeIf { !item.isLive }?.let {
                    Text(it, color = Color(0xFFCCCCCC), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (nowProgram != null) {
                    Text(
                        "${fmt.format(Date(nowProgram.start))} ${nowProgram.title}",
                        color = Color(0xFFDDDDDD), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    LinearProgressIndicator(
                        progress = { nowProgram.progress(now) },
                        modifier = Modifier.width(200.dp).padding(vertical = 4.dp),
                    )
                }
                if (nextProgram != null) {
                    Text(
                        "Następnie ${fmt.format(Date(nextProgram.start))} ${nextProgram.title}",
                        color = Color(0xFFAAAAAA), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = {
                resizeMode = when (resizeMode) {
                    AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            }) {
                Icon(Icons.Filled.AspectRatio, "Proporcje obrazu", tint = Color.White)
            }
            IconButton(onClick = { openExternal(item) }) {
                Icon(Icons.Filled.OpenInNew, "Zewnętrzny odtwarzacz", tint = Color.White)
            }
        }
    }

    @Composable
    private fun ZapButton(
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        modifier: Modifier,
        enabled: Boolean,
        onClick: () -> Unit,
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.padding(16.dp).size(56.dp).background(Color(0x66000000), CircleShape),
        ) {
            Icon(icon, null, tint = if (enabled) Color.White else Color.Gray, modifier = Modifier.size(36.dp))
        }
    }
}
