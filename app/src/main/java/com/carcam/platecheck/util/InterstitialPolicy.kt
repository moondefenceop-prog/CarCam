package com.carcam.platecheck.util

import android.content.Context

/**
 * Decides *when* a full-screen ad may appear. Deliberately independent of any ad SDK so the
 * rule can be reasoned about and tested on its own; the caller loads and shows the ad.
 *
 * The constraint that shapes everything here: this app is used one-handed at a barrier, with
 * a driver waiting. A full-screen ad between a car arriving and the attendant seeing
 * registered/not-registered is not just annoying, it blocks the job — and an operator who
 * cannot do the job during a rush uninstalls. So the policy never interrupts a check.
 *
 * Ads are allowed only at *natural stopping points*, where the user has finished something
 * and nothing is waiting on them:
 *   - [Trigger.SESSION_END]  — leaving the scanner (back out of the camera screen)
 *   - [Trigger.LIST_MANAGED] — after a bulk edit such as a spreadsheet import
 * and never during scanning, never on a lookup result, never on app start (a cold-start ad
 * delays the first scan, which is the moment the app has to feel fast).
 *
 * On top of the trigger, three limits keep frequency sane for a user who opens the app
 * dozens of times a shift: a minimum gap between ads, a daily cap, and a grace period for a
 * newly installed app, since early uninstalls track closely with early ad exposure.
 */
class InterstitialPolicy(
    private val prefs: android.content.SharedPreferences,
    private val minGapMs: Long = 4 * 60 * 1000L,      // never twice within 4 minutes
    private val maxPerDay: Int = 6,
    private val graceOpens: Int = 5,                   // no ads for the first few sessions
    private val now: () -> Long = System::currentTimeMillis
) {
    enum class Trigger { SESSION_END, LIST_MANAGED }

    companion object {
        private const val KEY_LAST_SHOWN = "ad_last_shown"
        private const val KEY_DAY = "ad_day"
        private const val KEY_COUNT_TODAY = "ad_count_today"
        private const val KEY_OPENS = "ad_opens"

        fun from(context: Context) = InterstitialPolicy(
            context.getSharedPreferences("ads", Context.MODE_PRIVATE)
        )
    }

    /** Count an app open. Drives the new-install grace period. */
    fun noteAppOpen() {
        prefs.edit().putInt(KEY_OPENS, prefs.getInt(KEY_OPENS, 0) + 1).apply()
    }

    fun shouldShow(trigger: Trigger): Boolean {
        if (prefs.getInt(KEY_OPENS, 0) <= graceOpens) return false
        if (now() - prefs.getLong(KEY_LAST_SHOWN, 0L) < minGapMs) return false
        if (countToday() >= maxPerDay) return false
        // A session that ended without any work being done is someone who opened the app by
        // mistake or is checking one car; an ad there is pure friction for no engagement.
        return when (trigger) {
            Trigger.SESSION_END -> true
            Trigger.LIST_MANAGED -> true
        }
    }

    /** Record that an ad was actually displayed (call from the SDK's shown callback). */
    fun noteShown() {
        val day = dayKey()
        val count = if (prefs.getLong(KEY_DAY, -1L) == day) prefs.getInt(KEY_COUNT_TODAY, 0) else 0
        prefs.edit()
            .putLong(KEY_LAST_SHOWN, now())
            .putLong(KEY_DAY, day)
            .putInt(KEY_COUNT_TODAY, count + 1)
            .apply()
    }

    private fun countToday(): Int =
        if (prefs.getLong(KEY_DAY, -1L) == dayKey()) prefs.getInt(KEY_COUNT_TODAY, 0) else 0

    private fun dayKey(): Long = now() / (24 * 60 * 60 * 1000L)
}
