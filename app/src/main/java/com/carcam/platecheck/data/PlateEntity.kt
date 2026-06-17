package com.carcam.platecheck.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "plates")
data class PlateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val plateNumber: String,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
