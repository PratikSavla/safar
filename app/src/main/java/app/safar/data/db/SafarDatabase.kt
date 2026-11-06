package app.safar.data.db

import android.content.Context
import androidx.room.AutoMigration
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
        DocumentEntity::class,
    ],
    version = 3,
    exportSchema = true,
    autoMigrations = [
        // Service items by time, and a first service that differs from the recurring one.
        AutoMigration(from = 1, to = 2),
        // The document table (bike papers).
        AutoMigration(from = 2, to = 3),
    ],
)
abstract class SafarDatabase : RoomDatabase() {
    abstract fun fuelUps(): FuelUpDao
    abstract fun trips(): TripDao
    abstract fun serviceItems(): ServiceItemDao
    abstract fun places(): PlaceDao
    abstract fun parkedSpot(): ParkedSpotDao
    abstract fun documents(): DocumentDao
    abstract fun backup(): BackupDao

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
