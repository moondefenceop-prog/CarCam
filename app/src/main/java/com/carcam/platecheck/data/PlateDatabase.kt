package com.carcam.platecheck.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [PlateEntity::class, VisitEntity::class], version = 2, exportSchema = false)
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

        fun getInstance(context: Context): PlateDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PlateDatabase::class.java,
                    "plate_db"
                ).addMigrations(MIGRATION_1_2).build().also { INSTANCE = it }
            }
    }
}
