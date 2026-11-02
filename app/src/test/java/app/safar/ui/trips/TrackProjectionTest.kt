package app.safar.ui.trips

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrackProjectionTest {
    @Test
    fun `a north-south track fills the height and is centred across`() {
        val p = projectTrack(listOf(18.50, 18.51), listOf(73.80, 73.80), width = 200f, height = 100f, pad = 10f)
        // North is up: the second (more northern) fix sits at the top.
        assertEquals(XY(100f, 90f), p.points[0])
        assertEquals(XY(100f, 10f), p.points[1])
        // 0.01 degree of latitude = 1,113.2 m over 80 px.
        assertEquals(80.0 / 1113.2, p.pxPerMetre, 1e-6)
    }

    @Test
    fun `east-west spans shrink with latitude`() {
        val atEquator = projectTrack(listOf(0.0, 0.0), listOf(10.0, 10.01), 100f, 100f, 0f).pxPerMetre
        val at60 = projectTrack(listOf(60.0, 60.0), listOf(10.0, 10.01), 100f, 100f, 0f).pxPerMetre
        assertEquals(2.0, at60 / atEquator, 1e-6)
    }

    @Test
    fun `a single fix lands in the centre and empty input projects nothing`() {
        val one = projectTrack(listOf(18.5), listOf(73.8), 120f, 96f, 10f)
        assertEquals(listOf(XY(60f, 48f)), one.points)
        assertEquals(0.0, one.pxPerMetre)
        assertTrue(projectTrack(emptyList(), emptyList(), 120f, 96f, 10f).points.isEmpty())
    }

    @Test
    fun `scale bar picks the longest round distance that fits`() {
        assertEquals(ScaleBar(1_000, 100f, "1 km"), scaleBar(0.1, 120f))
        assertEquals(ScaleBar(200, 40f, "200 m"), scaleBar(0.2, 50f))
        assertNull(scaleBar(0.0, 100f))
        assertNull(scaleBar(10.0, 50f))
    }

    @Test
    fun `thin keeps the ends and the requested count`() {
        val pts = (0..999).toList()
        val t = thin(pts, 100)
        assertEquals(100, t.size)
        assertEquals(0, t.first())
        assertEquals(999, t.last())
        assertEquals(pts.take(5), thin(pts.take(5), 100))
    }
}
