package com.carcam.platecheck;

/**
 * Benchmarks plate recognition accuracy + latency against a labeled photo set in
 * app/src/androidTest/assets/plates/ (filename, minus extension and trailing "_N", = ground truth plate).
 *
 * Run: gradle connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.carcam.platecheck.PlateRecognitionBenchmarkTest
 * Results (per-config summary + per-case CSV) are logged to logcat under tag "PlateBenchmark".
 */
@org.junit.runner.RunWith(value = androidx.test.ext.junit.runners.AndroidJUnit4.class)
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000d\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u0007\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0007\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\b\b\u0007\u0018\u0000 02\u00020\u0001:\u0004/012B\u0005\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\n\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\u000bH\u0002J\u001e\u0010\r\u001a\b\u0012\u0004\u0012\u00020\t0\u00062\u0006\u0010\u000e\u001a\u00020\u000b2\u0006\u0010\u000f\u001a\u00020\u0010H\u0002J\b\u0010\u0011\u001a\u00020\u0012H\u0007J\b\u0010\u0013\u001a\u00020\u0012H\u0007J\b\u0010\u0014\u001a\u00020\u0012H\u0007J\b\u0010\u0015\u001a\u00020\u0012H\u0007J\b\u0010\u0016\u001a\u00020\u0012H\u0007J\b\u0010\u0017\u001a\u00020\u0012H\u0007J\u0018\u0010\u0018\u001a\u00020\u000b2\u0006\u0010\u0019\u001a\u00020\u001a2\u0006\u0010\u001b\u001a\u00020\tH\u0002J\"\u0010\u001c\u001a\u0014\u0012\u0010\u0012\u000e\u0012\u0004\u0012\u00020\t\u0012\u0004\u0012\u00020\t0\u001d0\u00062\u0006\u0010\u0019\u001a\u00020\u001aH\u0002J\u0018\u0010\u001e\u001a\u00020\u000b2\u0006\u0010\u000e\u001a\u00020\u000b2\u0006\u0010\u001f\u001a\u00020 H\u0002J8\u0010!\u001a\b\u0012\u0004\u0012\u00020\"0\u00062\u0006\u0010\u0019\u001a\u00020\u001a2\u0006\u0010#\u001a\u00020\u00072\u0018\u0010$\u001a\u0014\u0012\u0010\u0012\u000e\u0012\u0004\u0012\u00020\t\u0012\u0004\u0012\u00020\t0\u001d0\u0006H\u0002J8\u0010%\u001a\b\u0012\u0004\u0012\u00020\"0\u00062\u0006\u0010\u0019\u001a\u00020\u001a2\u0006\u0010#\u001a\u00020\u00072\u0018\u0010$\u001a\u0014\u0012\u0010\u0012\u000e\u0012\u0004\u0012\u00020\t\u0012\u0004\u0012\u00020\t0\u001d0\u0006H\u0002J \u0010&\u001a\u00020\u00122\u0006\u0010\'\u001a\u00020\u000b2\u0006\u0010(\u001a\u00020)2\u0006\u0010*\u001a\u00020+H\u0002J\u0010\u0010,\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\u000bH\u0002J\u0010\u0010-\u001a\u00020\u000b2\u0006\u0010\'\u001a\u00020\u000bH\u0002J\u0010\u0010.\u001a\u00020\u000b2\u0006\u0010\'\u001a\u00020\u000bH\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082D\u00a2\u0006\u0002\n\u0000R\u0014\u0010\u0005\u001a\b\u0012\u0004\u0012\u00020\u00070\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\tX\u0082\u000e\u00a2\u0006\u0002\n\u0000\u00a8\u00063"}, d2 = {"Lcom/carcam/platecheck/PlateRecognitionBenchmarkTest;", "", "()V", "CNN_THRESHOLD", "", "configs", "", "Lcom/carcam/platecheck/PlateRecognitionBenchmarkTest$OcrConfig;", "glyphMode", "", "adaptiveBinary", "Landroid/graphics/Bitmap;", "gray", "applyTemplateFallback", "bitmap", "text", "Lcom/google/mlkit/vision/text/Text;", "benchmarkAllConfigs", "", "dumpGlyphCrops", "experimentBinarizedReOcr", "experimentCnnMiddle", "experimentReOcr", "harvestGlyphs", "loadBitmapWithExifRotation", "context", "Landroid/content/Context;", "assetPath", "loadLabeledCases", "Lkotlin/Pair;", "resizeToMaxDim", "maxDim", "", "runConfig", "Lcom/carcam/platecheck/PlateRecognitionBenchmarkTest$CaseResult;", "config", "cases", "runTwoPassConfig", "saveCrop", "src", "box", "Landroid/graphics/Rect;", "dest", "Ljava/io/File;", "stretchAndBlur", "toGray", "toGrayscale", "CaseResult", "Companion", "OcrConfig", "ReOcrStrategy", "app_debugAndroidTest"})
public final class PlateRecognitionBenchmarkTest {
    @org.jetbrains.annotations.NotNull()
    private java.lang.String glyphMode = "cnn";
    private final float CNN_THRESHOLD = 0.5F;
    @org.jetbrains.annotations.NotNull()
    private final java.util.List<com.carcam.platecheck.PlateRecognitionBenchmarkTest.OcrConfig> configs = null;
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String TAG = "PlateBenchmark";
    @org.jetbrains.annotations.NotNull()
    public static final com.carcam.platecheck.PlateRecognitionBenchmarkTest.Companion Companion = null;
    
    public PlateRecognitionBenchmarkTest() {
        super();
    }
    
    @org.junit.Test()
    public final void benchmarkAllConfigs() {
    }
    
    /**
     * Harvest labeled real glyph crops (same pipeline the app feeds the CNN) into the app's files
     * dir for fine-tuning. Each plate yields several offset crops. Filename: <label>__<file>__<i>.png
     * Pull with: adb exec-out run-as com.carcam.platecheck tar c -C files harvest > harvest.tar
     */
    @org.junit.Test()
    public final void harvestGlyphs() {
    }
    
    @org.junit.Test()
    public final void experimentCnnMiddle() {
    }
    
    /**
     * Feeds a CLEANED, full-plate crop (not the tiny glyph slot) to ML Kit: contrast-stretch,
     * adaptive binarization, and Sobel. Tests whether binarizing the plate before OCR recovers '러'.
     * Run: adb ... class=...#experimentBinarizedReOcr
     */
    @org.junit.Ignore(value = "Diagnostic only; run explicitly. Showed ML Kit itself never reads this \'\ub7ec\' (reads \'\ub9ac\'/digit).")
    @org.junit.Test()
    public final void experimentBinarizedReOcr() {
    }
    
    private final android.graphics.Bitmap toGray(android.graphics.Bitmap src) {
        return null;
    }
    
    private final android.graphics.Bitmap stretchAndBlur(android.graphics.Bitmap gray) {
        return null;
    }
    
    private final android.graphics.Bitmap adaptiveBinary(android.graphics.Bitmap gray) {
        return null;
    }
    
    @org.junit.Ignore(value = "Diagnostic only; run explicitly. Proved ML Kit re-OCR cannot recover this \'\ub7ec\' under moire.")
    @org.junit.Test()
    public final void experimentReOcr() {
    }
    
    /**
     * Diagnostic: dump the exact Hangul-slot crops the template matcher evaluates, so we can eyeball
     * whether the geometry actually isolates '러' or cuts off its vertical vowel stroke.
     * Saves PNGs to the test app's external files dir; pull with adb afterwards.
     */
    @org.junit.Ignore(value = "Diagnostic only; run explicitly. Dumps Hangul-slot crops for offline inspection.")
    @org.junit.Test()
    public final void dumpGlyphCrops() {
    }
    
    private final void saveCrop(android.graphics.Bitmap src, android.graphics.Rect box, java.io.File dest) {
    }
    
    private final java.util.List<com.carcam.platecheck.PlateRecognitionBenchmarkTest.CaseResult> runConfig(android.content.Context context, com.carcam.platecheck.PlateRecognitionBenchmarkTest.OcrConfig config, java.util.List<kotlin.Pair<java.lang.String, java.lang.String>> cases) {
        return null;
    }
    
    private final java.util.List<com.carcam.platecheck.PlateRecognitionBenchmarkTest.CaseResult> runTwoPassConfig(android.content.Context context, com.carcam.platecheck.PlateRecognitionBenchmarkTest.OcrConfig config, java.util.List<kotlin.Pair<java.lang.String, java.lang.String>> cases) {
        return null;
    }
    
    private final java.util.List<kotlin.Pair<java.lang.String, java.lang.String>> loadLabeledCases(android.content.Context context) {
        return null;
    }
    
    private final java.util.List<java.lang.String> applyTemplateFallback(android.graphics.Bitmap bitmap, com.google.mlkit.vision.text.Text text) {
        return null;
    }
    
    private final android.graphics.Bitmap loadBitmapWithExifRotation(android.content.Context context, java.lang.String assetPath) {
        return null;
    }
    
    private final android.graphics.Bitmap resizeToMaxDim(android.graphics.Bitmap bitmap, int maxDim) {
        return null;
    }
    
    private final android.graphics.Bitmap toGrayscale(android.graphics.Bitmap src) {
        return null;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000*\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010\t\n\u0002\b\u0017\n\u0002\u0010\b\n\u0002\b\u0002\b\u0082\b\u0018\u00002\u00020\u0001BA\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0003\u0012\b\u0010\u0005\u001a\u0004\u0018\u00010\u0003\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u0012\u0006\u0010\b\u001a\u00020\u0007\u0012\u0006\u0010\t\u001a\u00020\n\u0012\b\b\u0002\u0010\u000b\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\fJ\t\u0010\u0017\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u0018\u001a\u00020\u0003H\u00c6\u0003J\u000b\u0010\u0019\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003J\t\u0010\u001a\u001a\u00020\u0007H\u00c6\u0003J\t\u0010\u001b\u001a\u00020\u0007H\u00c6\u0003J\t\u0010\u001c\u001a\u00020\nH\u00c6\u0003J\t\u0010\u001d\u001a\u00020\u0003H\u00c6\u0003JQ\u0010\u001e\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00032\n\b\u0002\u0010\u0005\u001a\u0004\u0018\u00010\u00032\b\b\u0002\u0010\u0006\u001a\u00020\u00072\b\b\u0002\u0010\b\u001a\u00020\u00072\b\b\u0002\u0010\t\u001a\u00020\n2\b\b\u0002\u0010\u000b\u001a\u00020\u0003H\u00c6\u0001J\u0013\u0010\u001f\u001a\u00020\u00072\b\u0010 \u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010!\u001a\u00020\"H\u00d6\u0001J\t\u0010#\u001a\u00020\u0003H\u00d6\u0001R\u0013\u0010\u0005\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000eR\u0011\u0010\b\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u0010R\u0011\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u0010R\u0011\u0010\u0004\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0012\u0010\u000eR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0013\u0010\u000eR\u0011\u0010\t\u001a\u00020\n\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0014\u0010\u0015R\u0011\u0010\u000b\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0016\u0010\u000e\u00a8\u0006$"}, d2 = {"Lcom/carcam/platecheck/PlateRecognitionBenchmarkTest$CaseResult;", "", "file", "", "expected", "actual", "exactMatch", "", "digitMatch", "latencyMs", "", "rawText", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ZZJLjava/lang/String;)V", "getActual", "()Ljava/lang/String;", "getDigitMatch", "()Z", "getExactMatch", "getExpected", "getFile", "getLatencyMs", "()J", "getRawText", "component1", "component2", "component3", "component4", "component5", "component6", "component7", "copy", "equals", "other", "hashCode", "", "toString", "app_debugAndroidTest"})
    static final class CaseResult {
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String file = null;
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String expected = null;
        @org.jetbrains.annotations.Nullable()
        private final java.lang.String actual = null;
        private final boolean exactMatch = false;
        private final boolean digitMatch = false;
        private final long latencyMs = 0L;
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String rawText = null;
        
        public CaseResult(@org.jetbrains.annotations.NotNull()
        java.lang.String file, @org.jetbrains.annotations.NotNull()
        java.lang.String expected, @org.jetbrains.annotations.Nullable()
        java.lang.String actual, boolean exactMatch, boolean digitMatch, long latencyMs, @org.jetbrains.annotations.NotNull()
        java.lang.String rawText) {
            super();
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String getFile() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String getExpected() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.String getActual() {
            return null;
        }
        
        public final boolean getExactMatch() {
            return false;
        }
        
        public final boolean getDigitMatch() {
            return false;
        }
        
        public final long getLatencyMs() {
            return 0L;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String getRawText() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String component1() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String component2() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.String component3() {
            return null;
        }
        
        public final boolean component4() {
            return false;
        }
        
        public final boolean component5() {
            return false;
        }
        
        public final long component6() {
            return 0L;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String component7() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.carcam.platecheck.PlateRecognitionBenchmarkTest.CaseResult copy(@org.jetbrains.annotations.NotNull()
        java.lang.String file, @org.jetbrains.annotations.NotNull()
        java.lang.String expected, @org.jetbrains.annotations.Nullable()
        java.lang.String actual, boolean exactMatch, boolean digitMatch, long latencyMs, @org.jetbrains.annotations.NotNull()
        java.lang.String rawText) {
            return null;
        }
        
        @java.lang.Override()
        public boolean equals(@org.jetbrains.annotations.Nullable()
        java.lang.Object other) {
            return false;
        }
        
        @java.lang.Override()
        public int hashCode() {
            return 0;
        }
        
        @java.lang.Override()
        @org.jetbrains.annotations.NotNull()
        public java.lang.String toString() {
            return null;
        }
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0012\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0005"}, d2 = {"Lcom/carcam/platecheck/PlateRecognitionBenchmarkTest$Companion;", "", "()V", "TAG", "", "app_debugAndroidTest"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000$\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u0007\n\u0002\b!\b\u0082\b\u0018\u00002\u00020\u0001BU\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\b\b\u0002\u0010\u0006\u001a\u00020\u0007\u0012\n\b\u0002\u0010\b\u001a\u0004\u0018\u00010\t\u0012\b\b\u0002\u0010\n\u001a\u00020\u0007\u0012\b\b\u0002\u0010\u000b\u001a\u00020\u0005\u0012\n\b\u0002\u0010\f\u001a\u0004\u0018\u00010\t\u0012\b\b\u0002\u0010\r\u001a\u00020\u0007\u00a2\u0006\u0002\u0010\u000eJ\t\u0010\u001c\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u001d\u001a\u00020\u0005H\u00c6\u0003J\t\u0010\u001e\u001a\u00020\u0007H\u00c6\u0003J\u0010\u0010\u001f\u001a\u0004\u0018\u00010\tH\u00c6\u0003\u00a2\u0006\u0002\u0010\u0010J\t\u0010 \u001a\u00020\u0007H\u00c6\u0003J\t\u0010!\u001a\u00020\u0005H\u00c6\u0003J\u0010\u0010\"\u001a\u0004\u0018\u00010\tH\u00c6\u0003\u00a2\u0006\u0002\u0010\u0010J\t\u0010#\u001a\u00020\u0007H\u00c6\u0003Jb\u0010$\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00052\b\b\u0002\u0010\u0006\u001a\u00020\u00072\n\b\u0002\u0010\b\u001a\u0004\u0018\u00010\t2\b\b\u0002\u0010\n\u001a\u00020\u00072\b\b\u0002\u0010\u000b\u001a\u00020\u00052\n\b\u0002\u0010\f\u001a\u0004\u0018\u00010\t2\b\b\u0002\u0010\r\u001a\u00020\u0007H\u00c6\u0001\u00a2\u0006\u0002\u0010%J\u0013\u0010&\u001a\u00020\u00072\b\u0010\'\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010(\u001a\u00020\u0005H\u00d6\u0001J\t\u0010)\u001a\u00020\u0003H\u00d6\u0001R\u0015\u0010\b\u001a\u0004\u0018\u00010\t\u00a2\u0006\n\n\u0002\u0010\u0011\u001a\u0004\b\u000f\u0010\u0010R\u0011\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0012\u0010\u0013R\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0014\u0010\u0015R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0016\u0010\u0017R\u0011\u0010\n\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0018\u0010\u0013R\u0015\u0010\f\u001a\u0004\u0018\u00010\t\u00a2\u0006\n\n\u0002\u0010\u0011\u001a\u0004\b\u0019\u0010\u0010R\u0011\u0010\r\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001a\u0010\u0013R\u0011\u0010\u000b\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u001b\u0010\u0015\u00a8\u0006*"}, d2 = {"Lcom/carcam/platecheck/PlateRecognitionBenchmarkTest$OcrConfig;", "", "name", "", "maxDim", "", "grayscale", "", "contrast", "", "twoPass", "zoomTargetDim", "zoomContrast", "zoomGrayscale", "(Ljava/lang/String;IZLjava/lang/Float;ZILjava/lang/Float;Z)V", "getContrast", "()Ljava/lang/Float;", "Ljava/lang/Float;", "getGrayscale", "()Z", "getMaxDim", "()I", "getName", "()Ljava/lang/String;", "getTwoPass", "getZoomContrast", "getZoomGrayscale", "getZoomTargetDim", "component1", "component2", "component3", "component4", "component5", "component6", "component7", "component8", "copy", "(Ljava/lang/String;IZLjava/lang/Float;ZILjava/lang/Float;Z)Lcom/carcam/platecheck/PlateRecognitionBenchmarkTest$OcrConfig;", "equals", "other", "hashCode", "toString", "app_debugAndroidTest"})
    static final class OcrConfig {
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String name = null;
        private final int maxDim = 0;
        private final boolean grayscale = false;
        @org.jetbrains.annotations.Nullable()
        private final java.lang.Float contrast = null;
        private final boolean twoPass = false;
        private final int zoomTargetDim = 0;
        @org.jetbrains.annotations.Nullable()
        private final java.lang.Float zoomContrast = null;
        private final boolean zoomGrayscale = false;
        
        public OcrConfig(@org.jetbrains.annotations.NotNull()
        java.lang.String name, int maxDim, boolean grayscale, @org.jetbrains.annotations.Nullable()
        java.lang.Float contrast, boolean twoPass, int zoomTargetDim, @org.jetbrains.annotations.Nullable()
        java.lang.Float zoomContrast, boolean zoomGrayscale) {
            super();
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String getName() {
            return null;
        }
        
        public final int getMaxDim() {
            return 0;
        }
        
        public final boolean getGrayscale() {
            return false;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.Float getContrast() {
            return null;
        }
        
        public final boolean getTwoPass() {
            return false;
        }
        
        public final int getZoomTargetDim() {
            return 0;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.Float getZoomContrast() {
            return null;
        }
        
        public final boolean getZoomGrayscale() {
            return false;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String component1() {
            return null;
        }
        
        public final int component2() {
            return 0;
        }
        
        public final boolean component3() {
            return false;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.Float component4() {
            return null;
        }
        
        public final boolean component5() {
            return false;
        }
        
        public final int component6() {
            return 0;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.Float component7() {
            return null;
        }
        
        public final boolean component8() {
            return false;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.carcam.platecheck.PlateRecognitionBenchmarkTest.OcrConfig copy(@org.jetbrains.annotations.NotNull()
        java.lang.String name, int maxDim, boolean grayscale, @org.jetbrains.annotations.Nullable()
        java.lang.Float contrast, boolean twoPass, int zoomTargetDim, @org.jetbrains.annotations.Nullable()
        java.lang.Float zoomContrast, boolean zoomGrayscale) {
            return null;
        }
        
        @java.lang.Override()
        public boolean equals(@org.jetbrains.annotations.Nullable()
        java.lang.Object other) {
            return false;
        }
        
        @java.lang.Override()
        public int hashCode() {
            return 0;
        }
        
        @java.lang.Override()
        @org.jetbrains.annotations.NotNull()
        public java.lang.String toString() {
            return null;
        }
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000$\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u0007\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0019\b\u0082\b\u0018\u00002\u00020\u0001B1\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\b\u0010\u0006\u001a\u0004\u0018\u00010\u0007\u0012\u0006\u0010\b\u001a\u00020\t\u0012\b\b\u0002\u0010\n\u001a\u00020\u0007\u00a2\u0006\u0002\u0010\u000bJ\t\u0010\u0017\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u0018\u001a\u00020\u0005H\u00c6\u0003J\u0010\u0010\u0019\u001a\u0004\u0018\u00010\u0007H\u00c6\u0003\u00a2\u0006\u0002\u0010\rJ\t\u0010\u001a\u001a\u00020\tH\u00c6\u0003J\t\u0010\u001b\u001a\u00020\u0007H\u00c6\u0003JB\u0010\u001c\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00052\n\b\u0002\u0010\u0006\u001a\u0004\u0018\u00010\u00072\b\b\u0002\u0010\b\u001a\u00020\t2\b\b\u0002\u0010\n\u001a\u00020\u0007H\u00c6\u0001\u00a2\u0006\u0002\u0010\u001dJ\u0013\u0010\u001e\u001a\u00020\t2\b\u0010\u001f\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010 \u001a\u00020\u0005H\u00d6\u0001J\t\u0010!\u001a\u00020\u0003H\u00d6\u0001R\u0015\u0010\u0006\u001a\u0004\u0018\u00010\u0007\u00a2\u0006\n\n\u0002\u0010\u000e\u001a\u0004\b\f\u0010\rR\u0011\u0010\b\u001a\u00020\t\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u0010R\u0011\u0010\n\u001a\u00020\u0007\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u0012R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0013\u0010\u0014R\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0015\u0010\u0016\u00a8\u0006\""}, d2 = {"Lcom/carcam/platecheck/PlateRecognitionBenchmarkTest$ReOcrStrategy;", "", "name", "", "zoomDim", "", "contrast", "", "grayscale", "", "moireBlur", "(Ljava/lang/String;ILjava/lang/Float;ZF)V", "getContrast", "()Ljava/lang/Float;", "Ljava/lang/Float;", "getGrayscale", "()Z", "getMoireBlur", "()F", "getName", "()Ljava/lang/String;", "getZoomDim", "()I", "component1", "component2", "component3", "component4", "component5", "copy", "(Ljava/lang/String;ILjava/lang/Float;ZF)Lcom/carcam/platecheck/PlateRecognitionBenchmarkTest$ReOcrStrategy;", "equals", "other", "hashCode", "toString", "app_debugAndroidTest"})
    static final class ReOcrStrategy {
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String name = null;
        private final int zoomDim = 0;
        @org.jetbrains.annotations.Nullable()
        private final java.lang.Float contrast = null;
        private final boolean grayscale = false;
        private final float moireBlur = 0.0F;
        
        public ReOcrStrategy(@org.jetbrains.annotations.NotNull()
        java.lang.String name, int zoomDim, @org.jetbrains.annotations.Nullable()
        java.lang.Float contrast, boolean grayscale, float moireBlur) {
            super();
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String getName() {
            return null;
        }
        
        public final int getZoomDim() {
            return 0;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.Float getContrast() {
            return null;
        }
        
        public final boolean getGrayscale() {
            return false;
        }
        
        public final float getMoireBlur() {
            return 0.0F;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String component1() {
            return null;
        }
        
        public final int component2() {
            return 0;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.Float component3() {
            return null;
        }
        
        public final boolean component4() {
            return false;
        }
        
        public final float component5() {
            return 0.0F;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.carcam.platecheck.PlateRecognitionBenchmarkTest.ReOcrStrategy copy(@org.jetbrains.annotations.NotNull()
        java.lang.String name, int zoomDim, @org.jetbrains.annotations.Nullable()
        java.lang.Float contrast, boolean grayscale, float moireBlur) {
            return null;
        }
        
        @java.lang.Override()
        public boolean equals(@org.jetbrains.annotations.Nullable()
        java.lang.Object other) {
            return false;
        }
        
        @java.lang.Override()
        public int hashCode() {
            return 0;
        }
        
        @java.lang.Override()
        @org.jetbrains.annotations.NotNull()
        public java.lang.String toString() {
            return null;
        }
    }
}