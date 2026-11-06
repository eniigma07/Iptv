package com.artykulacja.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.CompareArrows
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.artykulacja.app.app
import com.artykulacja.app.ui.theme.C

@Composable
fun PracticeHub(nav: NavHostController) {
    val store = app.store
    @Suppress("UNUSED_VARIABLE") val v = store.version
    val weak = store.weakest(1).firstOrNull()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column(Modifier.statusBarsPadding().padding(top = 22.dp, bottom = 8.dp)) {
                Eyebrow("Trening")
                Spacer(Modifier.height(8.dp))
                Text("Ćwicz to, czego potrzebujesz", style = MaterialTheme.typography.headlineLarge, color = C.Text)
            }
        }
        item { PlanCard(Icons.Rounded.Campaign, "Shadowing: język spotkań", "Frazy senior managera w 10 sytuacjach — melodia i pewność głosu") { nav.navigate("phrases") } }
        item { PlanCard(Icons.Rounded.Today, "Frazy dnia", "5 nowych zdań każdego dnia") { nav.navigate("drill/daily/0") } }
        if (weak != null) item {
            PlanCard(Icons.Rounded.GpsFixed, "Twoja najsłabsza głoska: /${weak.first}/", "Słowa i zdania celowane w ten dźwięk") { nav.navigate("drill/weak/${enc(weak.first)}") }
        }
        item { PlanCard(Icons.Rounded.CompareArrows, "Pary minimalne", "ship/sheep, think/sink, west/vest…") { nav.navigate("pairs_pick") } }
        item { PlanCard(Icons.Rounded.Straighten, "Akcent wyrazowy", "Gdzie pada akcent w słowach biznesowych") { nav.navigate("stress") } }
        item { PlanCard(Icons.Rounded.Bookmarks, "Moje słowa", "${store.myWords.size} na liście — nazwy, terminy, trudne słowa") { nav.navigate("mywords") } }
        item { PlanCard(Icons.Rounded.EditNote, "Twój tekst", "Wklej fragment maila lub prezentacji i przećwicz go") { nav.navigate("free") } }
    }
}

@Composable
fun PhrasesScreen(nav: NavHostController) {
    val cats = app.course.phrases
    Column(Modifier.fillMaxSize()) {
        TopBar("Język spotkań", { nav.popBackStack() })
        LazyColumn(contentPadding = PaddingValues(horizontal = 22.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text("Słuchaj wzorca i powtarzaj z tą samą melodią. Włącz tryb ciągły (∞), żeby ćwiczyć bez dotykania ekranu.",
                    style = MaterialTheme.typography.bodyMedium, color = C.Text2, modifier = Modifier.padding(bottom = 6.dp))
            }
            itemsIndexed(cats) { i, c ->
                Panel(Modifier.fillMaxWidth(), onClick = { nav.navigate("drill/phrases/$i") }, padding = PaddingValues(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(c.name, style = MaterialTheme.typography.titleMedium, color = C.Text)
                            Spacer(Modifier.height(4.dp))
                            Text("„${c.items.first()}”", style = MaterialTheme.typography.bodySmall, color = C.Text2, maxLines = 1)
                        }
                        Spacer(Modifier.width(10.dp))
                        Pill("${c.items.size}")
                    }
                }
            }
        }
    }
}

private val fieldColors @Composable get() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = C.Gold, unfocusedBorderColor = C.Line, focusedLabelColor = C.Gold, cursorColor = C.Gold,
    focusedTextColor = C.Text, unfocusedTextColor = C.Text,
)

@Composable
fun MyWordsScreen(nav: NavHostController) {
    val store = app.store
    @Suppress("UNUSED_VARIABLE") val v = store.version
    var input by remember { mutableStateOf("") }
    fun add() { if (input.isNotBlank()) { store.addWord(input.trim()); input = "" } }
    Column(Modifier.fillMaxSize().imePadding()) {
        TopBar("Moje słowa", { nav.popBackStack() })
        Column(Modifier.padding(horizontal = 22.dp)) {
            Text("Dodaj słowa i zwroty, które często mówisz w pracy: nazwy klientów, terminy, liczby.",
                style = MaterialTheme.typography.bodyMedium, color = C.Text2)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input, onValueChange = { input = it.take(120) }, singleLine = true,
                    placeholder = { Text("np. procurement, quarterly review") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add() }),
                    colors = fieldColors, shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                RoundIcon(Icons.Rounded.Add, "Dodaj", active = input.isNotBlank()) { add() }
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 22.dp)) {
            items(store.myWords.toList(), key = { it }) { w ->
                val unknown = app.speech.state.value == com.artykulacja.app.speech.Speech.State.Ready &&
                    app.speech.phonemizer.tokenize(w).any { it.isWord && !it.known }
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(w, style = MaterialTheme.typography.bodyLarge, color = C.Text)
                        if (unknown) Text("Słowo spoza słownika — wymowa przybliżona, bez oceny", style = MaterialTheme.typography.bodySmall, color = C.Mid)
                    }
                    IconButton(onClick = { nav.navigate("drill/text/${enc(w)}") }) { Icon(Icons.Rounded.GpsFixed, "Ćwicz", tint = C.Gold) }
                    IconButton(onClick = { store.removeWord(w) }) { Icon(Icons.Rounded.Delete, "Usuń", tint = C.Text3) }
                }
                HorizontalDivider(color = C.Line)
            }
        }
        if (store.myWords.isNotEmpty()) {
            PrimaryButton("Ćwicz całą listę (${store.myWords.size})", Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp)) {
                nav.navigate("drill/mywords/0")
            }
        }
    }
}

@Composable
fun FreeTextScreen(nav: NavHostController) {
    var text by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().imePadding()) {
        TopBar("Twój tekst", { nav.popBackStack() })
        Column(Modifier.weight(1f).padding(horizontal = 22.dp)) {
            Text("Wklej fragment, który masz powiedzieć — otwarcie prezentacji, odpowiedź dla zarządu. Podzielę go na zdania.",
                style = MaterialTheme.typography.bodyMedium, color = C.Text2)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = text, onValueChange = { text = it.take(2000) },
                placeholder = { Text("Good morning everyone. Today I'd like to walk you through…") },
                colors = fieldColors, shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp)
            )
            Spacer(Modifier.height(8.dp))
            val n = splitSentences(text).size
            if (n > 0) Text("$n ${if (n == 1) "zdanie" else if (n in 2..4) "zdania" else "zdań"}", style = MaterialTheme.typography.bodySmall, color = C.Text3)
        }
        PrimaryButton("Ćwicz", Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp), enabled = splitSentences(text).isNotEmpty()) {
            nav.navigate("drill/free/${enc(text)}")
        }
    }
}
