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
                    view.useController = !inPip && castTarget == null
                    view.resizeMode = resizeMode
                },
            )

            val item = current
            if (item != null && castTarget != null) {
                CastOverlay(item, castTarget!!)
            } else if (item != null && controlsVisible && !inPip) {
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

            if (castDialog) CastDialog()
            if (recordDialog && item != null) {
                com.mojatv.app.ui.RecordDialog(item) { recordDialog = false }
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
                    Icon(Icons.Filled.FiberManualRecord, "Nagraj", tint = Color(0xFFE5484D))
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
