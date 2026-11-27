package com.mojatv.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.mojatv.app.MojaTvApp
import com.mojatv.app.data.SavedProfile
import com.mojatv.app.pair.TvFinder
import com.mojatv.app.ui.theme.Accent
import com.mojatv.app.ui.theme.Background
import com.mojatv.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Telefon: wysyła zapisany profil (dane logowania) na Fire TV — na telewizorze nic nie trzeba wpisywać. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendToTvScreen(nav: NavController) {
    val context = LocalContext.current
    val repo = MojaTvApp.repo
    val scope = rememberCoroutineScope()

    val finder = remember { TvFinder(context.applicationContext) }
    DisposableEffect(finder) {
        finder.start()
        onDispose { finder.stop() }
    }
    val tvs by finder.found.collectAsState()
    val profiles by repo.storage.profiles.collectAsState()
    val currentKey = repo.storage.source?.let { SavedProfile.profileKey(it) }

    var selectedProfile by remember { mutableStateOf(profiles.firstOrNull { it.key == currentKey } ?: profiles.firstOrNull()) }
    var selectedTv by remember { mutableStateOf<String?>(null) }   // nazwa znalezionego TV
    var manualAddress by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }

    LaunchedEffect(tvs) {
        if (tvs.none { it.name == selectedTv }) selectedTv = tvs.firstOrNull()?.name
    }

    fun send() {
        val profile = selectedProfile ?: return
        busy = true
        done = false
        isError = false
        message = "Łączę z telewizorem…"
        scope.launch {
            val base = if (manualAddress.isNotBlank()) {
                TvFinder.normalizeAddress(manualAddress)
            } else {
                tvs.firstOrNull { it.name == selectedTv }?.let { finder.resolve(it) }
            }
            if (base == null) {
                busy = false
                isError = true
                message = "Nie znaleziono adresu telewizora. Wpisz adres widoczny na ekranie TV."
                return@launch
            }
            val err = TvFinder.send(base, profile.config, profile.name, pin)
            if (err != null) {
                busy = false
                isError = true
                message = err
                return@launch
            }
            // telewizor sprawdza dane i wczytuje kanały — pokazujemy postęp
            message = "Telewizor sprawdza dane i wczytuje kanały…"
            var misses = 0
            while (true) {
                delay(1500)
                val st = TvFinder.status(base)
                if (st == null) {
                    if (++misses > 4) {
                        // ekran parowania się zamknął = telewizor jest już zalogowany
                        done = true
                        message = "Gotowe! Sprawdź telewizor."
                        break
                    }
                    continue
                }
                misses = 0
                when (st.first) {
                    "done" -> { done = true; message = st.second ?: "Gotowe! Telewizor jest zalogowany."; break }
                    "error" -> { isError = true; message = st.second ?: "Nie udało się zalogować."; break }
                    "loading" -> message = st.second ?: message
                }
            }
            busy = false
        }
    }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text("Wyślij profil na telewizor") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wstecz") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(
                "Na Fire TV otwórz MojaTV. Przy pierwszym uruchomieniu od razu pokaże się ekran z kodem QR i PIN-em, " +
                    "a później: Ustawienia → Dodaj profil telefonem. Telefon i telewizor muszą być w tej samej sieci Wi‑Fi.",
                color = TextSecondary, fontSize = 14.sp,
            )

            Header("1. Który profil wysłać")
            if (profiles.isEmpty()) {
                Text("Najpierw zaloguj się na telefonie.", color = TextSecondary)
            }
            profiles.forEach { p ->
                Row(
                    Modifier.fillMaxWidth().clickable { selectedProfile = p },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selectedProfile?.key == p.key, onClick = null)
                    Column(Modifier.padding(start = 8.dp, top = 6.dp, bottom = 6.dp)) {
                        Text(p.name)
                        Text(SavedProfile.defaultName(p.config), color = TextSecondary, fontSize = 12.sp)
                    }
                }
            }

            Header("2. Telewizor")
            if (tvs.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Szukam telewizora z otwartym ekranem logowania…", color = TextSecondary, fontSize = 14.sp)
                }
            }
            tvs.forEach { tv ->
                Row(
                    Modifier.fillMaxWidth().clickable { selectedTv = tv.name; manualAddress = "" },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selectedTv == tv.name && manualAddress.isBlank(), onClick = null)
                    Icon(Icons.Filled.Tv, null, tint = Accent, modifier = Modifier.padding(start = 8.dp))
                    Text(tv.name, modifier = Modifier.padding(start = 8.dp, top = 10.dp, bottom = 10.dp))
                }
            }
            OutlinedTextField(
                value = manualAddress,
                onValueChange = { manualAddress = it },
                label = { Text("albo wpisz adres z ekranu TV") },
                placeholder = { Text("np. 192.168.1.20:8765") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )

            Header("3. Kod PIN z ekranu telewizora")
            OutlinedTextField(
                value = pin,
                onValueChange = { v -> pin = v.filter { it.isDigit() }.take(4) },
                label = { Text("PIN") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.width(160.dp),
            )

            Spacer(Modifier.height(20.dp))
            val hasTv = manualAddress.isNotBlank() || tvs.any { it.name == selectedTv }
            Button(
                onClick = { send() },
                enabled = !busy && selectedProfile != null && hasTv && pin.length == 4,
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                else Text("Wyślij na telewizor")
            }

            message?.let { m ->
                Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (done) {
                        Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF3DD68C))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(m, color = if (isError) MaterialTheme.colorScheme.error else Color.Unspecified)
                }
            }
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 20.dp, bottom = 6.dp),
    )
}
