package com.carcam.platecheck.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [PlateEntity::class], version = 1, exportSchema = false)
abstract class PlateDatabase : RoomDatabase() {
    abstract fun plateDao(): PlateDao

    companion object {
        @Volatile private var INSTANCE: PlateDatabase? = null

        fun getInstance(context: Context): PlateDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PlateDatabase::class.java,
                    "plate_db"
                ).build().also { INSTANCE = it }
            }
    }
}
