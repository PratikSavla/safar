package app.safar.phone

import app.safar.protocol.DHASH_HEIGHT
import app.safar.protocol.DHASH_WIDTH
import app.safar.protocol.MAX_ICON_CODE
import app.safar.protocol.MIN_ICON_CODE
import app.safar.protocol.dHash
import app.safar.protocol.hashFromHex
import app.safar.protocol.hashToHex
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/*
 * Turn-icon hash table (docs 11.4). Pure Kotlin: no Android imports.
 *
 * File format, for both `assets/icons/maps-icons.json` (shipped, starts as `{}`) and the learned table in
 * `filesDir/maps-icons-learned.json`: one JSON object, key = the 64-bit dHash of the Google Maps large icon
 * as 16 lower-case hex digits (protocol hashToHex), value = the cluster icon code 1..45 (docs 4.4). Example:
 *
 *   { "f0e0c0c0e0f0f8fc": 4, "0f070303070f1f3f": 1 }
 *
 * Learned entries win over shipped ones with the same key. Bad entries are skipped and reported.
 */

/** Parsed table plus one line per skipped entry. */
data class IconTableParse(val table: Map<Long, Int>, val errors: List<String>)

private val json = Json { prettyPrint = true }

fun parseIconTable(text: String): IconTableParse {
    if (text.isBlank()) return IconTableParse(emptyMap(), emptyList())
    val root: JsonObject = try {
        Json.parseToJsonElement(text).jsonObject
    } catch (e: SerializationException) {
        return IconTableParse(emptyMap(), listOf("not JSON: ${e.message}"))
    } catch (e: IllegalArgumentException) {
        return IconTableParse(emptyMap(), listOf("not a JSON object: ${e.message}"))
    }
    val table = LinkedHashMap<Long, Int>()
    val errors = mutableListOf<String>()
    for ((key, value) in root) {
        val code = (value as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
        val hash = runCatching { hashFromHex(key) }.getOrNull()
        when {
            hash == null -> errors += "bad hash \"$key\""
            code == null || code !in MIN_ICON_CODE..MAX_ICON_CODE -> errors += "bad code for $key: $value"
            else -> table[hash] = code
        }
    }
    return IconTableParse(table, errors)
}

/** Table as JSON in the format above, keys sorted so the file diffs well. */
fun iconTableToJson(table: Map<Long, Int>): String {
    val sorted = table.entries.sortedBy { hashToHex(it.key) }
    return json.encodeToString(JsonObject.serializer(), JsonObject(sorted.associate { hashToHex(it.key) to JsonPrimitive(it.value) }))
}

/**
 * Scale an ARGB_8888 image to 9 x 8 grayscale for [dHash] (docs 11.4). Each output pixel is the mean of its
 * source box. Transparent pixels count as black, so a white arrow on a transparent background keeps its shape.
 */
fun grayscale9x8(argb: IntArray, width: Int, height: Int): IntArray {
    require(width > 0 && height > 0 && argb.size >= width * height) { "image is ${width}x$height, ${argb.size} pixels" }
    val out = IntArray(DHASH_WIDTH * DHASH_HEIGHT)
    for (oy in 0 until DHASH_HEIGHT) {
        val y0 = oy * height / DHASH_HEIGHT
        val y1 = maxOf(y0 + 1, (oy + 1) * height / DHASH_HEIGHT)
        for (ox in 0 until DHASH_WIDTH) {
            val x0 = ox * width / DHASH_WIDTH
            val x1 = maxOf(x0 + 1, (ox + 1) * width / DHASH_WIDTH)
            out[oy * DHASH_WIDTH + ox] = boxMean(argb, width, x0, x1, y0, y1)
        }
    }
    return out
}

private fun boxMean(argb: IntArray, width: Int, x0: Int, x1: Int, y0: Int, y1: Int): Int {
    var sum = 0L
    for (y in y0 until y1) {
        for (x in x0 until x1) sum += luminance(argb[y * width + x])
    }
    return (sum / ((x1 - x0) * (y1 - y0))).toInt()
}

/** 0..255 luma of one ARGB pixel, scaled by alpha. */
fun luminance(pixel: Int): Int {
    val a = pixel ushr 24 and 0xFF
    val r = pixel ushr 16 and 0xFF
    val g = pixel ushr 8 and 0xFF
    val b = pixel and 0xFF
    val luma = (299 * r + 587 * g + 114 * b) / 1000
    return luma * a / 255
}

/** dHash of a full-size ARGB icon. */
fun iconHash(argb: IntArray, width: Int, height: Int): Long = dHash(grayscale9x8(argb, width, height))
