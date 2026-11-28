package app.safar.phone

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Canvas
import android.os.SystemClock
import android.util.Log
import androidx.core.graphics.createBitmap
import app.safar.protocol.GOOGLE_MAPS_PACKAGE
import app.safar.protocol.MANEUVER_ANCHOR_DRAWABLE
import app.safar.protocol.MANEUVER_DRAWABLE_PREFIX
import app.safar.protocol.ManeuverMatch
import app.safar.protocol.ManeuverRef
import app.safar.protocol.alphaOf
import app.safar.protocol.bestManeuver

/** Entry bits of a resource id; the rest is package and type. */
private const val RESOURCE_ENTRY_MASK = 0xFFFF

/**
 * Google Maps' own turn icons (docs 11.4). Maps draws the notification's large icon from its `maneuver_*`
 * drawables, so this class loads them from the installed Maps, renders each at the live icon's size and
 * matches the live bitmap against them ([bestManeuver]). References are cached per size and per Maps
 * version (`lastUpdateTime`), so an updated Maps is re-read. Thread-safe; the first match at a new size
 * renders 64 small vector drawables (a few milliseconds).
 */
class MapsManeuverIcons(context: Context, private val log: PhoneEventLog) {
    private val app = context.applicationContext
    private val lock = Any()
    private var cacheVersion = -1L
    private val cache = HashMap<Long, List<ManeuverRef>>()

    /** Closest Maps drawable to the [argb] icon of [width] x [height], or null when Maps cannot be read. */
    fun match(argb: IntArray, width: Int, height: Int): ManeuverMatch? =
        bestManeuver(alphaOf(argb), refsFor(width, height))

    private fun refsFor(width: Int, height: Int): List<ManeuverRef> = synchronized(lock) {
        val version = mapsVersion() ?: return emptyList()
        if (version != cacheVersion) {
            cache.clear()
            cacheVersion = version
        }
        val key = (width.toLong() shl 32) or height.toLong()
        cache.getOrPut(key) { render(width, height) }
    }

    private fun mapsVersion(): Long? = try {
        app.packageManager.getPackageInfo(GOOGLE_MAPS_PACKAGE, 0).lastUpdateTime
    } catch (e: PackageManager.NameNotFoundException) {
        Log.w(TAG, "Google Maps is not installed; turn icons fall back to the instruction text", e)
        null
    }

    private fun render(width: Int, height: Int): List<ManeuverRef> {
        val started = SystemClock.elapsedRealtime()
        val res = try {
            app.packageManager.getResourcesForApplication(GOOGLE_MAPS_PACKAGE)
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "Google Maps resources not readable; turn icons fall back to the instruction text", e)
            return emptyList()
        }
        val refs = maneuverIds(res).mapNotNull { (name, id) -> renderOne(res, name, id, width, height) }
        val millis = SystemClock.elapsedRealtime() - started
        log.info("maps.icons.rendered", mapOf("count" to refs.size.toString(), "size" to "${width}x$height", "ms" to millis.toString()))
        if (refs.isEmpty()) log.error("maps.icons.none", mapOf("size" to "${width}x$height"))
        return refs
    }

    /** Every drawable named maneuver_*, found by walking the drawable type of [MANEUVER_ANCHOR_DRAWABLE]. */
    private fun maneuverIds(res: Resources): List<Pair<String, Int>> {
        val anchor = res.getIdentifier(MANEUVER_ANCHOR_DRAWABLE, "drawable", GOOGLE_MAPS_PACKAGE)
        if (anchor == 0) {
            Log.w(TAG, "Google Maps has no $MANEUVER_ANCHOR_DRAWABLE drawable")
            return emptyList()
        }
        val typeBase = anchor and RESOURCE_ENTRY_MASK.inv()
        val found = mutableListOf<Pair<String, Int>>()
        for (entry in 0..RESOURCE_ENTRY_MASK) {
            val id = typeBase or entry
            // Maps' resource table is shrunk and has gaps, so every entry of the type is tried.
            val name = try {
                res.getResourceEntryName(id)
            } catch (_: Resources.NotFoundException) {
                continue
            }
            if (name.startsWith(MANEUVER_DRAWABLE_PREFIX)) found += name to id
        }
        return found
    }

    private fun renderOne(res: Resources, name: String, id: Int, width: Int, height: Int): ManeuverRef? = try {
        val drawable = res.getDrawable(id, null)
        val bitmap = createBitmap(width, height)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(Canvas(bitmap))
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        bitmap.recycle()
        ManeuverRef(name, alphaOf(pixels))
    } catch (e: RuntimeException) {
        Log.w(TAG, "Google Maps drawable $name could not be rendered", e)
        null
    }
}
