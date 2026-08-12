package com.carcam.platecheck.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One stay in the car park: an entry scan and, once the car leaves, an exit scan.
 *
 * A row with a null [exitAt] *is* the "currently parked" state, so no separate status column
 * can drift out of sync with the timestamps.
 */
@Entity(
    tableName = "visits",
    // Every scan asks "is this plate already parked?", and the record screen reads by day.
    indices = [Index("canonicalPlate", "exitAt"), Index("entryAt")]
)
data class VisitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /** What OCR actually read, kept so a misread can be traced back. */
    val plateNumber: String,

    /** The registered plate this matched, or the raw read when it matched nothing. */
    val canonicalPlate: String,

    /**
     * Registration status as it was at entry. Snapshotted rather than looked up later:
     * adding a car to the registered list must not silently rewrite last month's records.
     */
    val isRegistered: Boolean,

    /** Null when the car was first seen leaving, i.e. its entry was never scanned. */
    val entryAt: Long?,

    /** Null while the car is still parked. */
    val exitAt: Long? = null
) {
    val isOpen: Boolean get() = exitAt == null

    /** Stay length in milliseconds, or null if either end is unknown. */
    val durationMs: Long?
        get() {
            val start = entryAt ?: return null
            val end = exitAt ?: return null
            return (end - start).coerceAtLeast(0)
        }
}
