package com.carcam.platecheck.data

import android.content.Context
import com.carcam.platecheck.util.KoreanPlateRecognizer

class PlateRepository(context: Context) {
    private val dao = PlateDatabase.getInstance(context).plateDao()

    val allPlates = dao.getAllPlates()

    suspend fun addPlate(plateNumber: String, note: String) {
        dao.insert(PlateEntity(plateNumber = plateNumber.trim(), note = note.trim()))
    }

    suspend fun deletePlate(plate: PlateEntity) {
        dao.delete(plate)
    }

    // 흐림/저해상도로 가운데 한글이 오인식된 스캔 결과도 등록 차량과 매칭할 수 있도록,
    // 정확히 일치하는 번호판이 없으면 숫자 부분만으로 한 번 더 조회한다.
    suspend fun checkPlate(plateNumber: String): PlateEntity? {
        val trimmed = plateNumber.trim()
        dao.findByNumber(trimmed)?.let { return it }

        val scannedDigits = KoreanPlateRecognizer.digitsOnly(trimmed)
        if (scannedDigits.length < 6) return null
        return dao.getAllPlatesOnce().firstOrNull {
            KoreanPlateRecognizer.digitsOnly(it.plateNumber) == scannedDigits
        }
    }
}
