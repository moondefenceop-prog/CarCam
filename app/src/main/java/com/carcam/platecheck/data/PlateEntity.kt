package com.carcam.platecheck.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "plates",
    // A resident list runs to thousands of rows and every scanned frame queries it, so both
    // lookup paths — exact text and digits-only — have to be index hits rather than scans.
    indices = [Index("plateNumber"), Index("digitsKey")]
)
data class PlateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val plateNumber: String,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),

    /**
     * The plate's digits with everything else stripped, stored so the fallback match can be a
     * single indexed query. It exists because the usage glyph is the character OCR gets wrong
     * most often, and the digits alone still identify the car.
     */
    val digitsKey: String = ""
)
