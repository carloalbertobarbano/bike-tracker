package app.pedal.gpx

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream
import java.io.Writer
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Locale
import javax.xml.parsers.SAXParserFactory

data class GpxPoint(val lat: Double, val lon: Double, val ele: Double?, val time: Long?)

data class GpxTrack(val name: String?, val points: List<GpxPoint>)

class GpxException(message: String) : Exception(message)

/** Streaming GPX reader. Uses SAX so it handles big files and runs in plain JVM unit tests. */
object GpxParser {

    fun parse(input: InputStream): GpxTrack {
        val factory = SAXParserFactory.newInstance().apply { isNamespaceAware = true }
        // Harden against XXE; not every parser implementation supports every feature.
        runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        val handler = Handler()
        try {
            factory.newSAXParser().parse(input, handler)
        } catch (e: Exception) {
            throw GpxException("This file doesn't look like a valid GPX file")
        }
        val points = when {
            handler.trk.size >= 2 -> handler.trk
            handler.rte.size >= 2 -> handler.rte
            else -> handler.wpt
        }
        if (points.size < 2) throw GpxException("No track or route found in this file")
        return GpxTrack(handler.name, points)
    }

    private class Handler : DefaultHandler() {
        val trk = ArrayList<GpxPoint>()
        val rte = ArrayList<GpxPoint>()
        val wpt = ArrayList<GpxPoint>()
        var name: String? = null

        private val stack = ArrayDeque<String>()
        private val text = StringBuilder()
        private var pointTag: String? = null
        private var lat = Double.NaN
        private var lon = Double.NaN
        private var ele: Double? = null
        private var time: Long? = null

        private fun tag(localName: String?, qName: String?): String =
            localName?.takeIf { it.isNotEmpty() } ?: qName.orEmpty().substringAfter(':')

        override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
            val t = tag(localName, qName)
            stack.addLast(t)
            text.setLength(0)
            if (t == "trkpt" || t == "rtept" || t == "wpt") {
                pointTag = t
                lat = attributes.getValue("lat")?.trim()?.toDoubleOrNull() ?: Double.NaN
                lon = attributes.getValue("lon")?.trim()?.toDoubleOrNull() ?: Double.NaN
                ele = null
                time = null
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            text.append(ch, start, length)
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            val t = tag(localName, qName)
            stack.removeLastOrNull()
            val parent = stack.lastOrNull()
            val value = text.toString().trim()
            when {
                pointTag != null && t == "ele" -> ele = value.toDoubleOrNull()
                pointTag != null && t == "time" -> time = parseTime(value)
                t == pointTag -> {
                    if (!lat.isNaN() && !lon.isNaN() && lat in -90.0..90.0 && lon in -180.0..180.0) {
                        val p = GpxPoint(lat, lon, ele, time)
                        when (t) {
                            "trkpt" -> trk.add(p)
                            "rtept" -> rte.add(p)
                            else -> wpt.add(p)
                        }
                    }
                    pointTag = null
                }
                t == "name" && name == null && value.isNotEmpty() &&
                    (parent == "metadata" || parent == "trk" || parent == "rte") -> name = value
            }
            text.setLength(0)
        }

        private fun parseTime(s: String): Long? =
            runCatching { Instant.parse(s).toEpochMilli() }.getOrNull()
                ?: runCatching { OffsetDateTime.parse(s).toInstant().toEpochMilli() }.getOrNull()
    }
}

data class GpxOutPoint(val lat: Double, val lon: Double, val ele: Double?, val time: Long?)

object GpxWriter {
    fun write(out: Writer, name: String, time: Long?, segments: List<List<GpxOutPoint>>) {
        out.write("""<?xml version="1.0" encoding="UTF-8"?>""")
        out.write("\n<gpx version=\"1.1\" creator=\"Pedal\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        out.write("  <metadata>\n    <name>${escape(name)}</name>\n")
        if (time != null) out.write("    <time>${Instant.ofEpochMilli(time)}</time>\n")
        out.write("  </metadata>\n  <trk>\n    <name>${escape(name)}</name>\n    <type>cycling</type>\n")
        for (seg in segments) {
            if (seg.isEmpty()) continue
            out.write("    <trkseg>\n")
            for (p in seg) {
                out.write(String.format(Locale.US, "      <trkpt lat=\"%.7f\" lon=\"%.7f\">", p.lat, p.lon))
                if (p.ele != null) out.write(String.format(Locale.US, "<ele>%.1f</ele>", p.ele))
                if (p.time != null) out.write("<time>${Instant.ofEpochMilli(p.time)}</time>")
                out.write("</trkpt>\n")
            }
            out.write("    </trkseg>\n")
        }
        out.write("  </trk>\n</gpx>\n")
        out.flush()
    }

    private fun escape(s: String) = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
