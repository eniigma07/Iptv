package com.mojatv.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.mojatv.app.MojaTvApp
import com.mojatv.app.data.SourceConfig
import com.mojatv.app.data.SourceType
import com.mojatv.app.data.XtreamApi
import com.mojatv.app.ui.theme.Accent
import com.mojatv.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(onDone: () -> Unit) {
    val repo = MojaTvApp.repo
    val saved = repo.storage.source
    val scope = rememberCoroutineScope()

    var tab by rememberSaveable { mutableIntStateOf(if (saved?.type == SourceType.M3U) 1 else 0) }
    var server by rememberSaveable { mutableStateOf(saved?.server ?: "") }
    var username by rememberSaveable { mutableStateOf(saved?.username ?: "") }
    var password by rememberSaveable { mutableStateOf(saved?.password ?: "") }
    var m3uUrl by rememberSaveable { mutableStateOf(saved?.m3uUrl ?: "") }
    var epgUrl by rememberSaveable { mutableStateOf(saved?.epgUrl ?: "") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var busy by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    fun submit() {
        val cfg = if (tab == 0) {
            // ktoś mógł wkleić cały link get.php z danymi
            XtreamApi.credentialsFromUrl(server)?.copy(epgUrl = epgUrl.trim())
                ?: SourceConfig(SourceType.XTREAM, server.trim(), username.trim(), password.trim(), epgUrl = epgUrl.trim())
        } else {
            SourceConfig(SourceType.M3U, m3uUrl = m3uUrl.trim(), epgUrl = epgUrl.trim())
        }
        busy = true
        error = null
        scope.launch {
            try {
                repo.signIn(cfg)
                onDone()
            } catch (e: Exception) {
                error = e.message ?: "Nie udało się zalogować."
            } finally {
                busy = false
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Icon(Icons.Filled.LiveTv, null, tint = Accent, modifier = Modifier.size(56.dp))
        Text("MojaTV", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Dodaj swoją playlistę IPTV", color = TextSecondary)
        Spacer(Modifier.height(24.dp))

        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Xtream Codes") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Playlista M3U") })
        }
        Spacer(Modifier.height(16.dp))

        if (tab == 0) {
            OutlinedTextField(
                value = server, onValueChange = { server = it },
                label = { Text("Adres serwera") },
                placeholder = { Text("http://serwer.com:8080") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = username, onValueChange = { username = it },
                label = { Text("Nazwa użytkownika") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = password, onValueChange = { password = it },
                label = { Text("Hasło") },
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Pokaż hasło")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Możesz też wkleić w polu adresu cały link get.php?username=…&password=…",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp),
            )
        } else {
            OutlinedTextField(
                value = m3uUrl, onValueChange = { m3uUrl = it },
                label = { Text("Link do playlisty M3U") },
                placeholder = { Text("http://…/playlist.m3u") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            val detected = XtreamApi.credentialsFromUrl(m3uUrl)
            if (detected != null) {
                TextButton(onClick = {
                    server = detected.server
                    username = detected.username
                    password = detected.password
                    tab = 0
                }) {
                    Text("Link zawiera dane Xtream — użyj logowania Xtream (pełne opisy filmów i seriali)")
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = epgUrl, onValueChange = { epgUrl = it },
            label = { Text("Link do EPG (XMLTV) — opcjonalnie") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )

        if (error != null) {
            Text(error ?: "", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { submit() },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                Text("Zaloguj")
            }
        }
        if (busy) {
            Text(
                "Wczytuję kanały, filmy i seriale. Duże playlisty mogą potrzebować chwili…",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
