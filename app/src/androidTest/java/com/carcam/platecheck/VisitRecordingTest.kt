package com.carcam.platecheck

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.carcam.platecheck.data.PlateDatabase
import com.carcam.platecheck.data.PlateRepository
import com.carcam.platecheck.data.VisitRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The entry/exit rule runs unattended on a live camera feed, so its two dangerous behaviours
 * are pinned here: a car lingering at the barrier must not toggle in and out, and a real
 * return visit must not be swallowed by that same suppression.
 */
@RunWith(AndroidJUnit4::class)
class VisitRecordingTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var clock = 1_700_000_000_000L

    // In-memory, never the app's own database: clearing the real one would delete the
    // user's registered vehicles and their entry/exit history off their phone.
    private lateinit var db: PlateDatabase

    private fun repo(cooldownMs: Long = 90_000L) =
        VisitRepository(context, cooldownMs, { clock }, db)

    @Before
    fun openDb() {
        db = Room.inMemoryDatabaseBuilder(context, PlateDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After
    fun closeDb() { db.close() }

    @Test
    fun firstSightingIsAnEntryAndTheNextIsAnExit() = runBlocking {
        val r = repo()
        val entry = r.onPlateSeen("123가4567")
        assertNotNull(entry)
        assertEquals(VisitRepository.Kind.ENTRY, entry!!.kind)

        clock += 2 * 60 * 60 * 1000L                    // two hours later
        val exit = r.onPlateSeen("123가4567")
        assertNotNull(exit)
        assertEquals(VisitRepository.Kind.EXIT, exit!!.kind)
        assertEquals(2 * 60 * 60 * 1000L, exit.durationMs)
    }

    @Test
    fun repeatedSightingsWhileWaitingAtTheBarrierAreIgnored() = runBlocking {
        val r = repo()
        assertNotNull(r.onPlateSeen("123가4567"))
        // The camera reports the same plate several times a second; none of these may record.
        clock += 500
        assertNull(r.onPlateSeen("123가4567"))
        clock += 30_000
        assertNull(r.onPlateSeen("123가4567"))
        clock += 59_000                                  // still inside the 90s cooldown
        assertNull(r.onPlateSeen("123가4567"))
    }

    @Test
    fun aShortDropOffStillRecordsAnExit() = runBlocking {
        val r = repo()
        r.onPlateSeen("123가4567")
        clock += 91_000                                  // just past the cooldown
        val exit = r.onPlateSeen("123가4567")
        assertEquals(VisitRepository.Kind.EXIT, exit!!.kind)
    }

    @Test
    fun differentCarsDoNotShareACooldown() = runBlocking {
        val r = repo()
        assertNotNull(r.onPlateSeen("123가4567"))
        clock += 1_000
        val other = r.onPlateSeen("56너9876")
        assertNotNull("a second car arriving right behind must still record", other)
        assertEquals(VisitRepository.Kind.ENTRY, other!!.kind)
    }

    @Test
    fun undoRemovesTheRecordAndItsCooldown() = runBlocking {
        val r = repo()
        val entry = r.onPlateSeen("123가4567")!!
        r.undo(entry)
        // Undo must also clear the suppression, or the corrected re-scan is silently dropped.
        val again = r.onPlateSeen("123가4567")
        assertNotNull(again)
        assertEquals(VisitRepository.Kind.ENTRY, again!!.kind)
    }

    @Test
    fun undoingAnExitPutsTheCarBackInside() = runBlocking {
        val r = repo()
        r.onPlateSeen("123가4567")
        clock += 100_000
        val exit = r.onPlateSeen("123가4567")!!
        r.undo(exit)
        clock += 100_000
        val redo = r.onPlateSeen("123가4567")
        assertEquals(VisitRepository.Kind.EXIT, redo!!.kind)
    }

    @Test
    fun convertingAnEntryToAnExitLeavesTheEntryTimeUnknown() = runBlocking {
        val r = repo()
        val misread = r.onPlateSeen("123가4567")!!
        r.convertEntryToExit(misread)
        val all = r.allVisits().filter { it.canonicalPlate == "123가4567" }
        assertEquals(1, all.size)
        assertNull("entry time must not be invented", all[0].entryAt)
        assertNotNull(all[0].exitAt)
    }

    @Test
    fun aReturnVisitLaterTheSameDayIsASecondRecord() = runBlocking {
        val r = repo()
        r.onPlateSeen("123가4567")
        clock += 60 * 60 * 1000L
        r.onPlateSeen("123가4567")                       // exit
        clock += 60 * 60 * 1000L
        val back = r.onPlateSeen("123가4567")
        assertEquals(VisitRepository.Kind.ENTRY, back!!.kind)
        assertEquals(2, r.allVisits().count { it.canonicalPlate == "123가4567" })
    }

    @Test
    fun aRegisteredCarIsMatchedThroughItsDigitsWhenTheGlyphIsMisread() = runBlocking {
        PlateRepository(context, db).addPlate("154러7070", "테스트")
        val r = repo()
        // OCR dropping the usage glyph leaves a bare digit run; it must still be the same car.
        val entry = r.onPlateSeen("1547070")!!
        assertEquals("154러7070", entry.visit.canonicalPlate)
        clock += 100_000
        val exit = r.onPlateSeen("154러7070")
        assertEquals("a rescan that reads the glyph must close the same stay",
            VisitRepository.Kind.EXIT, exit!!.kind)
    }
}
