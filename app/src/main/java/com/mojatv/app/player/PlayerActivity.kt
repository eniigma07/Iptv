package com.mojatv.app.player

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Rational
import android.view.KeyEvent
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
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
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
import com.mojatv.app.cast.CastPosition
import com.mojatv.app.cast.Dlna
import com.mojatv.app.cast.Renderer
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

    // ---- wysyłanie na telewizor (DLNA) ----
    private var castTarget by mutableStateOf<Renderer?>(null)
    private var castDialog by mutableStateOf(false)
    private var recordDialog by mutableStateOf(false)
    private var castBusy by mutableStateOf(false)
    private var castError by mutableStateOf<String?>(null)
    private var castPos by mutableStateOf<CastPosition?>(null)
    private var castJob: Job? = null
    private var castPollJob: Job? = null

    private val current: PlayItem? get() = items.getOrNull(index)

    // ---- Fire TV / Android TV: sterowanie pilotem ----
    private val isTv by lazy { com.mojatv.app.ui.Device.isTv(this) }
    private var playerView: PlayerView? = null
    private var tvMenu by mutableStateOf(false)
    private var tvUi by mutableStateOf(TvUi.HIDDEN)
    private var tvUiStamp by mutableIntStateOf(0)

    // ---- diagnostyka obrazu (Menu → Informacje o obrazie) ----
    private var showStats by mutableStateOf(false)
    private var videoDecoder by mutableStateOf<String?>(null)
    private var droppedFrames by mutableIntStateOf(0)
    private var statsTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        val st = repo.storage
        player = PlayerFactory.create(
            this,
            tunneling = isTv && st.tvTunneling,
            tsCompat = st.tsCompat,
        )
        player.addListener(listener)
        player.addAnalyticsListener(object : androidx.media3.exoplayer.analytics.AnalyticsListener {
            override fun onVideoDecoderInitialized(
                eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                decoderName: String,
                initializedTimestampMs: Long,
                initializationDurationMs: Long,
            ) {
                videoDecoder = decoderName
            }

            override fun onDroppedVideoFrames(
                eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                droppedFrames: Int,
                elapsedMs: Long,
            ) {
                this@PlayerActivity.droppedFrames += droppedFrames
            }
        })

        setContent { MojaTvTheme { com.mojatv.app.ui.TvFocusProvider(isTv) { PlayerScreen() } } }

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
        val first = items.getOrNull(PlaybackQueue.index)
        if (isTv && repo.storage.tvUseVlc && first != null && !first.key.startsWith("rec:")) {
            // ustawienie: na telewizorze odtwarzaj w VLC
            repo.storage.addRecent(first)
            openInVlc(first)
            finish()
            return
        }
        play(PlaybackQueue.index, PlaybackQueue.startPosition)
    }

    private fun play(i: Int, startAt: Long = -1) {
        val item = items.getOrNull(i) ?: return
        index = i
        droppedFrames = 0
        videoDecoder = null
        showTvInfo()
        error = null
        triedHls = false
        liveRetries = 0
        retryJob?.cancel()
        if (!item.key.startsWith("rec:")) repo.storage.addRecent(item)

        val start = when {
            item.isLive -> 0L
            startAt >= 0 -> startAt
            else -> repo.storage.progress.value[item.key]?.takeIf { !it.finished }?.position ?: 0L
        }
        val tv = castTarget
        if (tv != null) {
            player.stop()
            castItem(tv, item, start)
            loadPrograms(item)
            return
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
                parsingError && !triedHls && !PlayerFactory.isHls(item.url) && !PlayerFactory.isLocal(item.url) -> {
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

    // ---------------- Telewizor ----------------

    private fun startCasting(tv: Renderer) {
        castDialog = false
        val item = current ?: return
        val pos = if (item.isLive) 0L else player.currentPosition
        saveProgress()
        // zamykamy strumień w telefonie — konta IPTV często pozwalają tylko na jedno połączenie
        player.stop()
        castTarget = tv
        repo.storage.castLocation = tv.location
        castItem(tv, item, pos)
        startCastPolling()
    }

    private fun castItem(tv: Renderer, item: PlayItem, startMs: Long) {
        castJob?.cancel()
        castError = null
        castPos = null
        castBusy = true
        castJob = lifecycleScope.launch {
            try {
                Dlna.play(tv, item, startMs)
            } catch (e: Exception) {
                castError = (e.message ?: "Nie udało się wysłać na telewizor.") +
                    "\nSprawdź, czy telewizor jest włączony i w tej samej sieci Wi‑Fi."
            } finally {
                castBusy = false
            }
        }
    }

    private fun startCastPolling() {
        castPollJob?.cancel()
        castPollJob = lifecycleScope.launch {
            var wasPlaying = false
            while (isActive) {
                delay(2000)
                val tv = castTarget ?: break
                if (castBusy) continue
                val p = runCatching { Dlna.position(tv) }.getOrNull() ?: continue
                castPos = p
                val item = current ?: continue
                if (!item.isLive && p.durationMs > 0) {
                    repo.storage.saveProgress(item, p.positionMs, p.durationMs)
                }
                if (p.state == "PLAYING") wasPlaying = true
                // koniec odcinka/filmu na telewizorze → następny w kolejce
                if (wasPlaying && p.state == "STOPPED" && !item.isLive) {
                    wasPlaying = false
                    if (index < items.lastIndex) play(index + 1, 0L)
                }
            }
        }
    }

    /** Kończy wysyłanie; resumeHere = oglądaj dalej na telefonie od tego samego miejsca. */
    private fun stopCasting(resumeHere: Boolean) {
        val tv = castTarget ?: return
        val pos = castPos?.positionMs ?: 0L
        castTarget = null
        castPollJob?.cancel()
        castJob?.cancel()
        lifecycleScope.launch { runCatching { Dlna.stop(tv) } }
        if (resumeHere) play(index, if (current?.isLive == true) -1L else pos)
        else finish()
    }

    private fun castToggle() {
        val tv = castTarget ?: return
        lifecycleScope.launch {
            val playing = castPos?.state == "PLAYING"
            runCatching { if (playing) Dlna.pause(tv) else Dlna.resume(tv) }
                .onFailure { castError = it.message }
            castPos = runCatching { Dlna.position(tv) }.getOrNull() ?: castPos
        }
    }

    private fun castSeekBy(deltaMs: Long) {
        val tv = castTarget ?: return
        val p = castPos ?: return
        val target = (p.positionMs + deltaMs).coerceIn(0, if (p.durationMs > 0) p.durationMs - 3000 else Long.MAX_VALUE)
        castPos = p.copy(positionMs = target)
        lifecycleScope.launch { runCatching { Dlna.seek(tv, target) }.onFailure { castError = it.message } }
    }

    private fun saveProgress() {
        val item = current ?: return
        if (item.isLive) return
        if (castTarget != null) return // postęp zapisuje odpytywanie telewizora
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
        // na telewizorze przycisk Home ma zamknąć obraz, a nie zostawić okienko
        if (player.isPlaying && !isTv) enterPip()
    }

    // ---------------- Pilot (Fire TV) ----------------

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (isTv && handleTvKey(event)) return true
        return super.dispatchKeyEvent(event)
    }

    /**
     * Pilot na Fire TV (bez przycisków odtwarzacza na ekranie):
     *  - OK = pasek z przyciskami (Wstecz, pauza, kanały, opcje…), Wstecz = schowaj pasek / zamknij,
     *  - kanały na żywo: góra/dół = poprzedni/następny kanał (także CH+/CH−),
     *  - filmy i seriale: lewo/prawo = przewijanie −10 s / +30 s,
     *  - Menu (≡) = opcje, play/pauza na pilocie działa zawsze.
     */
    private fun handleTvKey(e: KeyEvent): Boolean {
        if (castDialog || recordDialog || tvMenu || castTarget != null) return false
        val item = current ?: return false
        val down = e.action == KeyEvent.ACTION_DOWN
        val first = down && e.repeatCount == 0
        when (e.keyCode) {
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_INFO -> {
                if (first) tvMenu = true
                return true
            }
            KeyEvent.KEYCODE_GUIDE -> {
                if (first) {
                    if (item.kind == com.mojatv.app.data.ContentKind.LIVE) recordDialog = true else tvMenu = true
                }
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                if (first) { togglePlay(); showTvInfo() }
                return true
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                if (down && !item.isLive) { seekBy(30_000); showTvInfo() }
                return true
            }
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                if (down && !item.isLive) { seekBy(-10_000); showTvInfo() }
                return true
            }
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP -> {
                if (down) zap(+1)
                return true
            }
            KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> {
                if (down) zap(-1)
                return true
            }
        }
        // komunikat błędu ma własne przyciski — pilot chodzi po nich
        if (error != null) return false
        if (tvUi == TvUi.FULL) {
            if (e.keyCode == KeyEvent.KEYCODE_BACK) {
                if (first) tvUi = TvUi.HIDDEN
                return true
            }
            if (down) tvUiStamp++   // pasek zostaje, dopóki coś klikasz
            return false             // strzałki i OK chodzą po przyciskach paska
        }
        when (e.keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (first) showTvBar()
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (down) { if (item.isLive && items.size > 1) zap(-1) else showTvBar() }
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (down) { if (item.isLive && items.size > 1) zap(+1) else showTvBar() }
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (down) { if (!item.isLive) { seekBy(-10_000); showTvInfo() } else showTvBar() }
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (down) { if (!item.isLive) { seekBy(30_000); showTvInfo() } else showTvBar() }
                return true
            }
        }
        return false
    }

    private enum class TvUi { HIDDEN, INFO, FULL }

    /** Opis kanału na chwilę (po zmianie kanału, przewinięciu) — sam znika. */
    private fun showTvInfo() {
        if (!isTv) return
        if (tvUi != TvUi.FULL) tvUi = TvUi.INFO
        tvUiStamp++
    }

    /** Pasek z przyciskami do sterowania pilotem. */
    private fun showTvBar() {
        tvUi = TvUi.FULL
        tvUiStamp++
    }

    private fun togglePlay() {
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            player.play()
        }
    }

    private fun zap(delta: Int) {
        val item = current ?: return
        val n = index + delta
        if (n !in items.indices) return
        saveProgress()
        play(n, if (item.isLive) -1L else 0L)
    }

    private fun seekBy(deltaMs: Long) {
        val dur = player.duration
        var target = player.currentPosition + deltaMs
        if (dur > 0) target = target.coerceAtMost(dur - 1000)
        player.seekTo(target.coerceAtLeast(0))
    }

    private fun enterPip() {
        if (android.os.Build.VERSION.SDK_INT < 26) return
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
                            PlayerView.ControllerVisibilityListener { v ->
                                controlsVisible = v == View.VISIBLE
                                if (isTv && v == View.VISIBLE) {
                                    // pilot od razu na przycisku play/pauza
                                    post { findViewById<View>(androidx.media3.ui.R.id.exo_play_pause)?.requestFocus() }
                                }
                            }
                        )
                        if (isTv) controllerShowTimeoutMs = 5000
                        playerView = this
                    }
                },
                update = { view ->
                    // na telewizorze własny pasek sterowany pilotem zamiast przycisków dotykowych
                    view.useController = !isTv && !inPip && castTarget == null
                    view.resizeMode = resizeMode
                },
            )

            val item = current
            if (item != null && castTarget != null) {
                CastOverlay(item, castTarget!!)
            } else if (isTv) {
                if (item != null && !inPip && tvUi != TvUi.HIDDEN) TvOverlay(item, full = tvUi == TvUi.FULL)
            } else if (item != null && controlsVisible && !inPip) {
                TopBar(item)
                if (items.size > 1 && !isTv) {
                    ZapButton(Icons.Filled.SkipPrevious, Modifier.align(Alignment.CenterStart), index > 0) {
                        saveProgress(); play(index - 1, if (item.isLive) -1L else 0L)
                    }
                    ZapButton(Icons.Filled.SkipNext, Modifier.align(Alignment.CenterEnd), index < items.lastIndex) {
                        saveProgress(); play(index + 1, if (item.isLive) -1L else 0L)
                    }
                }
            }

            if (castDialog) CastDialog()
            if (tvMenu && item != null) TvMenu(item)
            if (showStats && !inPip) StatsOverlay(Modifier.align(Alignment.BottomStart))
            if (recordDialog && item != null) {
                com.mojatv.app.ui.ChannelGuide(item, onDismiss = { recordDialog = false }, onStartedNow = {
                    // nagrywanie od razu tego, co się ogląda = drugie połączenie z serwerem
                    val max = repo.account.value?.maxConnections?.trim()?.toIntOrNull()
                    if (max == 1 && castTarget == null) {
                        android.widget.Toast.makeText(
                            this@PlayerActivity,
                            "Nagrywanie trwa w tle. Twoje konto pozwala na 1 połączenie, więc zamykam odtwarzacz.",
                            android.widget.Toast.LENGTH_LONG,
                        ).show()
                        player.stop()
                        finish()
                    } else if (max == null) {
                        android.widget.Toast.makeText(
                            this@PlayerActivity,
                            "Nagrywanie trwa w tle. Jeśli obraz albo nagranie się przerwie, zamknij odtwarzacz — konto może mieć limit połączeń.",
                            android.widget.Toast.LENGTH_LONG,
                        ).show()
                    }
                })
            }

            val err = error
            if (err != null && item != null && !inPip && castTarget == null) {
                Column(
                    Modifier.align(Alignment.Center).background(Color(0xCC000000)).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(err, color = Color.White, fontSize = 15.sp)
                    Spacer(Modifier.size(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val retryFocus = remember { androidx.compose.ui.focus.FocusRequester() }
                        if (isTv) LaunchedEffect(err) { delay(80); runCatching { retryFocus.requestFocus() } }
                        Button(onClick = { play(index) }, modifier = Modifier.focusRequester(retryFocus)) { Text("Spróbuj ponownie") }
                        OutlinedButton(onClick = { openExternal(item) }) { Text("Otwórz w VLC") }
                    }
                }
            }
        }
    }

    // ---------------- Pasek na telewizorze ----------------

    @Composable
    private fun TvOverlay(item: PlayItem, full: Boolean) {
        // samo znika: opis po 4,5 s, pasek 8 s po ostatnim naciśnięciu pilota
        LaunchedEffect(full, tvUiStamp) {
            delay(if (full) 8_000 else 4_500)
            tvUi = TvUi.HIDDEN
        }
        var tick by remember { mutableIntStateOf(0) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(500)
                tick++
            }
        }
        @Suppress("UNUSED_VARIABLE") val t = tick
        val playFocus = remember { androidx.compose.ui.focus.FocusRequester() }
        LaunchedEffect(full) {
            if (full) {
                delay(80)
                runCatching { playFocus.requestFocus() }
            }
        }

        val now = System.currentTimeMillis()
        val nowProgram = programs.firstOrNull { it.start <= now && it.end > now }
        val nextProgram = programs.firstOrNull { it.start > now }
        val fmt = com.mojatv.app.data.EpgTime

        Box(Modifier.fillMaxSize()) {
            // ---- opis: kanał / film i program ----
            Column(
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xE6000000), Color.Transparent)))
                    .padding(horizontal = 48.dp, vertical = 28.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.isLive && items.size > 1) {
                        Text("${index + 1}", color = Color(0xFFE5484D), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(14.dp))
                    }
                    Text(
                        item.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 24.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                item.subtitle?.takeIf { !item.isLive }?.let {
                    Text(it, color = Color(0xFFCCCCCC), fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (nowProgram != null) {
                    Text(
                        "${fmt.hm(nowProgram.start)}–${fmt.hm(nowProgram.end)}  ${nowProgram.title}",
                        color = Color(0xFFEEEEEE), fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    LinearProgressIndicator(
                        progress = { nowProgram.progress(now) },
                        modifier = Modifier.width(320.dp).padding(vertical = 6.dp),
                    )
                }
                if (nextProgram != null) {
                    Text(
                        "Następnie ${fmt.hm(nextProgram.start)}  ${nextProgram.title}",
                        color = Color(0xFFAAAAAA), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // ---- dół: czas filmu i przyciski ----
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
                    .padding(horizontal = 48.dp, vertical = 24.dp),
            ) {
                if (!item.isLive) {
                    val pos = player.currentPosition.coerceAtLeast(0)
                    val dur = player.duration.takeIf { it > 0 } ?: 0L
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(clock(pos), color = Color.White, fontSize = 15.sp)
                        LinearProgressIndicator(
                            progress = { if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f },
                            modifier = Modifier.weight(1f).padding(horizontal = 14.dp),
                        )
                        Text(if (dur > 0) clock(dur) else "--:--", color = Color.White, fontSize = 15.sp)
                    }
                    Spacer(Modifier.size(12.dp))
                }
                if (full) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        TvBarButton(Icons.AutoMirrored.Filled.ArrowBack, "Wstecz") { finish() }
                        if (item.isLive) {
                            if (items.size > 1) TvBarButton(Icons.Filled.SkipPrevious, "Poprz. kanał") { zap(-1) }
                        } else {
                            TvBarButton(androidx.compose.material.icons.Icons.Filled.Replay10, "−10 s") { seekBy(-10_000) }
                        }
                        TvBarButton(
                            if (player.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            if (player.isPlaying) "Pauza" else "Odtwórz",
                            Modifier.focusRequester(playFocus),
                        ) { togglePlay() }
                        if (item.isLive) {
                            if (items.size > 1) TvBarButton(Icons.Filled.SkipNext, "Nast. kanał") { zap(+1) }
                        } else {
                            TvBarButton(Icons.Filled.Forward30, "+30 s") { seekBy(30_000) }
                            if (items.size > 1 && index < items.lastIndex) {
                                TvBarButton(Icons.Filled.SkipNext, "Następny") { zap(+1) }
                            }
                        }
                        if (item.kind == com.mojatv.app.data.ContentKind.LIVE) {
                            TvBarButton(Icons.Filled.FiberManualRecord, "Program TV") { recordDialog = true }
                        }
                        TvBarButton(Icons.Filled.AspectRatio, "Obraz") {
                            resizeMode = when (resizeMode) {
                                AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                            }
                        }
                        TvBarButton(androidx.compose.material.icons.Icons.Filled.MoreVert, "Opcje") { tvMenu = true }
                    }
                } else {
                    Text(
                        if (item.isLive) "OK — przyciski · ▲▼ zmiana kanału · ≡ opcje"
                        else "OK — przyciski · ◀▶ przewijanie · ≡ opcje",
                        color = Color(0xFFB0B6C0), fontSize = 14.sp,
                    )
                }
            }
        }
    }

    @Composable
    private fun TvBarButton(
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        label: String,
        modifier: Modifier = Modifier,
        onClick: () -> Unit,
    ) {
        Column(
            modifier
                .width(104.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                .background(Color(0x33FFFFFF))
                .clickable { tvUiStamp++; onClick() }
                .padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(30.dp))
            Text(label, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }

    /** Kodek, rozdzielczość, dekoder i zgubione klatki — pomaga znaleźć przyczynę zacinania obrazu. */
    @Composable
    private fun StatsOverlay(modifier: Modifier) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(1000)
                statsTick++
            }
        }
        @Suppress("UNUSED_VARIABLE") val tick = statsTick
        val v = player.videoFormat
        val a = player.audioFormat
        val lines = buildList {
            add("Wideo: " + (v?.let { "${it.sampleMimeType ?: "?"} ${it.codecs ?: ""}".trim() } ?: "brak"))
            if (v != null) add("Obraz: ${v.width}×${v.height}" + (if (v.frameRate > 0) " · ${"%.2f".format(v.frameRate)} kl/s" else "") +
                (if (v.bitrate > 0) " · ${v.bitrate / 1000} kb/s" else ""))
            add("Dekoder: " + (videoDecoder ?: "—"))
            add("Zgubione klatki: $droppedFrames")
            add("Audio: " + (a?.let { "${it.sampleMimeType ?: "?"} ${it.channelCount} kan. ${it.sampleRate} Hz" } ?: "brak"))
            add("Bufor: ${(player.totalBufferedDuration / 1000)} s")
            add("Urządzenie: ${android.os.Build.MODEL} · Android ${android.os.Build.VERSION.RELEASE}")
        }
        Column(modifier.padding(16.dp).background(Color(0xCC000000)).padding(12.dp)) {
            lines.forEach { Text(it, color = Color.White, fontSize = 13.sp) }
        }
    }

    /** Menu opcji na telewizorze (przycisk ≡ na pilocie). */
    @Composable
    private fun TvMenu(item: PlayItem) {
        val ratioName = when (resizeMode) {
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "powiększony"
            AspectRatioFrameLayout.RESIZE_MODE_FILL -> "rozciągnięty"
            else -> "dopasowany"
        }
        AlertDialog(
            onDismissRequest = { tvMenu = false },
            title = { Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    TextButton(onClick = {
                        resizeMode = when (resizeMode) {
                            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Obraz: $ratioName (zmień)") }
                    if (item.kind == com.mojatv.app.data.ContentKind.LIVE) {
                        TextButton(onClick = { tvMenu = false; recordDialog = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Program TV i nagrywanie")
                        }
                    }
                    if (items.size > 1) {
                        TextButton(onClick = { tvMenu = false; zap(+1) }, enabled = index < items.lastIndex, modifier = Modifier.fillMaxWidth()) {
                            Text(if (item.isLive) "Następny kanał" else "Następny")
                        }
                        TextButton(onClick = { tvMenu = false; zap(-1) }, enabled = index > 0, modifier = Modifier.fillMaxWidth()) {
                            Text(if (item.isLive) "Poprzedni kanał" else "Poprzedni")
                        }
                    }
                    TextButton(onClick = { tvMenu = false; openExternal(item) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Otwórz w zewnętrznym odtwarzaczu (VLC)")
                    }
                    TextButton(onClick = { tvMenu = false; showStats = !showStats }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (showStats) "Ukryj informacje o obrazie" else "Informacje o obrazie (diagnostyka)")
                    }
                    TextButton(onClick = { tvMenu = false; finish() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Zamknij odtwarzacz")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { tvMenu = false }) { Text("Wróć") } },
        )
    }

    @Composable
    private fun TopBar(item: PlayItem) {
        val now = System.currentTimeMillis()
        val nowProgram = programs.firstOrNull { it.start <= now && it.end > now }
        val nextProgram = programs.firstOrNull { it.start > now }
        val fmt = com.mojatv.app.data.EpgTime

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
                        "${fmt.hm(nowProgram.start)} ${nowProgram.title}",
                        color = Color(0xFFDDDDDD), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    LinearProgressIndicator(
                        progress = { nowProgram.progress(now) },
                        modifier = Modifier.width(200.dp).padding(vertical = 4.dp),
                    )
                }
                if (nextProgram != null) {
                    Text(
                        "Następnie ${fmt.hm(nextProgram.start)} ${nextProgram.title}",
                        color = Color(0xFFAAAAAA), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (isTv) {
                    Text(
                        if (item.isLive) "Wstecz chowa przyciski · ▲▼ kanały · ≡ opcje" else "Wstecz chowa przyciski · ◀▶ przewijanie · ≡ opcje",
                        color = Color(0xFF9BA3AF), fontSize = 12.sp,
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
            if (item.kind == com.mojatv.app.data.ContentKind.LIVE) {
                IconButton(onClick = { recordDialog = true }) {
                    Icon(Icons.Filled.FiberManualRecord, "Program i nagrywanie", tint = Color(0xFFE5484D))
                }
            }
            IconButton(onClick = { castDialog = true }) {
                Icon(Icons.Filled.Cast, "Odtwórz na telewizorze", tint = Color.White)
            }
            IconButton(onClick = { openExternal(item) }) {
                Icon(Icons.Filled.OpenInNew, "Zewnętrzny odtwarzacz", tint = Color.White)
            }
        }
    }

    // ---------------- Pilot telewizora ----------------

    @Composable
    private fun CastOverlay(item: PlayItem, tv: Renderer) {
        val now = System.currentTimeMillis()
        val nowProgram = programs.firstOrNull { it.start <= now && it.end > now }
        val fmt = com.mojatv.app.data.EpgTime
        val pos = castPos
        val playing = pos?.state == "PLAYING" || (pos == null && castBusy)

        Box(Modifier.fillMaxSize().background(Color(0xFF0E1116))) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { finish() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wstecz", tint = Color.White)
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { castDialog = true }) { Text("Zmień telewizor") }
            }
            Column(
                Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Filled.CastConnected, null, tint = Color(0xFFE5484D), modifier = Modifier.size(56.dp))
                Spacer(Modifier.size(8.dp))
                Text("Odtwarzanie na telewizorze", color = Color(0xFF9BA3AF), fontSize = 14.sp)
                Text(tv.name, color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.size(20.dp))
                Text(
                    (if (item.isLive) "${index + 1}. " else "") + item.title,
                    color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 22.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                item.subtitle?.takeIf { !item.isLive }?.let {
                    Text(it, color = Color(0xFFCCCCCC), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (nowProgram != null) {
                    Text(
                        "${fmt.hm(nowProgram.start)} ${nowProgram.title}",
                        color = Color(0xFFDDDDDD), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.size(16.dp))

                if (!item.isLive && pos != null && pos.durationMs > 0) {
                    LinearProgressIndicator(
                        progress = { (pos.positionMs.toFloat() / pos.durationMs).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        color = Color(0xFFE5484D),
                    )
                    Row(Modifier.fillMaxWidth()) {
                        Text(clock(pos.positionMs), color = Color(0xFFCCCCCC), fontSize = 12.sp)
                        Spacer(Modifier.weight(1f))
                        Text(clock(pos.durationMs), color = Color(0xFFCCCCCC), fontSize = 12.sp)
                    }
                }

                if (castBusy) {
                    Spacer(Modifier.size(8.dp))
                    CircularProgressIndicator(Modifier.size(28.dp), color = Color(0xFFE5484D))
                    Text("Wysyłam na telewizor…", color = Color(0xFF9BA3AF), fontSize = 13.sp)
                }
                castError?.let {
                    Spacer(Modifier.size(8.dp))
                    Text(it, color = Color(0xFFFF8A8D), fontSize = 13.sp)
                    TextButton(onClick = { play(index, -1L) }) { Text("Spróbuj ponownie") }
                }

                Spacer(Modifier.size(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RoundButton(Icons.Filled.SkipPrevious, "Poprzedni", items.size > 1 && index > 0) {
                        play(index - 1, if (item.isLive) -1L else 0L)
                    }
                    if (!item.isLive) RoundButton(Icons.Filled.Replay30, "−30 s", pos != null) { castSeekBy(-30_000) }
                    RoundButton(
                        if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        "Pauza / odtwarzaj", !castBusy, big = true,
                    ) { castToggle() }
                    if (!item.isLive) RoundButton(Icons.Filled.Forward30, "+30 s", pos != null) { castSeekBy(30_000) }
                    RoundButton(Icons.Filled.SkipNext, "Następny", items.size > 1 && index < items.lastIndex) {
                        play(index + 1, if (item.isLive) -1L else 0L)
                    }
                }
                Spacer(Modifier.size(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { stopCasting(resumeHere = true) }) { Text("Oglądaj na telefonie") }
                    Button(onClick = { stopCasting(resumeHere = false) }) { Text("Zakończ") }
                }
            }
        }
    }

    @Composable
    private fun RoundButton(
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        label: String,
        enabled: Boolean,
        big: Boolean = false,
        onClick: () -> Unit,
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(if (big) 72.dp else 54.dp)
                .background(if (big) Color(0xFFE5484D) else Color(0xFF1F242D), CircleShape),
        ) {
            Icon(icon, label, tint = if (enabled) Color.White else Color.Gray, modifier = Modifier.size(if (big) 40.dp else 28.dp))
        }
    }

    private fun clock(ms: Long): String {
        val s = ms / 1000
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
    }

    // ---------------- Wybór telewizora ----------------

    @Composable
    private fun CastDialog() {
        var found by remember { mutableStateOf<List<Renderer>>(emptyList()) }
        var searching by remember { mutableStateOf(true) }
        var attempt by remember { mutableIntStateOf(0) }
        var manualIp by remember { mutableStateOf("") }
        var manualBusy by remember { mutableStateOf(false) }
        var manualError by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()

        LaunchedEffect(attempt) {
            searching = true
            val list = mutableListOf<Renderer>()
            // najpierw ostatnio używany telewizor — zwykle odpowiada od razu
            repo.storage.castLocation?.let { loc ->
                runCatching { Dlna.fromLocation(loc) }.getOrNull()?.let { list.add(it) }
                found = list.toList()
            }
            val discovered = Dlna.discover(this@PlayerActivity)
            found = (list + discovered).distinctBy { it.avTransportUrl }
            searching = false
        }

        AlertDialog(
            onDismissRequest = { castDialog = false },
            title = { Text("Odtwórz na telewizorze") },
            text = {
                Column {
                    if (found.isEmpty() && !searching) {
                        Text(
                            "Nie znaleziono telewizora. Sprawdź, czy jest włączony i podłączony do tego samego Wi‑Fi.",
                            fontSize = 14.sp,
                        )
                    }
                    found.forEach { tv ->
                        Row(
                            Modifier.fillMaxWidth().clickable { startCasting(tv) }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Tv, null, tint = Color(0xFFE5484D))
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(tv.name, fontWeight = FontWeight.Medium)
                                Text(tv.host, fontSize = 12.sp, color = Color(0xFF9BA3AF))
                            }
                        }
                    }
                    if (searching) {
                        Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("  Szukam telewizorów w sieci…", fontSize = 14.sp)
                        }
                    }
                    Spacer(Modifier.size(12.dp))
                    Text("Albo wpisz adres IP telewizora:", fontSize = 13.sp, color = Color(0xFF9BA3AF))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = manualIp,
                            onValueChange = { manualIp = it },
                            placeholder = { Text("np. 192.168.0.240") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            enabled = !manualBusy && manualIp.isNotBlank(),
                            onClick = {
                                manualBusy = true
                                manualError = null
                                scope.launch {
                                    try {
                                        startCasting(Dlna.fromIp(manualIp))
                                    } catch (e: Exception) {
                                        manualError = e.message
                                    } finally {
                                        manualBusy = false
                                    }
                                }
                            },
                        ) { Text("Połącz") }
                    }
                    manualError?.let { Text(it, color = Color(0xFFFF8A8D), fontSize = 12.sp) }
                }
            },
            confirmButton = {
                TextButton(enabled = !searching, onClick = { attempt++ }) { Text("Szukaj ponownie") }
            },
            dismissButton = {
                TextButton(onClick = { castDialog = false }) { Text("Anuluj") }
            },
        )
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
