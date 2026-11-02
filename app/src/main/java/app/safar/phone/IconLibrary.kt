package app.safar.phone

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import app.safar.protocol.IconMatch
import app.safar.protocol.IconMatcher
import app.safar.protocol.MAX_ICON_CODE
import app.safar.protocol.MIN_ICON_CODE
import app.safar.protocol.hashToHex
import java.io.File
import java.io.IOException

/** Shipped table (format in IconTable.kt). */
const val ICON_ASSET = "icons/maps-icons.json"
/** Learned table in `filesDir`, written by the future "learn icons" debug screen. */
const val ICON_LEARNED_FILE = "maps-icons-learned.json"

/**
 * Google Maps turn-icon hash table (docs 11.4): the shipped asset merged with the learned file, learned
 * entries winning. [load] and [learn] do file I/O: call them off the main thread. [match] is cheap and
 * thread-safe.
 */
class IconLibrary(context: Context, private val log: PhoneEventLog) {
    private val app = context.applicationContext
    private val learnedFile = AtomicFile(File(app.filesDir, ICON_LEARNED_FILE))
    private val lock = Any()

    @Volatile private var shipped: Map<Long, Int> = emptyMap()
    @Volatile private var learned: Map<Long, Int> = emptyMap()
    @Volatile private var matcher = IconMatcher(emptyMap())

    /** Learned entries only (hash -> code), for the debug screen. */
    val learnedTable: Map<Long, Int> get() = learned

    fun load() {
        val asset = readAsset()
        val saved = readLearned()
        synchronized(lock) {
            shipped = asset
            learned = saved
            rebuild()
        }
        log.info("maps.icons.loaded", mapOf("shipped" to asset.size.toString(), "learned" to saved.size.toString()))
    }

    fun match(hash: Long): IconMatch? = matcher.match(hash)

    /** Saves [hash] -> [code] in the learned table and uses it at once. */
    fun learn(hash: Long, code: Int) {
        require(code in MIN_ICON_CODE..MAX_ICON_CODE) { "icon code must be 1..45, got $code" }
        synchronized(lock) {
            learned = learned + (hash to code)
            rebuild()
            write(learned)
        }
        log.info("maps.icon.learned", mapOf("hash" to hashToHex(hash), "code" to code.toString()))
    }

    private fun rebuild() {
        matcher = IconMatcher(shipped + learned)
    }

    private fun readAsset(): Map<Long, Int> = try {
        parse(ICON_ASSET, app.assets.open(ICON_ASSET).bufferedReader().use { it.readText() })
    } catch (e: IOException) {
        Log.w(TAG, "Icon asset $ICON_ASSET not readable; matching uses learned icons only", e)
        emptyMap()
    }

    private fun readLearned(): Map<Long, Int> {
        if (!learnedFile.baseFile.exists()) return emptyMap()
        return try {
            parse(ICON_LEARNED_FILE, learnedFile.readFully().toString(Charsets.UTF_8))
        } catch (e: IOException) {
            Log.w(TAG, "Learned icons file not readable; starting empty", e)
            emptyMap()
        }
    }

    private fun parse(source: String, text: String): Map<Long, Int> {
        val parsed = parseIconTable(text)
        for (problem in parsed.errors) {
            Log.w(TAG, "$source: $problem")
            log.error("maps.icons.bad_entry", mapOf("file" to source, "problem" to problem))
        }
        return parsed.table
    }

    private fun write(table: Map<Long, Int>) {
        val out = try {
            learnedFile.startWrite()
        } catch (e: IOException) {
            Log.e(TAG, "Cannot open $ICON_LEARNED_FILE for writing; learned icon kept in memory only", e)
            log.error("maps.icons.save_failed", mapOf("reason" to (e.message ?: "open")))
            return
        }
        try {
            out.write(iconTableToJson(table).toByteArray(Charsets.UTF_8))
            learnedFile.finishWrite(out)
        } catch (e: IOException) {
            learnedFile.failWrite(out)
            Log.e(TAG, "Writing $ICON_LEARNED_FILE failed; learned icon kept in memory only", e)
            log.error("maps.icons.save_failed", mapOf("reason" to (e.message ?: "write")))
        }
    }
}
