package com.carcam.platecheck.data

import android.content.Context
import com.carcam.platecheck.util.KoreanPlateRecognizer
import com.carcam.platecheck.util.PlateImporter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * @param db injectable so tests can run against an in-memory database. Tests must never be
 * able to reach the real one: a scale test that seeded and cleared tables would wipe the
 * user's imported resident list off their phone.
 */
class PlateRepository(
    context: Context,
    db: PlateDatabase = PlateDatabase.getInstance(context)
) {
    private val dao = db.plateDao()

    val allPlates = dao.getAllPlates()

    suspend fun addPlate(plateNumber: String, note: String) {
        val trimmed = plateNumber.trim()
        dao.insert(
            PlateEntity(
                plateNumber = trimmed,
                note = note.trim(),
                digitsKey = KoreanPlateRecognizer.digitsOnly(trimmed)
            )
        )
    }

    suspend fun deletePlate(plate: PlateEntity) {
        dao.delete(plate)
    }

    /**
     * Bulk import from a spreadsheet. Plates already on the list keep their existing row —
     * re-importing an updated resident list must not wipe notes that were added in the app,
     * and must not create duplicates.
     */
    suspend fun importPlates(rows: List<PlateImporter.Row>): ImportOutcome {
        // Only the numbers are needed to detect duplicates; pulling whole rows for a list of
        // several thousand is a lot of objects for a single set membership test.
        val existing = dao.getAllNumbers().toHashSet()
        val fresh = rows.filter { it.plate !in existing }
        if (fresh.isNotEmpty()) {
            dao.insertAll(
                fresh.map {
                    PlateEntity(
                        plateNumber = it.plate,
                        note = it.note,
                        digitsKey = KoreanPlateRecognizer.digitsOnly(it.plate)
                    )
                }
            )
        }
        return ImportOutcome(added = fresh.size, alreadyPresent = rows.size - fresh.size)
    }

    data class ImportOutcome(val added: Int, val alreadyPresent: Int)

    /**
     * Is this plate on the resident list?
     *
     * Both paths are indexed queries. That matters because this runs for every scanned frame
     * against a list that is routinely thousands of rows in an apartment complex: the previous
     * fallback read the entire table into memory and filtered it in Kotlin, so the cost of a
     * miss grew with the size of the list — and a miss is the normal case when the point of
     * the app is to spot cars that are *not* on it.
     */
    suspend fun checkPlate(plateNumber: String): PlateEntity? {
        val trimmed = plateNumber.trim()
        dao.findByNumber(trimmed)?.let { return it }

        // The usage glyph is what OCR gets wrong most often, so fall back to the digits. Both
        // readings are tried: the glyph may have been dropped entirely or read as a digit.
        backfillDigitsKeys()
        val keys = KoreanPlateRecognizer.candidateDigitKeys(trimmed)
        if (keys.first().length < 6) return null
        val matches = dao.findByDigitsKeys(keys).filter { entity ->
            // Same digits but both sides read a *valid* usage glyph and they differ: that is a
            // different car, not a misreading.
            KoreanPlateRecognizer.isMiddleCompatible(trimmed, entity.plateNumber)
        }
        return matches.singleOrNull()
    }

    /**
     * Fill in digitsKey for rows written before the column existed. Runs at most once per
     * process, and only touches rows that need it.
     */
    private suspend fun backfillDigitsKeys() {
        if (backfilled) return
        backfillLock.withLock {
            if (backfilled) return
            if (dao.countMissingDigitsKey() > 0) {
                for (entity in dao.getAllPlatesOnce()) {
                    if (entity.digitsKey.isEmpty()) {
                        dao.setDigitsKey(entity.id, KoreanPlateRecognizer.digitsOnly(entity.plateNumber))
                    }
                }
            }
            backfilled = true
        }
    }

    // Per instance, not per process: the database is injectable now, so a static flag set by
    // one database would skip the backfill for another.
    private val backfillLock = Mutex()
    @Volatile private var backfilled = false
}
