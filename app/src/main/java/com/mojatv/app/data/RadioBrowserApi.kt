package com.mojatv.app.data

import java.io.IOException
import java.net.URLEncoder

/** Darmowy katalog radia internetowego: https://www.radio-browser.info */
object RadioBrowserApi {
    private val hosts = listOf(
        "https://de1.api.radio-browser.info",
        "https://nl1.api.radio-browser.info",
        "https://at1.api.radio-browser.info",
        "https://fi1.api.radio-browser.info",
    )

    suspend fun topByCountry(countryCode: String, limit: Int = 150): List<RadioStation> =
        get("/json/stations/bycountrycodeexact/$countryCode?order=clickcount&reverse=true&hidebroken=true&limit=$limit")

    suspend fun search(query: String, limit: Int = 80): List<RadioStation> =
        get("/json/stations/search?name=${URLEncoder.encode(query, "UTF-8")}&order=clickcount&reverse=true&hidebroken=true&limit=$limit")

    private suspend fun get(path: String): List<RadioStation> {
        var lastError: Exception? = null
        for (host in hosts) {
            try {
                val arr = XtreamApi.parseArray(Http.getString(host + path))
                return (0 until arr.length()).mapNotNull { i ->
                    val o = arr.optJSONObject(i) ?: return@mapNotNull null
                    val url = o.str("url_resolved") ?: o.str("url") ?: return@mapNotNull null
                    RadioStation(
                        id = o.str("stationuuid") ?: url,
                        name = o.str("name") ?: "Radio",
                        url = url,
                        logo = o.str("favicon"),
                        tags = o.str("tags")?.split(",")?.take(3)?.joinToString(" · "),
                        country = o.str("country"),
                    )
                }
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IOException("Brak połączenia z katalogiem radia")
    }
}
