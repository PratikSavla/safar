package app.safar.ui.art

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.safar.ui.theme.Atlas
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * A cluster turn icon for code 1..46 (docs 4.4), drawn in ink on a 64 grid with a 6 stroke
 * (art.js `turnIcon()`, extended to every code). Right-hand shapes mirror the left ones.
 */
@Composable
fun TurnArrow(
    code: Int,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    color: Color = Atlas.colors.ink,
    contentDescription: String? = TurnCodes.spec(code).name,
) {
    Image(
        imageVector = TurnArrows.vector(code),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        colorFilter = ColorFilter.tint(color),
    )
}

object TurnArrows {
    private const val FAINT = 0.35f
    private val cache = ConcurrentHashMap<Int, ImageVector>()

    fun vector(code: Int): ImageVector = cache.getOrPut(code) { build(TurnCodes.spec(code)) }

    private fun build(spec: TurnSpec): ImageVector = lineArt("turn.${spec.code}", w = 64f, h = 64f, stroke = 6f) {
        when (spec.family) {
            TurnFamily.ROUNDABOUT_EXIT -> roundaboutExit(spec.angle)
            TurnFamily.ROUNDABOUT -> roundabout()
            TurnFamily.START -> start(spec.angle)
            else -> group(mirror = spec.side == TurnSide.RIGHT, pivotX = 32f, pivotY = 32f) { leftShape(spec.family) }
        }
    }

    private fun LineArtScope.leftShape(family: TurnFamily) {
        when (family) {
            TurnFamily.TURN -> path("M40 58V34Q40 22 28 22H10" + head(10f, 22f, -90f))
            TurnFamily.SHARP -> path("M42 58V18L14 46M14 32V46H28")
            TurnFamily.SLIGHT -> path("M40 58V36L20 16" + head(20f, 16f, -45f))
            TurnFamily.UTURN -> path("M44 58V26a12 12 0 0 0-24 0v22" + head(20f, 50f, 180f))
            TurnFamily.ARRIVE -> { path("M20 58V8"); path("M20 10h26l-6 10 6 10H20", width = 5f) }
            TurnFamily.KEEP_LANE -> keepLeft()
            TurnFamily.KEEP_LANE_ALT -> { keepLeft(); path("M53 12v8M53 28v8M53 44v8", width = 3f, alpha = 0.45f) }
            TurnFamily.SIDE_ROAD -> { path("M38 32V8", alpha = FAINT); path("M38 58V30Q38 24 32 24H12" + head(12f, 24f, -90f)) }
            TurnFamily.END_OF_ROAD -> { path("M32 22H56", alpha = FAINT); path("M32 58V30Q32 22 24 22H10" + head(10f, 22f, -90f)) }
            TurnFamily.SLIP_ROAD -> { path("M40 46V8", alpha = FAINT); path("M40 58V46C40 34 22 36 22 22V12" + head(22f, 12f, 0f)) }
            TurnFamily.RAMP_BACK -> { path("M42 30V8", alpha = FAINT); path("M42 58V30C42 14 18 14 18 30V42" + head(18f, 42f, 180f)) }
            TurnFamily.FORK -> { path("M32 38L46 18", alpha = FAINT); path("M32 58V38L18 18" + headAlong(32f, 38f, 18f, 18f)) }
            TurnFamily.BEND_SHARP -> path("M42 58V38C42 20 26 14 14 28" + headAlong(26f, 14f, 14f, 28f))
            TurnFamily.BEND_SLIGHT -> path("M38 58V42C38 28 30 20 22 10" + headAlong(30f, 20f, 22f, 10f))
            TurnFamily.CROSS -> { path("M8 34H56", alpha = FAINT); straight() }
            TurnFamily.FERRY -> ferry()
            TurnFamily.STATUS -> { circle(32f, 32f, 24f, width = 5f); path("M32 18v18"); circle(32f, 46f, 3.5f, filled = true, width = 1f) }
            TurnFamily.UNKNOWN -> path("M32 58V8" + head(32f, 8f, 0f), alpha = 0.45f)
            else -> straight()
        }
    }

    private fun LineArtScope.straight() = path("M32 58V8" + head(32f, 8f, 0f))

    private fun LineArtScope.keepLeft() {
        path("M40 44V8", alpha = FAINT)
        path("M40 58V44L24 28V10" + head(24f, 10f, 0f))
    }

    private fun LineArtScope.ferry() {
        path("M10 34h44l-8 12H18z")
        path("M22 34V24h18v10")
        path("M31 24V14")
        path("M8 55c4-4 8-4 12 0s8 4 12 0 8-4 12 0 8 4 12 0", width = 4f)
    }

    /** Clockwise roundabout (left-hand traffic): entry from below, bold arc to the exit at [angle]. */
    private fun LineArtScope.roundaboutExit(angle: Float) {
        val cx = 32f
        val cy = 28f
        val r = 10f
        circle(cx, cy, r, width = 4f, alpha = FAINT)
        val sweep = ((angle + 180f) % 360f + 360f) % 360f
        val arcSweep = if (sweep == 0f) 360f else sweep
        val (ex, ey) = polar(cx, cy, r, angle)
        val (ox, oy) = polar(cx, cy, 24f, angle)
        val large = if (arcSweep > 180f) 1 else 0
        path("M32 58V${cy + r}A$r $r 0 $large 1 ${n(ex)} ${n(ey)}L${n(ox)} ${n(oy)}" + head(ox, oy, angle))
    }

    private fun LineArtScope.roundabout() {
        circle(32f, 28f, 11f)
        path("M32 58V39")
        path(head(21f, 28f, 0f, len = 8f))
    }

    /** Start icon: a dot and an arrow pointing to the heading, inside a faint compass ring. */
    private fun LineArtScope.start(angle: Float) {
        circle(32f, 32f, 27f, width = 2.5f, alpha = 0.3f)
        path("M32 2.5v5", width = 3f, alpha = 0.6f)
        group(rotate = angle, pivotX = 32f, pivotY = 32f) {
            circle(32f, 46f, 3.5f, filled = true, width = 3f)
            path("M32 46V14" + head(32f, 14f, 0f))
        }
    }

    /** Point at radius [r] around ([cx],[cy]), [deg] clockwise from up. */
    private fun polar(cx: Float, cy: Float, r: Float, deg: Float): Pair<Float, Float> {
        val a = Math.toRadians(deg.toDouble())
        return Pair((cx + r * sin(a)).toFloat(), (cy - r * cos(a)).toFloat())
    }

    /** Arrowhead arms meeting at the tip, pointing [deg] clockwise from up. */
    internal fun head(x: Float, y: Float, deg: Float, len: Float = 12f): String {
        val (ax, ay) = polar(x, y, len, deg + 180f - 45f)
        val (bx, by) = polar(x, y, len, deg + 180f + 45f)
        return "M${n(ax)} ${n(ay)}L${n(x)} ${n(y)}L${n(bx)} ${n(by)}"
    }

    /** Arrowhead at ([tx],[ty]) along the direction coming from ([fx],[fy]). */
    private fun headAlong(fx: Float, fy: Float, tx: Float, ty: Float): String {
        val deg = Math.toDegrees(atan2((tx - fx).toDouble(), (fy - ty).toDouble())).toFloat()
        return head(tx, ty, deg)
    }

    private fun n(v: Float): String = String.format(Locale.ROOT, "%.2f", v)
}
