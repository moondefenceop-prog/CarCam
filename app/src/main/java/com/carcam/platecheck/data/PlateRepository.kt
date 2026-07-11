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

        // 가운데 한글이 소실/숫자화된 스캔("1547070", "15447070")까지 대조할 수 있도록
        // 가능한 숫자 키 해석을 모두 시도한다.
        val keys = KoreanPlateRecognizer.candidateDigitKeys(trimmed)
        if (keys.first().length < 6) return null
        return dao.getAllPlatesOnce().firstOrNull { entity ->
            val entityKey = KoreanPlateRecognizer.digitsOnly(entity.plateNumber)
            keys.any { it == entityKey } &&
                // 양쪽 다 유효한 한글로 읽혔는데 글자가 다르면 숫자가 같아도 다른 차량
                KoreanPlateRecognizer.isMiddleCompatible(trimmed, entity.plateNumber)
        }
    }
}
