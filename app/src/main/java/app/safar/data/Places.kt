package app.safar.data

// Saved places helpers, a port of ux/shared/places.js. No map SDK and no API key: Safar hands Google Maps
// a query (address text or "lat,lng") and never searches places itself.

/** A place parsed from text Google Maps shared to Safar. [link] is the maps URL, if any. */
data class SharedPlace(
    val label: String,
    val detail: String,
    val query: String,
    val link: String?,
    val source: PlaceSource = PlaceSource.MAPS,
)

data class LatLng(val lat: Double, val lng: Double)

private val HTTP_LINK = Regex("^https?://")
private val LAT_LNG = Regex("""^\s*(-?\d{1,3}(?:\.\d+)?)\s*,\s*(-?\d{1,3}(?:\.\d+)?)\s*$""")

/**
 * Parses text that Google Maps shares (ACTION_SEND, text/plain): first line = name, the first http(s) line =
 * link, the other lines = address. Returns null when nothing usable is in it.
 */
fun parseMapsShare(text: String?): SharedPlace? {
    val lines = (text ?: "").split("\n").map { it.trim() }.filter { it.isNotEmpty() }
    val link = lines.firstOrNull { HTTP_LINK.containsMatchIn(it) }
    val words = lines.filter { it != link }
    if (words.isEmpty() && link == null) return null
    val label = words.firstOrNull() ?: "Shared place"
    val detail = words.drop(1).joinToString(", ")
    val query = listOf(label, detail).filter { it.isNotEmpty() }.joinToString(", ")
    return SharedPlace(label, detail, query, link)
}

/** The intent URI that starts Google Maps navigation to [query] in two-wheeler mode. */
fun navUri(query: String): String = "google.navigation:q=${encodeUriComponent(query)}&mode=l"

/** Parses "10.5,70.25" (spaces allowed). Null when malformed or out of range. */
fun parseLatLng(text: String?): LatLng? {
    val m = LAT_LNG.matchEntire(text ?: return null) ?: return null
    val lat = m.groupValues[1].toDoubleOrNull() ?: return null
    val lng = m.groupValues[2].toDoubleOrNull() ?: return null
    if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
    return LatLng(lat, lng)
}

/** "lat,lng" query for Google Maps, 6 decimals (about 0.1 m). */
fun latLngQuery(lat: Double, lng: Double): String =
    String.format(java.util.Locale.US, "%.6f,%.6f", lat, lng)

private const val URI_UNRESERVED = "-_.!~*'()"
private const val HEX = "0123456789ABCDEF"

/** JavaScript `encodeURIComponent`: UTF-8, keeps A-Z a-z 0-9 and `-_.!~*'()`, escapes the rest as %XX. */
fun encodeUriComponent(text: String): String {
    val out = StringBuilder(text.length)
    for (b in text.toByteArray(Charsets.UTF_8)) {
        val c = b.toInt() and 0xFF
        val ch = c.toChar()
        if (c < 0x80 && (ch.isLetterOrDigit() || ch in URI_UNRESERVED)) {
            out.append(ch)
        } else {
            out.append('%').append(HEX[c shr 4]).append(HEX[c and 0x0F])
        }
    }
    return out.toString()
}
