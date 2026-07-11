package com.carcam.platecheck.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.carcam.platecheck.data.PlateRepository
import kotlinx.coroutines.launch

data class ScanResult(
    val plateNumber: String,      // OCR이 읽은 원문 (캐시 키)
    val isRegistered: Boolean,
    val note: String = "",
    // 등록 차량과 매칭된 경우 DB에 저장된 정확한 번호판. 오인식된 한글을 화면에서
    // 올바른 글자로 바꿔 보여주기 위한 표시용 텍스트다.
    val canonicalPlate: String = plateNumber
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = PlateRepository(app)
    val scanResult = MutableLiveData<ScanResult?>()
    private var lastCheckedPlate = ""

    fun checkPlate(plateNumber: String) {
        if (plateNumber == lastCheckedPlate) return
        lastCheckedPlate = plateNumber
        viewModelScope.launch {
            val entity = repository.checkPlate(plateNumber)
            scanResult.postValue(
                ScanResult(
                    plateNumber = plateNumber,
                    isRegistered = entity != null,
                    note = entity?.note ?: "",
                    canonicalPlate = entity?.plateNumber ?: plateNumber
                )
            )
        }
    }

    fun clearResult() {
        lastCheckedPlate = ""
        scanResult.value = null
    }
}
