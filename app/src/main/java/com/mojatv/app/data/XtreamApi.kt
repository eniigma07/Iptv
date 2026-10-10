package com.mojatv.app.data

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

/** Serwer odpowiada, ale nie jak panel Xtream (np. strona HTML). */
class NotXtreamException : IOException("Serwer nie odpowiada jak panel Xtream. Sprawdź adres.")

/** Serwer potwierdził, że login lub hasło są złe. */
class AuthException : IOException("Nieprawidłowy login lub hasło.")

class XtreamApi(private val cfg: SourceConfig) {

    val base: String = normalizeServer(cfg.server)
    private val user = cfg.username.trim()
    private val pass = cfg.password.trim()

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun api(action: String? = null, extra: String = ""): String =
        "$base/player_api.php?username=${enc(user)}&password=${enc(pass)}" +
            (if (action != null) "&action=$action" else "") + extra

    fun m3uUrl(format: String): String =
        "$base/get.php?username=${enc(user)}&password=${enc(pass)}&type=m3u_plus&output=$format"

    fun xmltvUrl(): String = "$base/xmltv.php?username=${enc(user)}&password=${enc(pass)}"

    fun liveUrl(streamId: String, ext: String) = "$base/live/$user/$pass/$streamId.$ext"
    fun movieUrl(streamId: String, ext: String) = "$base/movie/$user/$pass/$streamId.$ext"
    fun episodeUrl(episodeId: String, ext: String) = "$base/series/$user/$pass/$episodeId.$ext"

    suspend fun login(): AccountInfo {
        val text = Http.getString(api())
        val root = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw NotXtreamException()
        }
        val info = root.optJSONObject("user_info") ?: throw NotXtreamException()
        if (info.optInt("auth", 0) != 1) throw AuthException()
        return AccountInfo(
            status = info.str("status"),
            expiresAt = info.str("exp_date")?.toLongOrNull()?.times(1000),
            maxConnections = info.str("max_connections"),
            activeConnections = info.str("active_cons"),
            isTrial = info.str("is_trial") == "1",
        )
    }

    suspend fun liveCategories(): List<Category> = categories("get_live_categories")
    suspend fun vodCategories(): List<Category> = categories("get_vod_categories")
    suspend fun seriesCategories(): List<Category> = categories("get_series_categories")

    private suspend fun categories(action: String): List<Category> {
        val arr = parseArray(Http.getString(api(action)))
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.str("category_id") ?: return@mapNotNull null
            Category(id, o.str("category_name") ?: "Bez nazwy")
        }
    }

    suspend fun liveStreams(ext: String): List<LiveChannel> {
        val arr = parseArray(Http.getString(api("get_live_streams")))
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.str("stream_id") ?: return@mapNotNull null
            LiveChannel(
                id = id,
                name = o.str("name") ?: "Kanał $id",
                logo = o.str("stream_icon"),
                categoryId = o.str("category_id") ?: "",
                epgId = o.str("epg_channel_id"),
                url = liveUrl(id, ext),
            )
        }
    }

    suspend fun vodStreams(): List<Movie> {
        val arr = parseArray(Http.getString(api("get_vod_streams")))
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.str("stream_id") ?: return@mapNotNull null
            Movie(
                id = id,
                name = o.str("name") ?: "Film $id",
                poster = o.str("stream_icon"),
                categoryId = o.str("category_id") ?: "",
                rating = o.str("rating")?.takeIf { it != "0" },
                url = movieUrl(id, o.str("container_extension") ?: "mp4"),
                added = o.str("added")?.toLongOrNull() ?: 0L,
            )
        }
    }

    suspend fun seriesList(): List<Series> {
        val arr = parseArray(Http.getString(api("get_series")))
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.str("series_id") ?: return@mapNotNull null
            Series(
                id = id,
                name = o.str("name") ?: "Serial $id",
                poster = o.str("cover"),
                categoryId = o.str("category_id") ?: "",
                rating = o.str("rating")?.takeIf { it != "0" },
                plot = o.str("plot"),
                genre = o.str("genre"),
                year = (o.str("releaseDate") ?: o.str("release_date"))?.take(4),
            )
        }
    }

    suspend fun movieDetail(movie: Movie): MovieDetail {
        val root = JSONObject(Http.getString(api("get_vod_info", "&vod_id=${movie.id}")))
        val info = root.optJSONObject("info") ?: JSONObject()
        return MovieDetail(
            movie = movie,
            plot = info.str("plot") ?: info.str("description"),
            cast = info.str("cast") ?: info.str("actors"),
            director = info.str("director"),
            genre = info.str("genre"),
            year = (info.str("releasedate") ?: info.str("release_date"))?.take(4),
            duration = info.str("duration"),
            backdrop = firstImage(info.opt("backdrop_path")) ?: info.str("movie_image"),
            rating = info.str("rating")?.takeIf { it != "0" } ?: movie.rating,
        )
    }

    suspend fun seriesDetail(series: Series): SeriesDetail {
        val root = JSONObject(Http.getString(api("get_series_info", "&series_id=${series.id}")))
        val info = root.optJSONObject("info") ?: JSONObject()
        val seasons = mutableMapOf<Int, MutableList<Episode>>()

        fun addEpisode(o: JSONObject, seasonHint: Int) {
            val id = o.str("id") ?: return
            val epInfo = o.optJSONObject("info")
            val season = o.str("season")?.toIntOrNull() ?: seasonHint
            val number = o.str("episode_num")?.toIntOrNull() ?: (seasons[season]?.size ?: 0) + 1
            seasons.getOrPut(season) { mutableListOf() }.add(
                Episode(
                    id = id,
                    season = season,
                    number = number,
                    title = o.str("title") ?: "Odcinek $number",
                    plot = epInfo?.str("plot"),
                    image = epInfo?.str("movie_image"),
                    duration = epInfo?.str("duration"),
                    url = episodeUrl(id, o.str("container_extension") ?: "mp4"),
                )
            )
        }

        when (val eps = root.opt("episodes")) {
            is JSONObject -> eps.keys().forEach { key ->
                val list = eps.optJSONArray(key) ?: return@forEach
                for (i in 0 until list.length()) list.optJSONObject(i)?.let { addEpisode(it, key.toIntOrNull() ?: 1) }
            }
            is JSONArray -> for (i in 0 until eps.length()) {
                val inner = eps.optJSONArray(i)
                if (inner != null) {
                    for (j in 0 until inner.length()) inner.optJSONObject(j)?.let { addEpisode(it, i + 1) }
                } else {
                    eps.optJSONObject(i)?.let { addEpisode(it, 1) }
                }
            }
        }

        return SeriesDetail(
            series = series,
            plot = info.str("plot") ?: series.plot,
            cast = info.str("cast"),
            genre = info.str("genre") ?: series.genre,
            year = (info.str("releaseDate") ?: info.str("release_date"))?.take(4) ?: series.year,
            backdrop = firstImage(info.opt("backdrop_path")) ?: info.str("cover"),
            seasons = seasons.toSortedMap().mapValues { (_, v) -> v.sortedBy { it.number } },
        )
    }

    suspend fun shortEpg(streamId: String, limit: Int = 6): List<EpgProgram> {
        val root = JSONObject(Http.getString(api("get_short_epg", "&stream_id=$streamId&limit=$limit")))
        val arr = root.optJSONArray("epg_listings") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val start = o.str("start_timestamp")?.toLongOrNull() ?: return@mapNotNull null
            val end = (o.str("stop_timestamp") ?: o.str("end_timestamp"))?.toLongOrNull() ?: return@mapNotNull null
            EpgProgram(
                title = decodeB64(o.str("title")) ?: "",
                description = decodeB64(o.str("description")),
                start = start * 1000,
                end = end * 1000,
                wallStart = EpgTime.parseWall(o.str("start")),
                wallEnd = EpgTime.parseWall(o.str("end") ?: o.str("stop")),
            )
        }
    }

    private fun decodeB64(s: String?): String? {
        if (s == null) return null
        return try {
            String(Base64.decode(s, Base64.DEFAULT), Charsets.UTF_8).trim()
        } catch (e: Exception) {
            s
        }
    }

    private fun firstImage(v: Any?): String? = when (v) {
        is JSONArray -> (0 until v.length()).map { v.optString(it) }.firstOrNull { it.startsWith("http") }
        is String -> v.takeIf { it.startsWith("http") }
        else -> null
    }

    companion object {
        fun parseArray(text: String): JSONArray {
            val t = text.trim()
            return if (t.startsWith("[")) JSONArray(t) else JSONArray()
        }

        fun normalizeServer(raw: String): String {
            var s = raw.trim()
            if (s.isEmpty()) return s
            if (!s.startsWith("http://", true) && !s.startsWith("https://", true)) s = "http://$s"
            for (marker in listOf("/player_api.php", "/get.php", "/xmltv.php")) {
                val idx = s.indexOf(marker)
                if (idx > 0) s = s.substring(0, idx)
            }
            return s.trimEnd('/')
        }

        /** Rozpoznaje link typu http://serwer:port/get.php?username=X&password=Y */
        fun credentialsFromUrl(url: String): SourceConfig? {
            val t = url.trim()
            if (!t.contains("username=") || !t.contains("password=")) return null
            return try {
                val uri = java.net.URI(t)
                val params = (uri.rawQuery ?: "").split("&").mapNotNull {
                    val p = it.split("=", limit = 2)
                    if (p.size == 2) p[0] to java.net.URLDecoder.decode(p[1], "UTF-8") else null
                }.toMap()
                val u = params["username"] ?: return null
                val p = params["password"] ?: return null
                val port = if (uri.port > 0) ":${uri.port}" else ""
                SourceConfig(
                    type = SourceType.XTREAM,
                    server = "${uri.scheme}://${uri.host}$port",
                    username = u,
                    password = p,
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
