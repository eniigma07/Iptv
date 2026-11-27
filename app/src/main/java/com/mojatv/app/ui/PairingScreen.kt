package com.mojatv.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.mojatv.app.MojaTvApp
import com.mojatv.app.data.SavedProfile
import com.mojatv.app.pair.PairServer
import com.mojatv.app.ui.theme.Accent
import com.mojatv.app.ui.theme.SurfaceHigh
import com.mojatv.app.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.concurrent.thread

/**
 * Ekran na Fire TV: logowanie telefonem.
 * Kod QR prowadzi do strony z formularzem na telefonie; aplikacja MojaTV na telefonie
 * może też wysłać gotowy profil („Wyślij profil na telewizor”). Obok — zapisane profile.
 */
@Composable
fun PairingScreen(
    onDone: () -> Unit,
    onManual: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val repo = MojaTvApp.repo
    val scope = rememberCoroutineScope()
    var switching by remember { mutableStateOf<String?>(null) }
    var switchError by remember { mutableStateOf<String?>(null) }

    val latestOnDone by rememberUpdatedState(onDone)
    val server = remember {
        lateinit var s: PairServer
        s = PairServer(context.applicationContext) { cfg, name ->
            scope.launch {
                try {
                    repo.signIn(cfg, name)
                    s.setStatus(PairServer.Status.Done)
                    delay(1500)
                    latestOnDone()
                } catch (e: Exception) {
                    s.setStatus(PairServer.Status.Failed(e.message ?: "Nie udało się zalogować."))
                }
            }
        }
        s
    }
    LaunchedEffect(server) { withContext(Dispatchers.IO) { server.start() } }
    DisposableEffect(server) {
        onDispose { thread(isDaemon = true) { server.stop() } }
    }

    val address by server.address.collectAsState()
    val pin by server.pin.collectAsState()
    val status by server.status.collectAsState()
    val profiles by repo.storage.profiles.collectAsState()
    val qr = remember(address, pin) { server.qrUrl()?.let { qrBitmap(it, 600) } }

    fun useProfile(p: SavedProfile) {
        switching = p.name
        switchError = null
        scope.launch {
            try {
                repo.signIn(p.config, p.name)
                onDone()
            } catch (e: Exception) {
                switchError = e.message ?: "Nie udało się zalogować."
            } finally {
                switching = null
            }
        }
    }

    Row(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 48.dp, vertical = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(40.dp),
    ) {
        // ---- lewa strona: kod QR i PIN ----
        Column(Modifier.weight(1.1f).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.PhoneAndroid, null, tint = Accent, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(10.dp))
                Text("Zaloguj telefonem", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Bez wpisywania pilotem. Telefon musi być w tej samej sieci Wi‑Fi co telewizor.",
                color = TextSecondary, fontSize = 15.sp,
            )
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier
                        .size(220.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .padding(10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (qr != null) {
                        Image(qr, "Kod QR", Modifier.fillMaxSize(), filterQuality = FilterQuality.None)
                    } else {
                        Text("Brak sieci", color = Color.Black, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
                Spacer(Modifier.width(24.dp))
                Column {
                    Step("1", "Zeskanuj kod aparatem telefonu i wpisz dane na telefonie.")
                    Step("2", "Albo w MojaTV na telefonie: Ustawienia → Wyślij profil na telewizor.")
                    Step("3", "Albo otwórz w przeglądarce telefonu:")
                    Text(
                        address ?: "— brak połączenia z siecią —",
                        fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
                        modifier = Modifier.padding(start = 30.dp, bottom = 12.dp),
                    )
                    Text("Kod PIN", color = TextSecondary, fontSize = 14.sp)
                    Text(pin, fontSize = 44.sp, fontWeight = FontWeight.Bold, color = Accent, letterSpacing = 8.sp)
                }
            }
            Spacer(Modifier.height(20.dp))
            StatusLine(status)
        }

        // ---- prawa strona: zapisane profile i ręczne wpisanie ----
        Column(
            Modifier
                .weight(0.9f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
        ) {
            if (profiles.isNotEmpty()) {
                Text("Zapisane profile", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                val currentKey = repo.storage.source?.let { SavedProfile.profileKey(it) }
                profiles.forEach { p ->
                    ProfileRow(p, isCurrent = p.key == currentKey, busy = switching == p.name) { useProfile(p) }
                }
                switchError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }
                Spacer(Modifier.height(20.dp))
            }
            OutlinedButton(onClick = onManual, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Keyboard, null)
                Spacer(Modifier.width(8.dp))
                Text("Wpisz dane pilotem")
            }
            if (onBack != null) {
                Spacer(Modifier.height(8.dp))
                Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Wstecz") }
            }
        }
    }
}

@Composable
private fun Step(n: String, text: String) {
    Row(Modifier.padding(bottom = 10.dp)) {
        Box(
            Modifier.size(22.dp).clip(RoundedCornerShape(11.dp)).background(Accent),
            contentAlignment = Alignment.Center,
        ) { Text(n, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White) }
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 15.sp, modifier = Modifier.width(340.dp))
    }
}

@Composable
private fun StatusLine(status: PairServer.Status) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceHigh)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (status) {
            PairServer.Status.Waiting -> {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = TextSecondary)
                Spacer(Modifier.width(12.dp))
                Text("Czekam na dane z telefonu…", color = TextSecondary)
            }
            is PairServer.Status.Loading -> {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text(status.message)
            }
            PairServer.Status.Done -> {
                Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF3DD68C))
                Spacer(Modifier.width(12.dp))
                Text("Zalogowano!")
            }
            is PairServer.Status.Failed -> {
                Text(status.message + " Popraw dane na telefonie i wyślij ponownie.", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun ProfileRow(p: SavedProfile, isCurrent: Boolean, busy: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.AccountCircle, null, tint = if (isCurrent) Accent else TextSecondary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(
                if (isCurrent) "Używany teraz" else SavedProfile.defaultName(p.config),
                color = TextSecondary, fontSize = 12.sp, maxLines = 1,
            )
        }
        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
    }
}

/** Kod QR jako obraz (czarne moduły na białym tle). */
fun qrBitmap(text: String, size: Int): ImageBitmap? = try {
    val matrix = QRCodeWriter().encode(
        text, BarcodeFormat.QR_CODE, size, size,
        mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.CHARACTER_SET to "UTF-8"),
    )
    val w = matrix.width
    val h = matrix.height
    val pixels = IntArray(w * h)
    for (y in 0 until h) {
        val row = y * w
        for (x in 0 until w) pixels[row + x] = if (matrix.get(x, y)) -0x1000000 else -0x1
    }
    Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
} catch (e: Exception) {
    null
}
