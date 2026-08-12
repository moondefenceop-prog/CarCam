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

    // Typed lookups are kept off [scanResult] on purpose: that stream drives the camera
    // overlay and is cleared on a timer, which would wipe a result the user is still reading.
    val manualResult = MutableLiveData<ScanResult?>()
    private var lastCheckedPlate = ""

    /** Look up a hand-typed plate. Same matching rules as a scan, so a plate registered with
     *  a mistyped usage glyph still resolves by its digits. */
    fun checkManual(plateNumber: String) {
        val query = plateNumber.trim()
        if (query.isEmpty()) return
        viewModelScope.launch {
            val entity = repository.checkPlate(query)
            manualResult.postValue(
                ScanResult(
                    plateNumber = query,
                    isRegistered = entity != null,
                    note = entity?.note ?: "",
                    canonicalPlate = entity?.plateNumber ?: query
                )
            )
        }
    }

    fun clearManualResult() {
        manualResult.value = null
    }

    fun registerPlate(plateNumber: String, note: String = "") = viewModelScope.launch {
        repository.addPlate(plateNumber, note)
        // Drop the cached miss so the camera overlay stops calling it unregistered.
        lastCheckedPlate = ""
        checkManual(plateNumber)
    }

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
