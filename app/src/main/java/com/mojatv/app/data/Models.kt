package com.mojatv.app.data

import org.json.JSONObject

enum class SourceType { XTREAM, M3U }

data class SourceConfig(
    val type: SourceType,
    val server: String = "",
    val username: String = "",
    val password: String = "",
    val m3uUrl: String = "",
    val epgUrl: String = "",
) {
    fun toJson(): String = JSONObject()
        .put("type", type.name)
        .put("server", server)
        .put("username", username)
        .put("password", password)
        .put("m3uUrl", m3uUrl)
        .put("epgUrl", epgUrl)
        .toString()

    companion object {
        fun fromJson(s: String): SourceConfig? = try {
            val o = JSONObject(s)
            SourceConfig(
                type = SourceType.valueOf(o.getString("type")),
                server = o.optString("server"),
                username = o.optString("username"),
                password = o.optString("password"),
                m3uUrl = o.optString("m3uUrl"),
                epgUrl = o.optString("epgUrl"),
            )
        } catch (e: Exception) {
            null
        }
    }
}

enum class ContentKind { LIVE, MOVIE, SERIES, EPISODE, RADIO }

data class Category(val id: String, val name: String)

data class LiveChannel(
    val id: String,
    val name: String,
    val logo: String?,
    val categoryId: String,
    val epgId: String?,
    val url: String,
)

data class Movie(
    val id: String,
    val name: String,
    val poster: String?,
    val categoryId: String,
    val rating: String?,
    val url: String,
    val added: Long,
)

data class Series(
    val id: String,
    val name: String,
    val poster: String?,
    val categoryId: String,
    val rating: String?,
    val plot: String?,
    val genre: String?,
    val year: String?,
)

data class Episode(
    val id: String,
    val season: Int,
    val number: Int,
    val title: String,
    val plot: String?,
    val image: String?,
    val duration: String?,
    val url: String,
)

data class MovieDetail(
    val movie: Movie,
    val plot: String?,
    val cast: String?,
    val director: String?,
    val genre: String?,
    val year: String?,
    val duration: String?,
    val backdrop: String?,
    val rating: String?,
)

data class SeriesDetail(
    val series: Series,
    val plot: String?,
    val cast: String?,
    val genre: String?,
    val year: String?,
    val backdrop: String?,
    val seasons: Map<Int, List<Episode>>,
)

data class EpgProgram(
    val title: String,
    val description: String?,
    val start: Long,
    val end: Long,
    /** Godziny „z zegara” zapisane przez dostawcę (cyfry czytane jak UTC) — do korekty strefy. */
    val wallStart: Long? = null,
    val wallEnd: Long? = null,
) {
    fun progress(now: Long = System.currentTimeMillis()): Float =
        if (end <= start) 0f else ((now - start).toFloat() / (end - start)).coerceIn(0f, 1f)
}

data class RadioStation(
    val id: String,
    val name: String,
    val url: String,
    val logo: String?,
    val tags: String?,
    val country: String?,
)

data class AccountInfo(
    val status: String?,
    val expiresAt: Long?,
    val maxConnections: String?,
    val activeConnections: String?,
    val isTrial: Boolean,
)

/** Wszystko, co da się odtworzyć, zapisać w ulubionych albo w historii. */
data class PlayItem(
    val key: String,
    val kind: ContentKind,
    val title: String,
    val url: String,
    val image: String? = null,
    val subtitle: String? = null,
    val epgId: String? = null,
    val parentId: String? = null,
) {
    val isLive: Boolean get() = kind == ContentKind.LIVE || kind == ContentKind.RADIO

    fun toJson(): JSONObject = JSONObject()
        .put("key", key)
        .put("kind", kind.name)
        .put("title", title)
        .put("url", url)
        .put("image", image ?: JSONObject.NULL)
        .put("subtitle", subtitle ?: JSONObject.NULL)
        .put("epgId", epgId ?: JSONObject.NULL)
        .put("parentId", parentId ?: JSONObject.NULL)

    companion object {
        fun fromJson(o: JSONObject): PlayItem? = try {
            PlayItem(
                key = o.getString("key"),
                kind = ContentKind.valueOf(o.getString("kind")),
                title = o.getString("title"),
                url = o.getString("url"),
                image = o.str("image"),
                subtitle = o.str("subtitle"),
                epgId = o.str("epgId"),
                parentId = o.str("parentId"),
            )
        } catch (e: Exception) {
            null
        }
    }
}

data class WatchProgress(
    val item: PlayItem,
    val position: Long,
    val duration: Long,
    val updatedAt: Long,
) {
    val fraction: Float get() = if (duration <= 0) 0f else (position.toFloat() / duration).coerceIn(0f, 1f)
    val finished: Boolean get() = duration > 0 && position >= duration * 0.95
}

fun LiveChannel.toPlayItem() = PlayItem(
    key = "live:$id", kind = ContentKind.LIVE, title = name, url = url, image = logo, epgId = epgId,
)

fun Movie.toPlayItem() = PlayItem(
    key = "movie:$id", kind = ContentKind.MOVIE, title = name, url = url, image = poster,
)

fun Series.toPlayItem() = PlayItem(
    key = "series:$id", kind = ContentKind.SERIES, title = name, url = "", image = poster,
)

fun Episode.toPlayItem(series: Series) = PlayItem(
    key = "episode:$id",
    kind = ContentKind.EPISODE,
    title = series.name,
    url = url,
    image = image ?: series.poster,
    subtitle = "S${season.toString().padStart(2, '0')}E${number.toString().padStart(2, '0')} · $title",
    parentId = series.id,
)

fun RadioStation.toPlayItem() = PlayItem(
    key = "radio:$id", kind = ContentKind.RADIO, title = name, url = url, image = logo, subtitle = tags,
)

/** Bezpieczny odczyt tekstu z JSON (org.json zwraca "null" zamiast null). */
fun JSONObject.str(name: String): String? {
    if (!has(name) || isNull(name)) return null
    val v = optString(name).trim()
    return if (v.isEmpty() || v == "null") null else v
}
