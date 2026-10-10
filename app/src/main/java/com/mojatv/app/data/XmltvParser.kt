package com.mojatv.app.data

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Strumieniowy parser XMLTV. Zachowuje tylko programy z okna czasowego
 * i tylko dla kanałów, które są w playliście (klucze w małych literach).
 */
object XmltvParser {

    fun parse(
        input: InputStream,
        wantedIds: Set<String>,
        fromMs: Long,
        toMs: Long,
    ): Map<String, List<EpgProgram>> {
        val withZone = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
        val noZone = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

        fun time(s: String?): Long? {
            if (s.isNullOrBlank()) return null
            val t = s.trim()
            return try {
                if (t.length > 15) withZone.parse(t)?.time else noZone.parse(t.take(14))?.time
            } catch (e: Exception) {
                null
            }
        }

        val result = HashMap<String, MutableList<EpgProgram>>()
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)

        var channel: String? = null
        var start = 0L
        var end = 0L
        var title: String? = null
        var desc: String? = null
        var inProgramme = false

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "programme" -> {
                        val ch = parser.getAttributeValue(null, "channel")?.lowercase()
                        val s = time(parser.getAttributeValue(null, "start"))
                        val e = time(parser.getAttributeValue(null, "stop"))
                        inProgramme = ch != null && s != null && e != null &&
                            ch in wantedIds && e > fromMs && s < toMs
                        if (inProgramme) {
                            channel = ch
                            start = s!!
                            end = e!!
                            title = null
                            desc = null
                        }
                    }
                    "title" -> if (inProgramme && title == null) title = safeText(parser)
                    "desc" -> if (inProgramme && desc == null) desc = safeText(parser)
                }
            } else if (event == XmlPullParser.END_TAG && parser.name == "programme") {
                if (inProgramme) {
                    val ch = channel
                    if (ch != null) {
                        result.getOrPut(ch) { ArrayList() }
                            .add(EpgProgram(title ?: "", desc, start, end))
                    }
                }
                inProgramme = false
            }
            event = parser.next()
        }
        return result.mapValues { (_, v) -> v.sortedBy { it.start } }
    }

    private fun safeText(parser: XmlPullParser): String? = try {
        parser.nextText()?.trim()
    } catch (e: Exception) {
        null
    }
}
