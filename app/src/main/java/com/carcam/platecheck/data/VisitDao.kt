package com.carcam.platecheck.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface VisitDao {

    /** The open stay for a plate, if it is parked right now. */
    @Query("SELECT * FROM visits WHERE canonicalPlate = :plate AND exitAt IS NULL ORDER BY id DESC LIMIT 1")
    suspend fun findOpenVisit(plate: String): VisitEntity?

    @Query("SELECT COUNT(*) FROM visits WHERE exitAt IS NULL")
    fun observeParkedCount(): LiveData<Int>

    /**
     * Records touching a day: entered during it, left during it, or spanned it while parked.
     * A car that stays three days must appear on all three, or a day's list looks empty
     * while the car park is full.
     */
    @Query(
        """SELECT * FROM visits
           WHERE (entryAt IS NOT NULL AND entryAt < :dayEnd AND (exitAt IS NULL OR exitAt >= :dayStart))
              OR (entryAt IS NULL AND exitAt >= :dayStart AND exitAt < :dayEnd)
           ORDER BY COALESCE(exitAt, entryAt) DESC"""
    )
    fun observeVisitsForDay(dayStart: Long, dayEnd: Long): LiveData<List<VisitEntity>>

    @Query("SELECT * FROM visits ORDER BY COALESCE(entryAt, exitAt) ASC")
    suspend fun getAllOnce(): List<VisitEntity>

    @Insert
    suspend fun insert(visit: VisitEntity): Long

    @Update
    suspend fun update(visit: VisitEntity)

    @Delete
    suspend fun delete(visit: VisitEntity)

    @Query("SELECT * FROM visits WHERE id = :id")
    suspend fun findById(id: Long): VisitEntity?
}
