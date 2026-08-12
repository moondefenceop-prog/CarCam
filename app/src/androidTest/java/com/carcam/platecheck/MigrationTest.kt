package com.carcam.platecheck

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.carcam.platecheck.data.PlateDatabase
import com.carcam.platecheck.data.VisitEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The registered-vehicle list is the data users cannot recreate — often hundreds of rows
 * imported from a spreadsheet. An app update that drops it is unrecoverable for them, so the
 * 1→2 migration is exercised against a real version-1 database rather than trusted.
 *
 * Room also validates the migrated schema against the entities on open, so this fails if the
 * hand-written CREATE TABLE drifts from [VisitEntity] in column type, nullability or index.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbName = "migration_test_db"

    @Before
    fun removeOldFile() {
        context.deleteDatabase(dbName)
    }

    /** Build the schema exactly as version 1 shipped it. */
    private fun createVersion1Database() {
        val file = context.getDatabasePath(dbName)
        file.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `plates` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `plateNumber` TEXT NOT NULL,
                `note` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL)"""
        )
        db.execSQL("INSERT INTO plates (plateNumber, note, createdAt) VALUES ('154러7070', '301호', 1700000000000)")
        db.execSQL("INSERT INTO plates (plateNumber, note, createdAt) VALUES ('12가3456', '', 1700000001000)")
        db.version = 1
        db.close()
    }

    /** Build the schema as version 2 shipped it: version 1 plus the visits table. */
    private fun createVersion2Database() {
        createVersion1Database()
        val db = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(dbName), null)
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `visits` (
                `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                `plateNumber` TEXT NOT NULL,
                `canonicalPlate` TEXT NOT NULL,
                `isRegistered` INTEGER NOT NULL,
                `entryAt` INTEGER,
                `exitAt` INTEGER)"""
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_visits_canonicalPlate_exitAt` ON `visits` (`canonicalPlate`, `exitAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_visits_entryAt` ON `visits` (`entryAt`)")
        db.execSQL("INSERT INTO visits (plateNumber, canonicalPlate, isRegistered, entryAt, exitAt) " +
            "VALUES ('154러7070', '154러7070', 1, 1700000100000, NULL)")
        db.version = 2
        db.close()
    }

    private fun openWithMigration(): PlateDatabase =
        Room.databaseBuilder(context, PlateDatabase::class.java, dbName)
            .addMigrations(PlateDatabase.MIGRATION_1_2, PlateDatabase.MIGRATION_2_3)
            .build()

    @Test
    fun registeredVehiclesSurviveTheUpgrade() = runBlocking {
        createVersion1Database()
        val db = openWithMigration()
        try {
            val plates = db.plateDao().getAllPlatesOnce()
            assertEquals("every registered vehicle must survive", 2, plates.size)
            val kept = plates.first { it.plateNumber == "154러7070" }
            assertEquals("notes must survive too", "301호", kept.note)
            assertEquals(1700000000000L, kept.createdAt)
        } finally {
            db.close()
        }
    }

    @Test
    fun visitsTableIsUsableAfterTheUpgrade() = runBlocking {
        createVersion1Database()
        val db = openWithMigration()
        try {
            val dao = db.visitDao()
            val id = dao.insert(
                VisitEntity(
                    plateNumber = "154러7070",
                    canonicalPlate = "154러7070",
                    isRegistered = true,
                    entryAt = 1700000100000
                )
            )
            val open = dao.findOpenVisit("154러7070")
            assertNotNull("the new table must be queryable through its index", open)
            assertEquals(id, open!!.id)
            // Closing the stay must be readable back as a finished visit.
            dao.update(open.copy(exitAt = 1700000100000 + 3_600_000))
            assertEquals(null, dao.findOpenVisit("154러7070"))
        } finally {
            db.close()
        }
    }

    /**
     * The upgrade existing installs actually take. Version 2 is what is on phones today, and
     * version 3 adds the digits index that keeps lookups fast on a resident list of thousands.
     */
    @Test
    fun anInstalledVersion2DatabaseKeepsItsDataAndGainsTheIndex() = runBlocking {
        createVersion2Database()
        val db = openWithMigration()
        try {
            assertEquals("residents must survive", 2, db.plateDao().getAllPlatesOnce().size)
            val open = db.visitDao().findOpenVisit("154러7070")
            assertNotNull("an in-progress stay must survive", open)
            // The new column exists and its index is usable.
            assertEquals(2, db.plateDao().countMissingDigitsKey())
            assertEquals(0, db.plateDao().findByDigitsKeys(listOf("1547070")).size)
        } finally {
            db.close()
        }
    }

    @Test
    fun upgradingTwiceIsHarmless() = runBlocking {
        createVersion1Database()
        openWithMigration().let { it.plateDao().getAllPlatesOnce(); it.close() }
        // Reopening an already-migrated file must not attempt the migration again.
        val db = openWithMigration()
        try {
            assertEquals(2, db.plateDao().getAllPlatesOnce().size)
        } finally {
            db.close()
        }
    }
}
