package io.homedeck.grid.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.homedeck.grid.support.Converters
import io.homedeck.grid.contracts.LaunchableAppDao
import io.homedeck.grid.contracts.AppOpenStatDao
import io.homedeck.grid.contracts.ConcealedAppDao
import io.homedeck.grid.contracts.DeskGridEntryDao
import io.homedeck.grid.entities.LaunchableApp
import io.homedeck.grid.entities.AppOpenStat
import io.homedeck.grid.entities.ConcealedApp
import io.homedeck.grid.entities.DeskGridEntry

@Database(
    entities = [LaunchableApp::class, DeskGridEntry::class, ConcealedApp::class, AppOpenStat::class],
    version = 6
)
@TypeConverters(Converters::class)
abstract class HomeGridDatabase : RoomDatabase() {

    abstract fun LaunchableAppDao(): LaunchableAppDao

    abstract fun DeskGridEntryDao(): DeskGridEntryDao

    abstract fun ConcealedAppDao(): ConcealedAppDao

    abstract fun AppOpenStatDao(): AppOpenStatDao

    companion object {
        private var db: HomeGridDatabase? = null

        /** The launcher's own launch log for the search's suggested apps. */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `app_usage` (`package_name` TEXT NOT NULL, `activity_name` TEXT NOT NULL, " +
                        "`launch_count` INTEGER NOT NULL, `last_launched` INTEGER NOT NULL, PRIMARY KEY(`package_name`))"
                )
            }
        }

        fun getInstance(context: Context): HomeGridDatabase {
            if (db == null) {
                synchronized(HomeGridDatabase::class) {
                    if (db == null) {
                        db = Room.databaseBuilder(
                            context.applicationContext,
                            HomeGridDatabase::class.java,
                            "apps.db"
                        ).addMigrations(MIGRATION_5_6).build()
                    }
                }
            }
            return db!!
        }
    }
}
