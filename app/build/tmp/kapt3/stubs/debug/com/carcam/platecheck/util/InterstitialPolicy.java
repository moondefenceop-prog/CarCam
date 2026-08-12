package com.carcam.platecheck.util;

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
 *  - [Trigger.SESSION_END]  — leaving the scanner (back out of the camera screen)
 *  - [Trigger.LIST_MANAGED] — after a bulk edit such as a spreadsheet import
 * and never during scanning, never on a lookup result, never on app start (a cold-start ad
 * delays the first scan, which is the moment the app has to feel fast).
 *
 * On top of the trigger, three limits keep frequency sane for a user who opens the app
 * dozens of times a shift: a minimum gap between ads, a daily cap, and a grace period for a
 * newly installed app, since early uninstalls track closely with early ad exposure.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000<\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\t\n\u0000\n\u0002\u0010\b\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\u0018\u0000 \u00152\u00020\u0001:\u0002\u0015\u0016B;\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\b\b\u0002\u0010\u0004\u001a\u00020\u0005\u0012\b\b\u0002\u0010\u0006\u001a\u00020\u0007\u0012\b\b\u0002\u0010\b\u001a\u00020\u0007\u0012\u000e\b\u0002\u0010\t\u001a\b\u0012\u0004\u0012\u00020\u00050\n\u00a2\u0006\u0002\u0010\u000bJ\b\u0010\f\u001a\u00020\u0007H\u0002J\b\u0010\r\u001a\u00020\u0005H\u0002J\u0006\u0010\u000e\u001a\u00020\u000fJ\u0006\u0010\u0010\u001a\u00020\u000fJ\u000e\u0010\u0011\u001a\u00020\u00122\u0006\u0010\u0013\u001a\u00020\u0014R\u000e\u0010\b\u001a\u00020\u0007X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0007X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0004\u001a\u00020\u0005X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\t\u001a\b\u0012\u0004\u0012\u00020\u00050\nX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0017"}, d2 = {"Lcom/carcam/platecheck/util/InterstitialPolicy;", "", "prefs", "Landroid/content/SharedPreferences;", "minGapMs", "", "maxPerDay", "", "graceOpens", "now", "Lkotlin/Function0;", "(Landroid/content/SharedPreferences;JIILkotlin/jvm/functions/Function0;)V", "countToday", "dayKey", "noteAppOpen", "", "noteShown", "shouldShow", "", "trigger", "Lcom/carcam/platecheck/util/InterstitialPolicy$Trigger;", "Companion", "Trigger", "app_debug"})
public final class InterstitialPolicy {
    @org.jetbrains.annotations.NotNull()
    private final android.content.SharedPreferences prefs = null;
    private final long minGapMs = 0L;
    private final int maxPerDay = 0;
    private final int graceOpens = 0;
    @org.jetbrains.annotations.NotNull()
    private final kotlin.jvm.functions.Function0<java.lang.Long> now = null;
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String KEY_LAST_SHOWN = "ad_last_shown";
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String KEY_DAY = "ad_day";
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String KEY_COUNT_TODAY = "ad_count_today";
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String KEY_OPENS = "ad_opens";
    @org.jetbrains.annotations.NotNull()
    public static final com.carcam.platecheck.util.InterstitialPolicy.Companion Companion = null;
    
    public InterstitialPolicy(@org.jetbrains.annotations.NotNull()
    android.content.SharedPreferences prefs, long minGapMs, int maxPerDay, int graceOpens, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function0<java.lang.Long> now) {
        super();
    }
    
    /**
     * Count an app open. Drives the new-install grace period.
     */
    public final void noteAppOpen() {
    }
    
    public final boolean shouldShow(@org.jetbrains.annotations.NotNull()
    com.carcam.platecheck.util.InterstitialPolicy.Trigger trigger) {
        return false;
    }
    
    /**
     * Record that an ad was actually displayed (call from the SDK's shown callback).
     */
    public final void noteShown() {
    }
    
    private final int countToday() {
        return 0;
    }
    
    private final long dayKey() {
        return 0L;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000 \n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u000e\u0010\b\u001a\u00020\t2\u0006\u0010\n\u001a\u00020\u000bR\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\f"}, d2 = {"Lcom/carcam/platecheck/util/InterstitialPolicy$Companion;", "", "()V", "KEY_COUNT_TODAY", "", "KEY_DAY", "KEY_LAST_SHOWN", "KEY_OPENS", "from", "Lcom/carcam/platecheck/util/InterstitialPolicy;", "context", "Landroid/content/Context;", "app_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.carcam.platecheck.util.InterstitialPolicy from(@org.jetbrains.annotations.NotNull()
        android.content.Context context) {
            return null;
        }
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0010\u0010\n\u0002\b\u0004\b\u0086\u0081\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00000\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002j\u0002\b\u0003j\u0002\b\u0004\u00a8\u0006\u0005"}, d2 = {"Lcom/carcam/platecheck/util/InterstitialPolicy$Trigger;", "", "(Ljava/lang/String;I)V", "SESSION_END", "LIST_MANAGED", "app_debug"})
    public static enum Trigger {
        /*public static final*/ SESSION_END /* = new SESSION_END() */,
        /*public static final*/ LIST_MANAGED /* = new LIST_MANAGED() */;
        
        Trigger() {
        }
        
        @org.jetbrains.annotations.NotNull()
        public static kotlin.enums.EnumEntries<com.carcam.platecheck.util.InterstitialPolicy.Trigger> getEntries() {
            return null;
        }
    }
}