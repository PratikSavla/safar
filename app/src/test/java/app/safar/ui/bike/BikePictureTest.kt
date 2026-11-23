package app.safar.ui.bike

import app.safar.core.BikeCatalog
import app.safar.core.BikeStyle
import app.safar.data.Settings
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BikePictureTest {
    private val vstrom = BikeCatalog.forBleName("SBM01A1B2C3D")

    @Test
    fun `each catalogue model has its own drawing and unknown codes fall back by family`() {
        assertEquals(BikeStyle.ADVENTURE, vstrom.style)
        assertEquals(BikeStyle.STANDARD, BikeCatalog.forBleName("SBM11A1B2C3D").style)
        assertEquals(BikeStyle.SPORT, BikeCatalog.forBleName("SCM21A1B2C3D").style)
        assertEquals(BikeStyle.SCOOTER, BikeCatalog.forBleName("SAS01A1B2C3D").style)
        assertEquals(BikeStyle.MAXI_SCOOTER, BikeCatalog.forBleName("SBS31A1B2C3D").style)
        assertEquals(BikeStyle.SPORT_SCOOTER, BikeCatalog.forBleName("SCS21A1B2C3D").style)
        assertEquals(BikeStyle.SCOOTER, BikeCatalog.forBleName("SZS99A1B2C3D").style)
        assertEquals(BikeStyle.STANDARD, BikeCatalog.forBleName("SZM99A1B2C3D").style)
    }

    @Test
    fun `the picture follows the model until the rider chooses`() {
        assertEquals(BikePictureUi(BikeStyle.ADVENTURE, BikeStyle.ADVENTURE, null, null), bikePicture(vstrom, Settings()))
        val chosen = bikePicture(vstrom, Settings(bikeStyle = BikeStyle.SPORT, bikePhoto = "a.jpg"))
        assertEquals(BikePictureUi(BikeStyle.SPORT, BikeStyle.ADVENTURE, BikeStyle.SPORT, "a.jpg"), chosen)
        assertEquals(BikeStyle.STANDARD, bikePicture(null, Settings()).style)
    }

    @Test
    fun `choosing the model's own drawing clears the choice`() {
        val picture = bikePicture(vstrom, Settings(bikeStyle = BikeStyle.SPORT))
        assertNull(styleChoice(picture, BikeStyle.ADVENTURE))
        assertEquals(BikeStyle.SCOOTER, styleChoice(picture, BikeStyle.SCOOTER))
    }
}
