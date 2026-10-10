package com.mojatv.app.data

import java.io.InputStream

data class M3uEntry(
    val name: String,
    val url: String,
    val group: String,
    val logo: String?,
    val tvgId: String?,
)

data class M3uPlaylist(val epgUrl: String?, val entries: List<M3uEntry>)

object M3uParser {
    private val attrRegex = Regex("""([A-Za-z0-9_\-]+)="([^"]*)"""")

    fun parse(input: InputStream): M3uPlaylist {
        val entries = ArrayList<M3uEntry>(4096)
        var epgUrl: String? = null
        var pendingInfo: String? = null
        var pendingGroup: String? = null

        input.bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (raw in lines) {
                val line = raw.trim()
                if (line.isEmpty()) continue
                when {
                    line.startsWith("#EXTM3U") -> {
                        val attrs = attrs(line)
                        epgUrl = attrs["x-tvg-url"] ?: attrs["url-tvg"] ?: attrs["tvg-url"]
                        epgUrl = epgUrl?.split(",")?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
                    }
                    line.startsWith("#EXTINF") -> {
                        pendingInfo = line
                        pendingGroup = null
                    }
                    line.startsWith("#EXTGRP:") -> pendingGroup = line.removePrefix("#EXTGRP:").trim()
                    line.startsWith("#") -> Unit
                    else -> {
                        val info = pendingInfo
                        if (info != null) {
                            val attrs = attrs(info)
                            val name = nameOf(info).ifBlank { attrs["tvg-name"] ?: "Bez nazwy" }
                            entries.add(
                                M3uEntry(
                                    name = name,
                                    url = line,
                                    group = attrs["group-title"]?.takeIf { it.isNotBlank() }
                                        ?: pendingGroup ?: "Inne",
                                    logo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() },
                                    tvgId = attrs["tvg-id"]?.takeIf { it.isNotBlank() },
                                )
                            )
                        }
                        pendingInfo = null
                        pendingGroup = null
                    }
                }
            }
        }
        return M3uPlaylist(epgUrl, entries)
    }

    private fun attrs(line: String): Map<String, String> =
        attrRegex.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }

    private fun nameOf(line: String): String {
        val lastAttrEnd = attrRegex.findAll(line).lastOrNull()?.range?.last ?: line.indexOf(':')
        val comma = line.indexOf(',', lastAttrEnd + 1)
        return if (comma >= 0) line.substring(comma + 1).trim() else ""
    }

    // ---- Klasyfikacja wpisów ----

    private val videoExt = listOf(".mp4", ".mkv", ".avi", ".mov", ".m4v", ".wmv", ".flv")
    private val episodeRegex = Regex("""^(.*?)[\s._\-|:]*[Ss](\d{1,2})[\s._\-]*[Ee](\d{1,3})""")

    fun isRadio(e: M3uEntry): Boolean =
        e.group.contains("radio", ignoreCase = true) || e.group.contains("راديو")

    fun isSeries(e: M3uEntry): Boolean =
        e.url.contains("/series/") || (isVideoFile(e.url) && episodeRegex.containsMatchIn(e.name))

    fun isMovie(e: M3uEntry): Boolean =
        !isSeries(e) && (e.url.contains("/movie/") || isVideoFile(e.url))

    private fun isVideoFile(url: String): Boolean {
        val path = url.substringBefore('?').lowercase()
        return videoExt.any { path.endsWith(it) }
    }

    /** Zwraca (nazwa serialu, sezon, odcinek). */
    fun episodeInfo(e: M3uEntry, fallbackIndex: Int): Triple<String, Int, Int> {
        val m = episodeRegex.find(e.name)
        if (m != null) {
            val title = m.groupValues[1].trim().trimEnd('-', '|', ':', '.').trim()
            return Triple(
                title.ifBlank { e.group },
                m.groupValues[2].toIntOrNull() ?: 1,
                m.groupValues[3].toIntOrNull() ?: fallbackIndex,
            )
        }
        return Triple(e.name, 1, fallbackIndex)
    }
}
