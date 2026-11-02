package app.safar.protocol

/*
 * Maneuver from the Google Maps turn icon (docs 11.4). The instruction text changes with the phone
 * language; the icon does not. The app scales android.largeIcon to 9 x 8 grayscale pixels, then this
 * file computes a 64-bit difference hash (dHash) and finds the closest known icon.
 */

const val DHASH_WIDTH = 9
const val DHASH_HEIGHT = 8

/** Accept a match when the Hamming distance is this or less (docs 11.4). */
const val ICON_MATCH_MAX_DISTANCE = 6

/**
 * dHash of a 9 x 8 grayscale image, row-major, [DHASH_WIDTH] pixels per row, any brightness scale.
 * Bit 63 (most significant) is row 0, column 0; for each row the 8 bits say "this pixel is brighter
 * than its right-hand neighbour".
 */
fun dHash(gray: IntArray): Long {
    require(gray.size == DHASH_WIDTH * DHASH_HEIGHT) {
        "dHash needs ${DHASH_WIDTH * DHASH_HEIGHT} pixels (9 x 8), got ${gray.size}"
    }
    var hash = 0L
    for (y in 0 until DHASH_HEIGHT) {
        val row = y * DHASH_WIDTH
        for (x in 0 until DHASH_WIDTH - 1) {
            val bit = if (gray[row + x] > gray[row + x + 1]) 1L else 0L
            hash = (hash shl 1) or bit
        }
    }
    return hash
}

/** Number of bits that differ between two hashes (0..64). */
fun hammingDistance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

/** Hash as 16 lower-case hex digits, the key format of the JSON icon table asset. */
fun hashToHex(hash: Long): String = java.lang.Long.toHexString(hash).padStart(16, '0')

/** Parse a 1..16 digit hex hash written by [hashToHex]. */
fun hashFromHex(text: String): Long {
    require(text.length in 1..16) { "icon hash must be 1..16 hex digits, got \"$text\"" }
    return java.lang.Long.parseUnsignedLong(text, 16)
}

/** Closest table entry for a hash. */
data class IconMatch(val code: Int, val hash: Long, val distance: Int)

/**
 * Finds the cluster icon code for a turn-icon hash. [table] maps known hashes to cluster codes 1..45.
 * Ties on distance go to the lower hash value, so the result does not depend on map order.
 */
class IconMatcher(table: Map<Long, Int>, private val maxDistance: Int = ICON_MATCH_MAX_DISTANCE) {
    private val entries: List<Pair<Long, Int>> = table.entries.map { it.key to it.value }.sortedBy { it.first }

    init {
        require(maxDistance in 0..64) { "maxDistance must be 0..64, got $maxDistance" }
    }

    /** Best entry within [maxDistance], or null when no icon is close enough. */
    fun match(hash: Long): IconMatch? {
        var best: IconMatch? = null
        for ((known, code) in entries) {
            val distance = hammingDistance(hash, known)
            if (distance <= maxDistance && (best == null || distance < best.distance)) {
                best = IconMatch(code, known, distance)
            }
        }
        return best
    }

    /** Cluster icon code of the best match, or null. */
    fun codeFor(hash: Long): Int? = match(hash)?.code

    /** Cluster icon code for a 9 x 8 grayscale image, or null. */
    fun codeFor(gray: IntArray): Int? = codeFor(dHash(gray))
}
