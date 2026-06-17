package com.carcam.platecheck.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.carcam.platecheck.data.PlateEntity
import com.carcam.platecheck.data.PlateRepository
import kotlinx.coroutines.launch

class PlateListViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = PlateRepository(app)
    val allPlates = repository.allPlates

    fun addPlate(plateNumber: String, note: String) = viewModelScope.launch {
        repository.addPlate(plateNumber, note)
    }

    fun deletePlate(plate: PlateEntity) = viewModelScope.launch {
        repository.deletePlate(plate)
    }
}
