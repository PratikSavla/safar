package app.safar.ui.trips

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrackLabelsTest {
    // A 300 x 200 map; labels 12 px tall; START 40 px, END 24 px and "START · END" 80 px wide.
    private val spec = EndLabelSpec(
        start = LabelSize(40f, 12f),
        end = LabelSize(24f, 12f),
        merged = LabelSize(80f, 12f),
        gap = 10f,
        mergeWithin = 16f,
        markerHalf = 7f,
    )

    private fun place(track: List<XY>) = assertNotNull(placeEndLabels(track, spec, width = 300f, height = 200f))

    /** A ride that leaves [start] straight down, runs along the bottom and comes up to [end]: clear of side labels. */
    private fun ride(start: XY, end: XY) = listOf(start, XY(start.x, 190f), XY(end.x, 190f), end)

    @Test
    fun `far-apart ends keep their markers and get labels to their right`() {
        val ends = place(ride(XY(50f, 100f), XY(250f, 50f)))
        assertEquals(XY(50f, 100f), ends.startMarker)
        assertEquals(XY(250f, 50f), ends.endMarker)
        assertEquals(LabelBox(60f, 94f, 40f, 12f), ends.start)
        assertEquals(LabelBox(260f, 44f, 24f, 12f), ends.end)
    }

    @Test
    fun `END moves left when START already holds its right side`() {
        val ends = place(ride(XY(100f, 100f), XY(80f, 104f)))
        assertEquals(LabelBox(110f, 94f, 40f, 12f), ends.start)
        val end = assertNotNull(ends.end)
        assertEquals(LabelBox(46f, 98f, 24f, 12f), end)
        assertFalse(end.overlaps(ends.start))
    }

    @Test
    fun `START moves left when the END marker sits just to its right`() {
        val ends = place(ride(XY(100f, 100f), XY(130f, 103f)))
        assertEquals(LabelBox(50f, 94f, 40f, 12f), ends.start)
        assertEquals(LabelBox(140f, 97f, 24f, 12f), ends.end)
    }

    @Test
    fun `a ride that ends where it began gets one label and markers side by side`() {
        val ends = place(ride(XY(150f, 100f), XY(155f, 104f)))
        assertNull(ends.end)
        assertEquals(LabelBox(172f, 96f, 80f, 12f), ends.start)
        // Middle at (152.5, 102); markers 9.5 px either side of it, start on the left.
        assertEquals(XY(143f, 102f), ends.startMarker)
        assertEquals(XY(162f, 102f), ends.endMarker)
    }

    @Test
    fun `a loop finishing west of its start puts the start marker on the right`() {
        val ends = place(ride(XY(150f, 100f), XY(140f, 100f)))
        assertNull(ends.end)
        assertEquals(XY(154.5f, 100f), ends.startMarker)
        assertEquals(XY(135.5f, 100f), ends.endMarker)
    }

    @Test
    fun `a label with no room on the right flips to the left instead of covering its marker`() {
        val ends = place(ride(XY(290f, 100f), XY(100f, 100f)))
        assertEquals(LabelBox(240f, 94f, 40f, 12f), ends.start)
        assertEquals(LabelBox(110f, 94f, 24f, 12f), ends.end)
    }

    @Test
    fun `a label moves off the route when the route runs through its first choice`() {
        // The ride heads east from the start, right through where START would go.
        val track = listOf(XY(100f, 100f), XY(160f, 100f), XY(160f, 190f), XY(250f, 190f), XY(250f, 150f))
        assertEquals(LabelBox(50f, 94f, 40f, 12f), place(track).start)
    }

    @Test
    fun `when the route crosses all four sides the label takes a free corner`() {
        // A cross through the start: east-west and north-south lines block right, left, above and below.
        val track = listOf(XY(100f, 100f), XY(200f, 100f), XY(0f, 100f), XY(100f, 100f), XY(100f, 0f), XY(100f, 199f))
        assertEquals(LabelBox(110f, 78f, 40f, 12f), place(track).start)
    }

    @Test
    fun `when every spot touches the route the label takes the least crossed one`() {
        // Crossings per spot: right 2, left 2, below 2, lower-left 2; above, upper-right, upper-left, lower-right 1.
        val track = listOf(
            XY(100f, 100f), XY(300f, 100f), XY(300f, 84f), XY(0f, 84f), XY(0f, 100f), XY(60f, 100f),
            XY(60f, 116f), XY(300f, 116f), XY(300f, 98f), XY(105f, 98f), XY(105f, 190f), XY(250f, 190f),
        )
        // The least crossed, earliest in order: above.
        assertEquals(LabelBox(80f, 78f, 40f, 12f), place(track).start)
    }

    @Test
    fun `an empty track has nothing to label`() {
        assertNull(placeEndLabels(emptyList(), spec, 300f, 200f))
    }

    @Test
    fun `boxes that only touch do not overlap, and a line through a box crosses it`() {
        val a = LabelBox(0f, 0f, 10f, 10f)
        assertFalse(a.overlaps(LabelBox(10f, 0f, 10f, 10f)))
        assertFalse(a.overlaps(LabelBox(0f, 10f, 10f, 10f)))
        assertTrue(a.overlaps(LabelBox(9f, 9f, 10f, 10f)))
        assertTrue(a.crossedBy(XY(-5f, 5f), XY(15f, 5f)))
        assertFalse(a.crossedBy(XY(-5f, 15f), XY(15f, 15f)))
    }
}
