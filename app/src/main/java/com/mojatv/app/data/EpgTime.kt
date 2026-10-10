package com.mojatv.app.data

import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Korekta godzin programu TV.
 *
 * Wielu dostawców podaje godziny programów jako „czas polski”, ale oznacza je jak czas uniwersalny
 * (albo ze stałym przesunięciem przez cały rok). Wtedy w Omanie wszystko jest przesunięte
 * o 1–2 godziny, a różnica zmienia się przy zmianie czasu w Polsce.
 *
 * Tryb POLISH bierze godzinę zapisaną w programie (np. „21:00”) i traktuje ją jako czas w Warszawie
 * w danym dniu — java.time sam uwzględnia czas letni i zimowy. Wynik to prawdziwy moment emisji,
 * który telefon pokazuje w swoim czasie (Oman) albo — jeśli wybrano — w czasie polskim.
 */
object EpgTime {
    enum class Mode { AUTO, POLISH }

    val WARSAW: ZoneId = ZoneId.of("Europe/Warsaw")
    private val WARSAW_TZ: TimeZone = TimeZone.getTimeZone("Europe/Warsaw")

    @Volatile var mode: Mode = Mode.AUTO
    @Volatile var shiftMinutes: Int = 0
    @Volatile var showPolishTime: Boolean = false

    fun configure(storage: Storage) {
        mode = if (storage.epgMode == "pl") Mode.POLISH else Mode.AUTO
        shiftMinutes = storage.epgShiftMinutes
        showPolishTime = storage.epgShowPolish
    }

    /** Program z poprawionymi godzinami (prawdziwy moment emisji). */
    fun adjust(p: EpgProgram): EpgProgram {
        if (mode == Mode.AUTO && shiftMinutes == 0) return p
        var start = p.start
        var end = p.end
        if (mode == Mode.POLISH) {
            start = fromWarsawWall(p.wallStart ?: p.start)
            end = fromWarsawWall(p.wallEnd ?: p.end)
        }
        val shift = shiftMinutes * 60_000L
        return p.copy(start = start + shift, end = end + shift)
    }

    fun adjustAll(list: List<EpgProgram>): List<EpgProgram> =
        if (mode == Mode.AUTO && shiftMinutes == 0) list else list.map { adjust(it) }

    /** „Godzina na zegarze” zapisana jako ms (cyfry czytane jak UTC) → prawdziwy moment w Polsce. */
    private fun fromWarsawWall(wallMs: Long): Long =
        LocalDateTime.ofEpochSecond(Math.floorDiv(wallMs, 1000L), 0, ZoneOffset.UTC)
            .atZone(WARSAW).toInstant().toEpochMilli()

    /** Godzina do wyświetlenia (czas telefonu albo czas polski). */
    fun hm(ms: Long): String {
        val f = SimpleDateFormat("HH:mm", Locale.getDefault())
        if (showPolishTime) f.timeZone = WARSAW_TZ
        return f.format(Date(ms))
    }

    /** Data i godzina do listy nagrań. */
    fun dayHm(ms: Long): String {
        val f = SimpleDateFormat("EEE d MMM, HH:mm", Locale("pl"))
        if (showPolishTime) f.timeZone = WARSAW_TZ
        return f.format(Date(ms))
    }

    private fun cal(ms: Long): java.util.Calendar =
        java.util.Calendar.getInstance(if (showPolishTime) WARSAW_TZ else TimeZone.getDefault()).apply { timeInMillis = ms }

    /** Klucz dnia (rok*1000+dzień roku) w wyświetlanej strefie. */
    fun dayKey(ms: Long): Int = cal(ms).let { it.get(java.util.Calendar.YEAR) * 1000 + it.get(java.util.Calendar.DAY_OF_YEAR) }

    /** „Dziś”, „Jutro” albo np. „wt., 13 paź”. */
    fun dayLabel(ms: Long): String {
        val now = System.currentTimeMillis()
        val k = dayKey(ms)
        if (k == dayKey(now)) return "Dziś"
        if (k == dayKey(now + 24 * 3600_000L)) return "Jutro"
        val f = SimpleDateFormat("EEEE, d MMMM", Locale("pl"))
        if (showPolishTime) f.timeZone = WARSAW_TZ
        return f.format(Date(ms)).replaceFirstChar { it.uppercase() }
    }

    fun zoneLabel(): String = if (showPolishTime) "czas polski" else "czas telefonu"

    /** Odczytuje „yyyy-MM-dd HH:mm:ss” jako cyfry zegara (bez strefy). */
    fun parseWall(s: String?): Long? {
        if (s.isNullOrBlank()) return null
        return try {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
                .parse(s.trim())?.time
        } catch (e: Exception) {
            null
        }
    }
}
