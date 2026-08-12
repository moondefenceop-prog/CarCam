package com.carcam.platecheck.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.carcam.platecheck.data.VisitEntity
import com.carcam.platecheck.data.VisitRepository
import com.carcam.platecheck.util.formatDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class VisitListViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = VisitRepository(app)

    /** Midnight of the day being viewed. */
    private val day = MutableLiveData(startOfToday())

    val visits: LiveData<List<VisitEntity>> = day.switchMap { start ->
        repository.visitsForDay(start, start + DAY_MS)
    }

    val dayLabel: LiveData<String> = day.map { start ->
        SimpleDateFormat("yyyy년 M월 d일 (E)", Locale.KOREA).format(Date(start))
    }

    val exportStatus = MutableLiveData<String?>()

    fun shiftDay(days: Int) {
        day.value = (day.value ?: startOfToday()) + days * DAY_MS
    }

    /** Is the viewed day today? Used to stop the user paging into the future. */
    fun isToday(): Boolean = day.value == startOfToday()

    fun closeManually(visit: VisitEntity) = viewModelScope.launch {
        repository.closeManually(visit)
    }

    fun delete(visit: VisitEntity) = viewModelScope.launch {
        repository.delete(visit)
    }

    /**
     * Export every record as CSV. Written with a BOM and CRLF so Excel opens it with Hangul
     * intact — without the BOM it decodes as the system codepage and the plates turn to mojibake.
     */
    fun exportCsv(): LiveData<String?> {
        val result = MutableLiveData<String?>()
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                val ts = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA)
                buildString {
                    append('﻿')
                    append("번호판,등록여부,입차,출차,사용시간(분)\r\n")
                    for (v in repository.allVisits()) {
                        append(v.canonicalPlate).append(',')
                        append(if (v.isRegistered) "등록" else "미등록").append(',')
                        append(v.entryAt?.let { ts.format(Date(it)) } ?: "").append(',')
                        append(v.exitAt?.let { ts.format(Date(it)) } ?: "").append(',')
                        append(v.durationMs?.let { (it / 60_000).toString() } ?: "")
                        append("\r\n")
                    }
                }
            }
            result.value = text
        }
        return result
    }

    fun summaryOf(list: List<VisitEntity>): String {
        val start = day.value ?: startOfToday()
        val end = start + DAY_MS
        val entered = list.count { it.entryAt != null && it.entryAt in start until end }
        val exited = list.count { it.exitAt != null && it.exitAt in start until end }
        val parked = list.count { it.isOpen }
        val totalMs = list.mapNotNull { it.durationMs }.sum()
        val avg = list.mapNotNull { it.durationMs }.let {
            if (it.isEmpty()) null else it.sum() / it.size
        }
        return buildString {
            append("입차 $entered · 출차 $exited · 주차중 $parked")
            if (avg != null) append("  |  평균 ${formatDuration(avg)}")
            if (totalMs > 0) append(" · 합계 ${formatDuration(totalMs)}")
        }
    }

    private fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    companion object { private const val DAY_MS = 24 * 60 * 60 * 1000L }
}
