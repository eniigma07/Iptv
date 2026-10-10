package com.mojatv.app.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.text.Normalizer

sealed interface LoadState {
    data object Idle : LoadState
    data class Loading(val message: String) : LoadState
    data class Ready(val catalog: Catalog) : LoadState
    data class Error(val message: String) : LoadState
}

data class Catalog(
    val type: SourceType,
    val liveCategories: List<Category>,
    val live: List<LiveChannel>,
    val movieCategories: List<Category>,
    val movies: List<Movie>,
    val seriesCategories: List<Category>,
    val series: List<Series>,
    val m3uEpisodes: Map<String, List<Episode>>,
    val epgUrl: String?,
) {
    private val radioCategoryIds: Set<String> =
        liveCategories.filter { isRadioName(it.name) }.map { it.id }.toSet()

    val radioChannels: List<LiveChannel> = live.filter { it.categoryId in radioCategoryIds }
}

/** Kategoria w zakładce Show — z filmów albo z seriali. */
data class ShowCategory(val key: String, val name: String, val isSeries: Boolean)

data class SearchResults(
    val live: List<LiveChannel>,
    val movies: List<Movie>,
    val series: List<Series>,
) {
    val isEmpty get() = live.isEmpty() && movies.isEmpty() && series.isEmpty()
}

private fun isRadioName(name: String) =
    name.contains("radio", ignoreCase = true) || name.contains("راديو") || name.contains("إذاع")

fun normalizeText(s: String): String =
    Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace('ł', 'l')

class Repository(context: Context) {
    val storage = Storage(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val loadMutex = Mutex()

    private val _state = MutableStateFlow<LoadState>(LoadState.Idle)
    val state: StateFlow<LoadState> = _state

    private val _epg = MutableStateFlow<Map<String, List<EpgProgram>>>(emptyMap())
    val epg: StateFlow<Map<String, List<EpgProgram>>> = _epg

    private val _epgStatus = MutableStateFlow<String?>(null)
    val epgStatus: StateFlow<String?> = _epgStatus

    private val _account = MutableStateFlow<AccountInfo?>(null)
    val account: StateFlow<AccountInfo?> = _account

    private val movieDetails = HashMap<String, MovieDetail>()
    private val seriesDetails = HashMap<String, SeriesDetail>()

    init {
        Http.userAgent = storage.userAgent
        EpgTime.configure(storage)
    }

    val catalog: Catalog? get() = (_state.value as? LoadState.Ready)?.catalog

    private fun xtream(): XtreamApi? =
        storage.source?.takeIf { it.type == SourceType.XTREAM }?.let { XtreamApi(it) }

    // ---------------- Logowanie ----------------

    /** Sprawdza dane, zapisuje źródło i wczytuje katalog. Rzuca wyjątek z opisem błędu. */
    suspend fun signIn(input: SourceConfig) {
        var cfg = input
        try {
            when (cfg.type) {
                SourceType.XTREAM -> {
                    if (cfg.server.isBlank() || cfg.username.isBlank()) throw IOException("Uzupełnij adres serwera i login.")
                    cfg = connectXtream(cfg.copy(server = XtreamApi.normalizeServer(cfg.server)))
                }
                SourceType.M3U -> {
                    if (!cfg.m3uUrl.startsWith("http", ignoreCase = true)) throw IOException("Podaj pełny link do playlisty (http…).")
                    var url = cfg.m3uUrl.trim()
                    val agents = (listOf(storage.userAgent) + Http.FALLBACK_USER_AGENTS).distinct()
                    var ua = findAgentForM3u(url, agents)
                    if (ua == null && url.startsWith("https://", ignoreCase = true) && lastM3uError?.let { isTlsProblem(it) } == true) {
                        url = "http://" + url.substring(8)
                        ua = findAgentForM3u(url, agents)
                    }
                    if (ua == null) {
                        val err = lastM3uError
                        if (err is HttpStatusException) {
                            throw IOException(
                                "Serwer odrzuca ten link (HTTP ${err.code}), także przy podszyciu się pod popularne aplikacje IPTV. " +
                                    "Sprawdź u dostawcy, czy konto jest aktywne i czy link jest aktualny."
                            )
                        }
                        throw err ?: IOException("Pod tym linkiem nie ma playlisty M3U.")
                    }
                    storage.userAgent = ua
                    cfg = cfg.copy(m3uUrl = url)
                }
            }
        } catch (e: Exception) {
            throw IOException(friendlyError(e), e)
        }
        storage.source = cfg
        _state.value = LoadState.Idle
        loadNow(force = true)
        val s = _state.value
        if (s is LoadState.Error) throw IOException(s.message)
    }

    /**
     * Próbuje zalogować się do Xtream na kilka sposobów:
     * 1) https:// → http://, gdy serwer nie obsługuje szyfrowania,
     * 2) różne nazwy aplikacji (User-Agent), gdy serwer blokuje nieznane aplikacje,
     * 3) gdy logowanie Xtream jest niedostępne — playlista M3U z tymi samymi danymi.
     * Zwraca konfigurację, która zadziałała.
     */
    private suspend fun connectXtream(cfg: SourceConfig): SourceConfig {
        val servers = buildList {
            add(cfg.server)
            if (cfg.server.startsWith("https://", ignoreCase = true)) add("http://" + cfg.server.substring(8))
        }
        val agents = (listOf(storage.userAgent) + Http.FALLBACK_USER_AGENTS).distinct()
        val originalAgent = Http.userAgent
        var lastError: Exception? = null
        var reachableServer: String? = null

        for (server in servers) {
            val candidate = cfg.copy(server = server)
            for (ua in agents) {
                Http.userAgent = ua
                try {
                    _account.value = XtreamApi(candidate).login()
                    storage.userAgent = ua
                    return candidate
                } catch (e: AuthException) {
                    Http.userAgent = originalAgent
                    throw e
                } catch (e: Exception) {
                    lastError = e
                    when {
                        isTlsProblem(e) -> break // ten adres nie działa — następny serwer
                        e is HttpStatusException || e is NotXtreamException -> reachableServer = server
                        else -> {
                            Http.userAgent = originalAgent
                            throw e // brak sieci, zły adres itp.
                        }
                    }
                }
            }
        }

        // Logowanie Xtream niedostępne — spróbuj playlisty get.php
        val server = reachableServer
        if (server != null) {
            val api = XtreamApi(cfg.copy(server = server))
            val url = api.m3uUrl(storage.liveFormat)
            val ua = findAgentForM3u(url, agents)
            if (ua != null) {
                storage.userAgent = ua
                _account.value = null
                return cfg.copy(
                    type = SourceType.M3U,
                    server = server,
                    m3uUrl = url,
                    epgUrl = cfg.epgUrl.ifBlank { api.xmltvUrl() },
                )
            }
            lastError = lastM3uError ?: lastError
        }

        Http.userAgent = originalAgent
        val err = lastError
        if (err is HttpStatusException || err is NotXtreamException) {
            throw IOException(
                "Serwer odpowiada, ale nie przyjmuje logowania pod tym adresem" +
                    (if (err is HttpStatusException) " (HTTP ${err.code})" else "") +
                    ". Sprawdź u dostawcy dokładny adres serwera (z portem) albo poproś o link M3U i użyj zakładki „Playlista M3U”."
            )
        }
        throw err ?: IOException("Nie udało się połączyć z serwerem.")
    }

    private var lastM3uError: Exception? = null

    /** Sprawdza, z którą nazwą aplikacji serwer oddaje playlistę. Ustawia ją i zwraca, albo null. */
    private suspend fun findAgentForM3u(url: String, agents: List<String>): String? {
        lastM3uError = null
        val original = Http.userAgent
        for (ua in agents) {
            Http.userAgent = ua
            try {
                val ok = Http.withStream(url) { input ->
                    val head = ByteArray(512)
                    val n = input.read(head)
                    n > 0 && String(head, 0, n, Charsets.UTF_8).trimStart('\uFEFF', ' ', '\r', '\n').startsWith("#EXTM3U")
                }
                if (ok) return ua
            } catch (e: Exception) {
                lastM3uError = e
                if (e !is HttpStatusException) break // brak sieci, zły adres — nie ma sensu próbować dalej
            }
        }
        Http.userAgent = original
        return null
    }

    private fun isTlsProblem(e: Throwable): Boolean {
        var t: Throwable? = e
        while (t != null) {
            if (t is javax.net.ssl.SSLException) return true
            t = t.cause
        }
        return false
    }

    /** Zamienia techniczne błędy sieci na zrozumiały komunikat. */
    fun friendlyError(e: Throwable): String {
        var t: Throwable? = e
        while (t != null) {
            when (t) {
                is javax.net.ssl.SSLException ->
                    return "Błąd połączenia szyfrowanego. Spróbuj adresu zaczynającego się od http:// zamiast https://."
                is java.net.UnknownHostException ->
                    return "Nie znaleziono serwera. Sprawdź adres i połączenie z internetem."
                is java.net.SocketTimeoutException ->
                    return "Serwer nie odpowiada (przekroczony czas). Spróbuj ponownie za chwilę."
                is java.net.ConnectException ->
                    return "Nie można połączyć się z serwerem. Sprawdź adres i port."
            }
            t = t.cause
        }
        return e.message ?: "Wystąpił nieznany błąd."
    }

    fun signOut() {
        storage.source = null
        _state.value = LoadState.Idle
        _epg.value = emptyMap()
        _account.value = null
        movieDetails.clear()
        seriesDetails.clear()
    }

    // ---------------- Wczytywanie katalogu ----------------

    fun load(force: Boolean = false) {
        scope.launch { loadNow(force) }
    }

    suspend fun loadNow(force: Boolean) {
        loadMutex.withLock {
            if (!force && _state.value is LoadState.Ready) return
            val cfg = storage.source ?: return
            _state.value = LoadState.Loading("Wczytywanie playlisty…")
            try {
                val raw = when (cfg.type) {
                    SourceType.XTREAM -> loadXtream(cfg)
                    SourceType.M3U -> loadM3u(cfg)
                }
                val cat = if (storage.hideAdult) withoutAdult(raw) else raw
                movieDetails.clear()
                seriesDetails.clear()
                _state.value = LoadState.Ready(cat)
                if (storage.epgEnabled) {
                    scope.launch { loadEpg(cfg, cat) }
                } else {
                    _epg.value = emptyMap()
                }
            } catch (e: Exception) {
                _state.value = LoadState.Error(friendlyError(e))
            }
        }
    }

    private suspend fun loadXtream(cfg: SourceConfig): Catalog = coroutineScope {
        val api = XtreamApi(cfg)
        val account = async { runCatching { api.login() }.getOrNull() }
        val liveCats = async { api.liveCategories() }
        val live = async { api.liveStreams(storage.liveFormat) }
        val vodCats = async { api.vodCategories() }
        val vod = async { api.vodStreams() }
        val serCats = async { api.seriesCategories() }
        val ser = async { api.seriesList() }
        account.await()?.let { _account.value = it }
        Catalog(
            type = SourceType.XTREAM,
            liveCategories = liveCats.await(),
            live = live.await(),
            movieCategories = vodCats.await(),
            movies = vod.await().sortedByDescending { it.added },
            seriesCategories = serCats.await(),
            series = ser.await(),
            m3uEpisodes = emptyMap(),
            epgUrl = cfg.epgUrl.ifBlank { api.xmltvUrl() },
        )
    }

    private suspend fun loadM3u(cfg: SourceConfig): Catalog {
        val playlist = Http.withStream(cfg.m3uUrl.trim()) { M3uParser.parse(it) }
        if (playlist.entries.isEmpty()) throw IOException("Playlista jest pusta albo ma nieprawidłowy format.")

        val liveGroups = LinkedHashSet<String>()
        val movieGroups = LinkedHashSet<String>()
        val seriesGroups = LinkedHashSet<String>()
        val live = ArrayList<LiveChannel>()
        val movies = ArrayList<Movie>()
        val series = LinkedHashMap<String, Series>()
        val episodes = HashMap<String, MutableList<Episode>>()

        playlist.entries.forEachIndexed { index, e ->
            val id = Integer.toHexString(e.url.hashCode()) + "-" + index
            when {
                M3uParser.isSeries(e) -> {
                    val (title, season, number) = M3uParser.episodeInfo(e, (index % 1000) + 1)
                    val sid = "m" + Integer.toHexString(normalizeText(title).hashCode())
                    series.getOrPut(sid) {
                        seriesGroups.add(e.group)
                        Series(sid, title, e.logo, e.group, null, null, null, null)
                    }
                    episodes.getOrPut(sid) { ArrayList() }.add(
                        Episode(id, season, number, e.name, null, e.logo, null, e.url)
                    )
                }
                M3uParser.isMovie(e) -> {
                    movieGroups.add(e.group)
                    movies.add(Movie(id, e.name, e.logo, e.group, null, e.url, 0L))
                }
                else -> {
                    liveGroups.add(e.group)
                    live.add(LiveChannel(id, e.name, e.logo, e.group, e.tvgId ?: e.name, e.url))
                }
            }
        }

        return Catalog(
            type = SourceType.M3U,
            liveCategories = liveGroups.map { Category(it, it) },
            live = live,
            movieCategories = movieGroups.map { Category(it, it) },
            movies = movies,
            seriesCategories = seriesGroups.map { Category(it, it) },
            series = series.values.toList(),
            m3uEpisodes = episodes,
            epgUrl = cfg.epgUrl.ifBlank { playlist.epgUrl ?: "" }.ifBlank { null },
        )
    }

    // ---------------- Kontrola rodzicielska ----------------

    private val adultWords = listOf("xxx", "adult", "18+", "+18", "porn", "erotic", "erotyk", "dla doroslych")

    private fun isAdult(name: String): Boolean {
        val n = normalizeText(name)
        return adultWords.any { n.contains(it) }
    }

    private fun withoutAdult(c: Catalog): Catalog {
        val liveCats = c.liveCategories.filterNot { isAdult(it.name) }
        val movieCats = c.movieCategories.filterNot { isAdult(it.name) }
        val seriesCats = c.seriesCategories.filterNot { isAdult(it.name) }
        // ukrywamy tylko pozycje z kategorii dla dorosłych
        val blockedLive = (c.liveCategories - liveCats.toSet()).map { it.id }.toSet()
        val blockedMovies = (c.movieCategories - movieCats.toSet()).map { it.id }.toSet()
        val blockedSeries = (c.seriesCategories - seriesCats.toSet()).map { it.id }.toSet()
        return c.copy(
            liveCategories = liveCats,
            live = c.live.filter { it.categoryId !in blockedLive && !isAdult(it.name) },
            movieCategories = movieCats,
            movies = c.movies.filter { it.categoryId !in blockedMovies },
            seriesCategories = seriesCats,
            series = c.series.filter { it.categoryId !in blockedSeries },
        )
    }

    // ---------------- Szczegóły ----------------

    suspend fun movieDetail(movie: Movie): MovieDetail {
        movieDetails[movie.id]?.let { return it }
        val api = xtream()
        val detail = if (api != null) {
            runCatching { api.movieDetail(movie) }.getOrElse { emptyMovieDetail(movie) }
        } else {
            emptyMovieDetail(movie)
        }
        movieDetails[movie.id] = detail
        return detail
    }

    private fun emptyMovieDetail(movie: Movie) =
        MovieDetail(movie, null, null, null, null, null, null, null, movie.rating)

    suspend fun seriesDetail(series: Series): SeriesDetail {
        seriesDetails[series.id]?.let { return it }
        val api = xtream()
        val detail = if (api != null) {
            api.seriesDetail(series)
        } else {
            val eps = catalog?.m3uEpisodes?.get(series.id).orEmpty()
            SeriesDetail(
                series = series,
                plot = null, cast = null, genre = null, year = null, backdrop = null,
                seasons = eps.groupBy { it.season }.toSortedMap().mapValues { (_, v) -> v.sortedBy { it.number } },
            )
        }
        seriesDetails[series.id] = detail
        return detail
    }

    fun findMovie(id: String): Movie? = catalog?.movies?.firstOrNull { it.id == id }
    fun findSeries(id: String): Series? = catalog?.series?.firstOrNull { it.id == id }

    // ---------------- EPG ----------------

    private suspend fun loadEpg(cfg: SourceConfig, cat: Catalog) {
        val url = cat.epgUrl ?: return
        val wanted = cat.live.mapNotNull { it.epgId?.lowercase() }.toSet()
        if (wanted.isEmpty()) return
        _epgStatus.value = "Wczytywanie programu TV…"
        try {
            val now = System.currentTimeMillis()
            val map = Http.withStream(url) {
                XmltvParser.parse(it, wanted, now - 3 * 3600_000L, now + 24 * 3600_000L)
            }
            _epg.value = map
            _epgStatus.value = null
        } catch (e: Exception) {
            _epgStatus.value = if (cfg.type == SourceType.XTREAM) null else "Nie udało się wczytać EPG"
        }
    }

    fun nowAndNext(epgId: String?, now: Long = System.currentTimeMillis()): Pair<EpgProgram?, EpgProgram?> {
        if (epgId == null) return null to null
        val raw = _epg.value[epgId.lowercase()] ?: return null to null
        val list = EpgTime.adjustAll(raw)
        val idx = list.indexOfFirst { it.start <= now && it.end > now }
        if (idx < 0) return null to list.firstOrNull { it.start > now }
        return list[idx] to list.getOrNull(idx + 1)
    }

    private val shortEpgCache = HashMap<String, Pair<Long, List<EpgProgram>>>()

    /** Surowy program z serwera (bez korekty godzin). limit = ile pozycji pobrać z Xtream. */
    private suspend fun rawPrograms(item: PlayItem, limit: Int): List<EpgProgram> {
        item.epgId?.lowercase()?.let { id ->
            _epg.value[id]?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        val api = xtream() ?: return emptyList()
        val streamId = when {
            item.key.startsWith("live:") -> item.key.removePrefix("live:")
            else -> return emptyList()
        }
        val cacheKey = "$streamId/$limit"
        synchronized(shortEpgCache) {
            shortEpgCache[cacheKey]?.let { (at, list) ->
                if (System.currentTimeMillis() - at < 5 * 60_000L) return list
            }
        }
        val list = runCatching { api.shortEpg(streamId, limit) }.getOrDefault(emptyList())
        synchronized(shortEpgCache) { shortEpgCache[cacheKey] = System.currentTimeMillis() to list }
        return list
    }

    /** Program dla kanału z poprawionymi godzinami (od bieżącej audycji). */
    suspend fun programsFor(item: PlayItem): List<EpgProgram> {
        val now = System.currentTimeMillis()
        return EpgTime.adjustAll(rawPrograms(item, 8)).filter { it.end > now }
    }

    /** Dłuższa lista audycji (do planowania nagrań). */
    suspend fun upcomingPrograms(item: PlayItem): List<EpgProgram> {
        val now = System.currentTimeMillis()
        return EpgTime.adjustAll(rawPrograms(item, 60)).filter { it.end > now }.sortedBy { it.start }
    }

    /** Zapisuje ustawienia korekty godzin i od razu je stosuje. */
    fun setEpgCorrection(mode: String? = null, shiftMinutes: Int? = null, showPolish: Boolean? = null) {
        mode?.let { storage.epgMode = it }
        shiftMinutes?.let { storage.epgShiftMinutes = it }
        showPolish?.let { storage.epgShowPolish = it }
        EpgTime.configure(storage)
        _epg.value = _epg.value.toMap() // odświeżenie ekranów korzystających z programu
    }

    // ---------------- Show ----------------

    private val showWords = listOf(
        "show", "program", "reality", "talk", "rozrywk", "entertain", "dokument", "documentar",
        "kabaret", "stand-up", "stand up", "standup", "teleturniej", "quiz", "magazyn", "lifestyle",
        "kulinar", "cooking", "travel", "podroz", "variety", "برامج", "منوعات", "وثائقي", "عروض",
    )

    fun allShowCandidates(c: Catalog): List<ShowCategory> =
        c.movieCategories.map { ShowCategory("movie:${it.id}", it.name, false) } +
            c.seriesCategories.map { ShowCategory("series:${it.id}", it.name, true) }

    fun autoShowKeys(c: Catalog): Set<String> =
        allShowCandidates(c).filter { cat ->
            val n = normalizeText(cat.name)
            showWords.any { n.contains(it) }
        }.map { it.key }.toSet()

    fun showCategories(c: Catalog): List<ShowCategory> {
        val keys = storage.showCategories ?: autoShowKeys(c)
        return allShowCandidates(c).filter { it.key in keys }
    }

    // ---------------- Wyszukiwanie ----------------

    fun search(c: Catalog, query: String, limit: Int = 60): SearchResults {
        val q = normalizeText(query.trim())
        if (q.length < 2) return SearchResults(emptyList(), emptyList(), emptyList())
        return SearchResults(
            live = c.live.asSequence().filter { normalizeText(it.name).contains(q) }.take(limit).toList(),
            movies = c.movies.asSequence().filter { normalizeText(it.name).contains(q) }.take(limit).toList(),
            series = c.series.asSequence().filter { normalizeText(it.name).contains(q) }.take(limit).toList(),
        )
    }
}
