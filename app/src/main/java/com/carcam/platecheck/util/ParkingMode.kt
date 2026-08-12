package com.carcam.platecheck.util

import android.content.Context

/**
 * What a scan means on the camera screen.
 *
 * [CHECK] is the default and stays the behaviour existing users know: a scan only answers
 * "is this car registered?". Turning [PARKING] on is an explicit choice, because from that
 * moment every sighting writes a record.
 */
enum class ParkingMode {
    CHECK, PARKING;

    companion object {
        private const val PREFS = "parking"
        private const val KEY = "mode"

        fun load(context: Context): ParkingMode =
            runCatching {
                valueOf(
                    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .getString(KEY, CHECK.name)!!
                )
            }.getOrDefault(CHECK)

        fun save(context: Context, mode: ParkingMode) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, mode.name).apply()
        }
    }
}

/** "2시간 15분" / "45분". Seconds are noise at parking timescales. */
fun formatDuration(ms: Long): String {
    val totalMinutes = ms / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}시간 ${minutes}분"
        hours > 0 -> "${hours}시간"
        else -> "${minutes}분"
    }
}
