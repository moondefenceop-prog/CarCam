package com.carcam.platecheck.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface PlateDao {
    @Query("SELECT * FROM plates ORDER BY createdAt DESC")
    fun getAllPlates(): LiveData<List<PlateEntity>>

    @Query("SELECT * FROM plates WHERE plateNumber = :plateNumber LIMIT 1")
    suspend fun findByNumber(plateNumber: String): PlateEntity?

    @Query("SELECT * FROM plates")
    suspend fun getAllPlatesOnce(): List<PlateEntity>

    /**
     * Fallback match on the digits alone, as an indexed lookup. The alternative — reading
     * every row and filtering in Kotlin — is what this replaces: with a few thousand
     * residents that ran on every scanned frame that was not an exact hit.
     */
    @Query("SELECT * FROM plates WHERE digitsKey IN (:keys)")
    suspend fun findByDigitsKeys(keys: List<String>): List<PlateEntity>

    @Query("SELECT plateNumber FROM plates")
    suspend fun getAllNumbers(): List<String>

    @Query("SELECT COUNT(*) FROM plates WHERE digitsKey = ''")
    suspend fun countMissingDigitsKey(): Int

    @Query("UPDATE plates SET digitsKey = :key WHERE id = :id")
    suspend fun setDigitsKey(id: Long, key: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(plate: PlateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(plates: List<PlateEntity>)

    @Delete
    suspend fun delete(plate: PlateEntity)
}
