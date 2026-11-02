package app.safar.core

/**
 * Model facts by BLE name chars [1..4] (docs 7.1, 7.2). Tank sizes are model specs; the rider can
 * override them in Settings. Unknown codes fall back by family (name char [2]: 'S' scooter, else motorcycle).
 */
object BikeCatalog {
    private val SCOOTER_ALERT = 5..120
    private val MOTO_ALERT = 10..299

    private val models = listOf(
        BikeModel("AS01", "Access 125", VehicleFamily.SCOOTER, 5, SCOOTER_ALERT, 5.3),
        BikeModel("AS02", "Access 125", VehicleFamily.SCOOTER, 5, SCOOTER_ALERT, 5.3),
        BikeModel("AS11", "Burgman Street", VehicleFamily.SCOOTER, 5, SCOOTER_ALERT, 5.5),
        BikeModel("AS12", "Burgman Street", VehicleFamily.SCOOTER, 5, SCOOTER_ALERT, 5.5),
        BikeModel("BS11", "Burgman Street", VehicleFamily.SCOOTER, 5, SCOOTER_ALERT, 5.5),
        BikeModel("BS21", "Avenis", VehicleFamily.SCOOTER, 5, SCOOTER_ALERT, 5.2),
        BikeModel("BS31", "Burgman Street EX", VehicleFamily.SCOOTER, 5, SCOOTER_ALERT, 5.5),
        BikeModel("BM01", "V-Strom SX", VehicleFamily.MOTORCYCLE, 6, MOTO_ALERT, 12.0),
        BikeModel("BM11", "Gixxer / Gixxer SF", VehicleFamily.MOTORCYCLE, 6, MOTO_ALERT, 12.0),
        BikeModel("BM21", "Gixxer 250 / SF 250", VehicleFamily.MOTORCYCLE, 6, MOTO_ALERT, 12.0),
    ).associateBy { it.code }

    fun forBleName(bleName: String): BikeModel {
        val code = if (bleName.length >= 5) bleName.substring(1, 5) else bleName
        models[code]?.let { return it }
        val scooter = bleName.getOrNull(2) == 'S'
        return BikeModel(
            code = code,
            name = "Suzuki",
            family = if (scooter) VehicleFamily.SCOOTER else VehicleFamily.MOTORCYCLE,
            fuelBars = if (scooter) 5 else 6,
            alertRange = if (scooter) SCOOTER_ALERT else MOTO_ALERT,
            tankLitres = if (scooter) 5.0 else 12.0,
        )
    }
}
