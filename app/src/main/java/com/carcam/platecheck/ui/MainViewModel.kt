package com.carcam.platecheck.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.carcam.platecheck.data.PlateRepository
import kotlinx.coroutines.launch

data class ScanResult(
    val plateNumber: String,
    val isRegistered: Boolean,
    val note: String = ""
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
                    note = entity?.note ?: ""
                )
            )
        }
    }

    fun clearResult() {
        lastCheckedPlate = ""
        scanResult.value = null
    }
}
