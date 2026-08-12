package com.carcam.platecheck

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.Room
import com.carcam.platecheck.data.PlateDatabase
import com.carcam.platecheck.data.PlateRepository
import com.carcam.platecheck.util.PlateImporter
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The resident list comes from a spreadsheet and is routinely thousands of rows in an
 * apartment complex, and a lookup runs for every scanned frame. Crucially the *miss* is the
 * normal case — the app exists to spot cars that are not on the list — so the unmatched path
 * is the one that has to stay fast.
 */
@RunWith(AndroidJUnit4::class)
class LookupScaleTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val size = 5000

    // In-memory, never the app's own database: seeding and clearing the real one would delete
    // the resident list the user imported from their spreadsheet.
    private lateinit var db: PlateDatabase
    private lateinit var repository: PlateRepository

    @Before
    fun fillWithAResidentList() = runBlocking<Unit> {
        db = Room.inMemoryDatabaseBuilder(context, PlateDatabase::class.java)
            .allowMainThreadQueries().build()
        repository = PlateRepository(context, db)
        val glyphs = "가나다라마거너더러머버서어저고노도로모보소오조구누두루무부수우주아자바사하허호배"
        val rows = (0 until size).map { i ->
            val lead = 100 + (i % 900)
            val glyph = glyphs[i % glyphs.length]
            val tail = 1000 + (i % 9000)
            PlateImporter.Row("$lead$glyph$tail", "${i / 20 + 101}호")
        }.distinctBy { it.plate }
        repository.importPlates(rows)
    }

    @After
    fun closeDb() { db.close() }

    private fun timeMs(times: Int, block: suspend () -> Unit): Double = runBlocking {
        block()                                   // warm up; the first call backfills
        val start = System.nanoTime()
        repeat(times) { block() }
        (System.nanoTime() - start) / 1e6 / times
    }

    @Test
    fun lookupsStayFastOnAFullResidentList() = runBlocking {
        val registered = repository.checkPlate("100가1000")
        assertNotNull("the seeded list must be queryable", registered)

        val hit = timeMs(50) { repository.checkPlate("100가1000") }
        // A miss is the normal case here, and it is the path that used to read the whole table.
        val miss = timeMs(50) { repository.checkPlate("999하9999") }
        // Digits-only, i.e. OCR dropped the usage glyph: the indexed fallback.
        val digits = timeMs(50) { repository.checkPlate("9999999") }
        Log.i("LookupScale", "n=$size hit=${"%.2f".format(hit)}ms miss=${"%.2f".format(miss)}ms digits=${"%.2f".format(digits)}ms")

        // Frames are analysed about every 300ms, so a lookup costing tens of milliseconds
        // would show up as lag. These bounds are loose enough for a slow device but would fail
        // loudly if the query went back to scanning the table.
        assertTrue("registered lookup too slow: ${hit}ms", hit < 20.0)
        assertTrue("unregistered lookup too slow: ${miss}ms", miss < 20.0)
        assertTrue("digits-only lookup too slow: ${digits}ms", digits < 20.0)
    }

    @Test
    fun aMisreadGlyphStillMatchesByDigits() = runBlocking {
        // 가 misread as 하: same digits, and the guard only rejects when both readings are
        // valid *and* differ, so this must not match a different resident.
        val byDigits = repository.checkPlate("1001000")
        assertNotNull("dropping the glyph must still find the car", byDigits)
        assertEquals("100가1000", byDigits!!.plateNumber)
    }

    @Test
    fun reimportingDoesNotDuplicateOrLoseNotes() = runBlocking {
        val again = repository.importPlates(listOf(PlateImporter.Row("100가1000", "다른 메모")))
        assertEquals("existing residents must not be added twice", 0, again.added)
        assertEquals(1, again.alreadyPresent)
        assertEquals("메모는 앱에서 고친 것이 유지되어야 한다",
            "101호", repository.checkPlate("100가1000")!!.note)
    }
}
