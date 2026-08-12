package com.carcam.platecheck.data

import android.content.Context

/**
 * Turns a stream of plate sightings into entry/exit records.
 *
 * The camera reports the same plate many times a second while a car sits at the barrier, so
 * the rule "a sighting toggles the car in or out" is only safe with a per-plate cooldown —
 * without it a car waiting ten seconds would enter and leave several times over. The cooldown
 * is deliberately short (90s by default): long enough to cover a car lingering in frame, short
 * enough that a genuine drop-off and immediate departure still records an exit.
 */
class VisitRepository(
    context: Context,
    private val cooldownMs: Long = 90_000L,
    private val now: () -> Long = System::currentTimeMillis,
    // Injectable so tests run against an in-memory database. A test that seeds and clears the
    // real one deletes the user's registered vehicles and their entry/exit history.
    db: PlateDatabase = PlateDatabase.getInstance(context)
) {
    private val dao = db.visitDao()
    private val plates = PlateRepository(context, db)

    /** Last time each plate produced a record, to suppress repeat sightings of the same car. */
    private val lastAction = HashMap<String, Long>()

    enum class Kind { ENTRY, EXIT }

    data class Record(val kind: Kind, val visit: VisitEntity, val durationMs: Long?)

    val parkedCount = dao.observeParkedCount()

    fun visitsForDay(dayStart: Long, dayEnd: Long) = dao.observeVisitsForDay(dayStart, dayEnd)

    /**
     * Record a sighting. Returns null when it is a repeat within the cooldown, or when the
     * plate is still within it — the caller shows nothing in that case, so a car queuing at
     * the barrier does not flicker between states.
     */
    suspend fun onPlateSeen(rawPlate: String): Record? {
        val matched = plates.checkPlate(rawPlate)
        val canonical = matched?.plateNumber ?: rawPlate.trim()
        if (canonical.isEmpty()) return null

        val t = now()
        val last = lastAction[canonical]
        if (last != null && t - last < cooldownMs) return null
        lastAction[canonical] = t

        val open = dao.findOpenVisit(canonical)
        return if (open != null) {
            val closed = open.copy(exitAt = t)
            dao.update(closed)
            Record(Kind.EXIT, closed, closed.durationMs)
        } else {
            val visit = VisitEntity(
                plateNumber = rawPlate.trim(),
                canonicalPlate = canonical,
                isRegistered = matched != null,
                entryAt = t
            )
            val id = dao.insert(visit)
            Record(Kind.ENTRY, visit.copy(id = id), null)
        }
    }

    /** Undo the record just made, including its cooldown, so a mistake can be re-scanned. */
    suspend fun undo(record: Record) {
        lastAction.remove(record.visit.canonicalPlate)
        when (record.kind) {
            Kind.ENTRY -> dao.findById(record.visit.id)?.let { dao.delete(it) }
            Kind.EXIT -> dao.findById(record.visit.id)?.let { dao.update(it.copy(exitAt = null)) }
        }
    }

    /**
     * Reinterpret an entry as an exit. Needed when a car got in while the app was closed:
     * with no open stay its exit scan looks exactly like an arrival, and only the operator
     * knows otherwise. The entry time is left unknown rather than invented.
     */
    suspend fun convertEntryToExit(record: Record) {
        if (record.kind != Kind.ENTRY) return
        dao.findById(record.visit.id)?.let {
            dao.update(it.copy(entryAt = null, exitAt = it.entryAt ?: now()))
        }
    }

    /** Close a stay by hand, for a car that left without being scanned. */
    suspend fun closeManually(visit: VisitEntity) {
        if (visit.isOpen) dao.update(visit.copy(exitAt = now()))
        lastAction.remove(visit.canonicalPlate)
    }

    suspend fun delete(visit: VisitEntity) {
        dao.delete(visit)
        lastAction.remove(visit.canonicalPlate)
    }

    suspend fun allVisits(): List<VisitEntity> = dao.getAllOnce()
}
