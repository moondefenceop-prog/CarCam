package com.carcam.platecheck.util;

/**
 * The ad policy is the one place a revenue change can quietly make the app unusable at a
 * barrier, so its limits are pinned down here rather than checked by hand on a device.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000.\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\t\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0005\n\u0002\u0010\b\n\u0002\b\u0003\u0018\u00002\u00020\u0001:\u0001\u0012B\u0005\u00a2\u0006\u0002\u0010\u0002J\b\u0010\t\u001a\u00020\nH\u0007J\b\u0010\u000b\u001a\u00020\nH\u0007J\b\u0010\f\u001a\u00020\nH\u0007J\b\u0010\r\u001a\u00020\nH\u0007J\u0010\u0010\u000e\u001a\u00020\n2\u0006\u0010\u000f\u001a\u00020\u0010H\u0002J\b\u0010\u0011\u001a\u00020\nH\u0007R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\bX\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0013"}, d2 = {"Lcom/carcam/platecheck/util/InterstitialPolicyTest;", "", "()V", "clock", "", "policy", "Lcom/carcam/platecheck/util/InterstitialPolicy;", "prefs", "Lcom/carcam/platecheck/util/InterstitialPolicyTest$FakePrefs;", "cap resets the next day", "", "daily cap holds across a busy shift", "never twice inside the minimum gap", "no ads while the install is new", "openApp", "times", "", "shows after the grace period", "FakePrefs", "app_debugUnitTest"})
public final class InterstitialPolicyTest {
    private long clock = 1000000000L;
    @org.jetbrains.annotations.NotNull()
    private final com.carcam.platecheck.util.InterstitialPolicyTest.FakePrefs prefs = null;
    @org.jetbrains.annotations.NotNull()
    private final com.carcam.platecheck.util.InterstitialPolicy policy = null;
    
    public InterstitialPolicyTest() {
        super();
    }
    
    private final void openApp(int times) {
    }
    
    /**
     * In-memory stand-in for SharedPreferences (android.jar is stubbed in unit tests).
     */
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000Z\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0002\u0010\u0000\n\u0002\b\u0003\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010$\n\u0002\b\u0003\n\u0002\u0010\u0007\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\t\n\u0002\b\u0002\n\u0002\u0010#\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\b\u0002\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J\u0013\u0010\t\u001a\u00020\n2\b\u0010\u000b\u001a\u0004\u0018\u00010\u0005H\u0096\u0002J\b\u0010\f\u001a\u00020\rH\u0016J\u0012\u0010\u000e\u001a\f\u0012\u0004\u0012\u00020\u0005\u0012\u0002\b\u00030\u000fH\u0016J\u001a\u0010\u0010\u001a\u00020\n2\b\u0010\u000b\u001a\u0004\u0018\u00010\u00052\u0006\u0010\u0011\u001a\u00020\nH\u0016J\u001a\u0010\u0012\u001a\u00020\u00132\b\u0010\u000b\u001a\u0004\u0018\u00010\u00052\u0006\u0010\u0011\u001a\u00020\u0013H\u0016J\u001a\u0010\u0014\u001a\u00020\u00152\b\u0010\u000b\u001a\u0004\u0018\u00010\u00052\u0006\u0010\u0011\u001a\u00020\u0015H\u0016J\u001a\u0010\u0016\u001a\u00020\u00172\b\u0010\u000b\u001a\u0004\u0018\u00010\u00052\u0006\u0010\u0011\u001a\u00020\u0017H\u0016J\u001e\u0010\u0018\u001a\u0004\u0018\u00010\u00052\b\u0010\u000b\u001a\u0004\u0018\u00010\u00052\b\u0010\u0011\u001a\u0004\u0018\u00010\u0005H\u0016J*\u0010\u0019\u001a\n\u0012\u0004\u0012\u00020\u0005\u0018\u00010\u001a2\b\u0010\u000b\u001a\u0004\u0018\u00010\u00052\u000e\u0010\u0011\u001a\n\u0012\u0004\u0012\u00020\u0005\u0018\u00010\u001aH\u0016J\u0012\u0010\u001b\u001a\u00020\u001c2\b\u0010\u001d\u001a\u0004\u0018\u00010\u001eH\u0016J\u0012\u0010\u001f\u001a\u00020\u001c2\b\u0010\u001d\u001a\u0004\u0018\u00010\u001eH\u0016R\u001f\u0010\u0003\u001a\u0010\u0012\u0004\u0012\u00020\u0005\u0012\u0006\u0012\u0004\u0018\u00010\u00060\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\b\u00a8\u0006 "}, d2 = {"Lcom/carcam/platecheck/util/InterstitialPolicyTest$FakePrefs;", "Landroid/content/SharedPreferences;", "()V", "map", "Ljava/util/HashMap;", "", "", "getMap", "()Ljava/util/HashMap;", "contains", "", "k", "edit", "Landroid/content/SharedPreferences$Editor;", "getAll", "", "getBoolean", "d", "getFloat", "", "getInt", "", "getLong", "", "getString", "getStringSet", "", "registerOnSharedPreferenceChangeListener", "", "l", "Landroid/content/SharedPreferences$OnSharedPreferenceChangeListener;", "unregisterOnSharedPreferenceChangeListener", "app_debugUnitTest"})
    static final class FakePrefs implements android.content.SharedPreferences {
        @org.jetbrains.annotations.NotNull()
        private final java.util.HashMap<java.lang.String, java.lang.Object> map = null;
        
        public FakePrefs() {
            super();
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.util.HashMap<java.lang.String, java.lang.Object> getMap() {
            return null;
        }
        
        @java.lang.Override()
        @org.jetbrains.annotations.NotNull()
        public java.util.Map<java.lang.String, ?> getAll() {
            return null;
        }
        
        @java.lang.Override()
        @org.jetbrains.annotations.Nullable()
        public java.lang.String getString(@org.jetbrains.annotations.Nullable()
        java.lang.String k, @org.jetbrains.annotations.Nullable()
        java.lang.String d) {
            return null;
        }
        
        @java.lang.Override()
        @org.jetbrains.annotations.Nullable()
        public java.util.Set<java.lang.String> getStringSet(@org.jetbrains.annotations.Nullable()
        java.lang.String k, @org.jetbrains.annotations.Nullable()
        java.util.Set<java.lang.String> d) {
            return null;
        }
        
        @java.lang.Override()
        public int getInt(@org.jetbrains.annotations.Nullable()
        java.lang.String k, int d) {
            return 0;
        }
        
        @java.lang.Override()
        public long getLong(@org.jetbrains.annotations.Nullable()
        java.lang.String k, long d) {
            return 0L;
        }
        
        @java.lang.Override()
        public float getFloat(@org.jetbrains.annotations.Nullable()
        java.lang.String k, float d) {
            return 0.0F;
        }
        
        @java.lang.Override()
        public boolean getBoolean(@org.jetbrains.annotations.Nullable()
        java.lang.String k, boolean d) {
            return false;
        }
        
        @java.lang.Override()
        public boolean contains(@org.jetbrains.annotations.Nullable()
        java.lang.String k) {
            return false;
        }
        
        @java.lang.Override()
        public void registerOnSharedPreferenceChangeListener(@org.jetbrains.annotations.Nullable()
        android.content.SharedPreferences.OnSharedPreferenceChangeListener l) {
        }
        
        @java.lang.Override()
        public void unregisterOnSharedPreferenceChangeListener(@org.jetbrains.annotations.Nullable()
        android.content.SharedPreferences.OnSharedPreferenceChangeListener l) {
        }
        
        @java.lang.Override()
        @org.jetbrains.annotations.NotNull()
        public android.content.SharedPreferences.Editor edit() {
            return null;
        }
    }
}