package com.carcam.platecheck.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.carcam.platecheck.data.PlateEntity
import com.carcam.platecheck.data.PlateRepository
import com.carcam.platecheck.util.PlateImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlateListViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = PlateRepository(app)
    val allPlates = repository.allPlates

    /** Message describing the last import, for the caller to surface. */
    val importStatus = MutableLiveData<String?>()

    fun addPlate(plateNumber: String, note: String) = viewModelScope.launch {
        repository.addPlate(plateNumber, note)
    }

    fun deletePlate(plate: PlateEntity) = viewModelScope.launch {
        repository.deletePlate(plate)
    }

    fun importFrom(uri: Uri) = viewModelScope.launch {
        val app = getApplication<Application>()
        val parsed = withContext(Dispatchers.IO) { PlateImporter.read(app, uri) }
        if (parsed.error != null) {
            importStatus.value = "가져오기 실패: ${parsed.error}"
            return@launch
        }
        if (parsed.rows.isEmpty()) {
            importStatus.value = "번호판을 찾지 못했습니다 (첫 번째 열에 번호판이 있어야 합니다)"
            return@launch
        }
        val outcome = withContext(Dispatchers.IO) { repository.importPlates(parsed.rows) }
        importStatus.value = buildString {
            append("${outcome.added}대 추가")
            if (outcome.alreadyPresent > 0) append(", ${outcome.alreadyPresent}대 이미 등록됨")
            if (parsed.skipped > 0) append(", ${parsed.skipped}줄 건너뜀")
        }
    }

    fun clearImportStatus() { importStatus.value = null }
}
