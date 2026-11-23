package app.safar.data

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BikePhotosTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun store() = BikePhotos(File(tmp.root, "bike"))

    @Test
    fun aPhotoIsCopiedInAndOnlyTheKeptOneStays() {
        val photos = store()
        val first = photos.save(ByteArrayInputStream(byteArrayOf(1, 2, 3)), "image/jpeg")
        val second = photos.save(ByteArrayInputStream(byteArrayOf(4, 5)), "image/png")
        assertTrue(first.endsWith(".jpg") && second.endsWith(".png"))
        photos.keepOnly(second)
        assertFalse(photos.exists(first))
        assertTrue(photos.exists(second))
        assertEquals(2, photos.file(second).length())
        photos.keepOnly(null)
        assertFalse(photos.exists(second))
        assertFalse(photos.exists(null))
    }

    @Test
    fun aPdfOrUnknownTypeIsRefused() {
        val photos = store()
        assertEquals("Choose a photo.", assertFailsWith<DocumentRejected> { photos.save(ByteArrayInputStream(byteArrayOf(1)), "application/pdf") }.reason)
        assertFailsWith<DocumentRejected> { photos.save(ByteArrayInputStream(byteArrayOf(1)), null) }
        assertTrue(File(tmp.root, "bike").listFiles().isNullOrEmpty())
    }
}
