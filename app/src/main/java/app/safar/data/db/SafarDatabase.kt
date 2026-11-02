package app.safar.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** The one Room database. Get it with [get]; never build another instance. */
@Database(
    entities = [
        FuelUpEntity::class,
        TripEntity::class,
        TrackPointEntity::class,
        ServiceItemEntity::class,
        PlaceEntity::class,
        ParkedSpotEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class SafarDatabase : RoomDatabase() {
    abstract fun fuelUps(): FuelUpDao
    abstract fun trips(): TripDao
    abstract fun serviceItems(): ServiceItemDao
    abstract fun places(): PlaceDao
    abstract fun parkedSpot(): ParkedSpotDao

    companion object {
        private const val NAME = "safar.db"

        @Volatile
        private var instance: SafarDatabase? = null

        fun get(context: Context): SafarDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, SafarDatabase::class.java, NAME)
                    .build()
                    .also { instance = it }
            }
    }
}
