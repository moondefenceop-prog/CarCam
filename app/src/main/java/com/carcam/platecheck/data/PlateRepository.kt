package com.carcam.platecheck.data

import android.content.Context

class PlateRepository(context: Context) {
    private val dao = PlateDatabase.getInstance(context).plateDao()

    val allPlates = dao.getAllPlates()

    suspend fun addPlate(plateNumber: String, note: String) {
        dao.insert(PlateEntity(plateNumber = plateNumber.trim(), note = note.trim()))
    }

    suspend fun deletePlate(plate: PlateEntity) {
        dao.delete(plate)
    }

    suspend fun checkPlate(plateNumber: String): PlateEntity? {
        return dao.findByNumber(plateNumber.trim())
    }
}
