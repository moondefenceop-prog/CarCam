package com.carcam.platecheck.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ad policy is the one place a revenue change can quietly make the app unusable at a
 * barrier, so its limits are pinned down here rather than checked by hand on a device.
 */
class InterstitialPolicyTest {

    /** In-memory stand-in for SharedPreferences (android.jar is stubbed in unit tests). */
    private class FakePrefs : android.content.SharedPreferences {
        val map = HashMap<String, Any?>()
        override fun getAll() = map as Map<String, *>
        override fun getString(k: String?, d: String?) = map[k] as? String ?: d
        override fun getStringSet(k: String?, d: MutableSet<String>?) = d
        override fun getInt(k: String?, d: Int) = map[k] as? Int ?: d
        override fun getLong(k: String?, d: Long) = map[k] as? Long ?: d
        override fun getFloat(k: String?, d: Float) = map[k] as? Float ?: d
        override fun getBoolean(k: String?, d: Boolean) = map[k] as? Boolean ?: d
        override fun contains(k: String?) = map.containsKey(k)
        override fun registerOnSharedPreferenceChangeListener(l: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(l: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun edit(): android.content.SharedPreferences.Editor = object : android.content.SharedPreferences.Editor {
            override fun putString(k: String?, v: String?) = apply { map[k!!] = v }
            override fun putStringSet(k: String?, v: MutableSet<String>?) = apply { map[k!!] = v }
            override fun putInt(k: String?, v: Int) = apply { map[k!!] = v }
            override fun putLong(k: String?, v: Long) = apply { map[k!!] = v }
            override fun putFloat(k: String?, v: Float) = apply { map[k!!] = v }
            override fun putBoolean(k: String?, v: Boolean) = apply { map[k!!] = v }
            override fun remove(k: String?) = apply { map.remove(k) }
            override fun clear() = apply { map.clear() }
            override fun commit() = true
            override fun apply() {}
        }
    }

    private var clock = 1_000_000_000L
    private val prefs = FakePrefs()
    private val policy = InterstitialPolicy(prefs, now = { clock })

    private fun openApp(times: Int) = repeat(times) { policy.noteAppOpen() }

    @Test fun `no ads while the install is new`() {
        openApp(5)
        assertFalse(policy.shouldShow(InterstitialPolicy.Trigger.SESSION_END))
    }

    @Test fun `shows after the grace period`() {
        openApp(6)
        assertTrue(policy.shouldShow(InterstitialPolicy.Trigger.SESSION_END))
    }

    @Test fun `never twice inside the minimum gap`() {
        openApp(6)
        policy.noteShown()
        clock += 3 * 60 * 1000L
        assertFalse(policy.shouldShow(InterstitialPolicy.Trigger.SESSION_END))
        clock += 2 * 60 * 1000L
        assertTrue(policy.shouldShow(InterstitialPolicy.Trigger.SESSION_END))
    }

    @Test fun `daily cap holds across a busy shift`() {
        openApp(6)
        repeat(6) {
            assertTrue(policy.shouldShow(InterstitialPolicy.Trigger.SESSION_END))
            policy.noteShown()
            clock += 10 * 60 * 1000L
        }
        assertFalse("7th ad in a day must be refused",
            policy.shouldShow(InterstitialPolicy.Trigger.SESSION_END))
    }

    @Test fun `cap resets the next day`() {
        openApp(6)
        repeat(6) { policy.noteShown(); clock += 10 * 60 * 1000L }
        clock += 24 * 60 * 60 * 1000L
        assertTrue(policy.shouldShow(InterstitialPolicy.Trigger.SESSION_END))
    }
}
