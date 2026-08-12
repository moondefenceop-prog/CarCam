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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(plate: PlateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(plates: List<PlateEntity>)

    @Delete
    suspend fun delete(plate: PlateEntity)
}
