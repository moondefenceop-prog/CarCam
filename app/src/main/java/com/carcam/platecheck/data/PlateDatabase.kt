package com.carcam.platecheck.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [PlateEntity::class, VisitEntity::class], version = 3, exportSchema = false)
abstract class PlateDatabase : RoomDatabase() {
    abstract fun plateDao(): PlateDao
    abstract fun visitDao(): VisitDao

    companion object {
        @Volatile private var INSTANCE: PlateDatabase? = null

        /**
         * Adds the visits table. Written out rather than falling back to a destructive
         * migration: the registered-vehicle list is the data users care about most — often
         * hundreds of rows imported from a spreadsheet — and dropping it on an app update
         * would be unrecoverable for them.
         */
        @androidx.annotation.VisibleForTesting
        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `visits` (
                        `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        `plateNumber` TEXT NOT NULL,
                        `canonicalPlate` TEXT NOT NULL,
                        `isRegistered` INTEGER NOT NULL,
                        `entryAt` INTEGER,
                        `exitAt` INTEGER)"""
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_visits_canonicalPlate_exitAt` " +
                        "ON `visits` (`canonicalPlate`, `exitAt`)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_visits_entryAt` ON `visits` (`entryAt`)")
            }
        }

        /**
         * Indexes the plate list for scale. A resident list of a few thousand is normal, and
         * every scanned frame queries it, so both lookup paths must be index hits. `digitsKey`
         * is added empty here and backfilled from Kotlin — SQLite has no way to strip the
         * non-digits out of a Hangul plate number.
         */
        @androidx.annotation.VisibleForTesting
        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `plates` ADD COLUMN `digitsKey` TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_plates_plateNumber` ON `plates` (`plateNumber`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_plates_digitsKey` ON `plates` (`digitsKey`)")
            }
        }

        fun getInstance(context: Context): PlateDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PlateDatabase::class.java,
                    "plate_db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { INSTANCE = it }
            }
    }
}
