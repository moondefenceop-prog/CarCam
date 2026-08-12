package com.carcam.platecheck.util;

/**
 * On-device 40-class classifier for the plate usage glyph (용도기호), trained to cover ML Kit's
 * ㅓ-column blind spot (러→리/로/digit). ML Kit still detects the plate and reads the digits;
 * this only decides the single middle Hangul from the digit line's geometry.
 *
 * Preprocessing MUST match ml/train.py: grayscale slot crop → 48x48 → per-image standardization.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000d\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\u0014\n\u0002\b\u0002\n\u0002\u0010\u0019\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0011\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\b\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0002\b\b\b\u00c6\u0002\u0018\u00002\u00020\u0001:\u0001-B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J \u0010\u0014\u001a\u0004\u0018\u00010\u00152\u0006\u0010\u0016\u001a\u00020\u00172\u0006\u0010\u0018\u001a\u00020\u00192\u0006\u0010\u001a\u001a\u00020\u000bJ*\u0010\u001b\u001a\u0004\u0018\u00010\u00152\u0006\u0010\u0016\u001a\u00020\u00172\u0006\u0010\u0018\u001a\u00020\u00192\u0006\u0010\u001c\u001a\u00020\u000b2\u0006\u0010\u001d\u001a\u00020\u000bH\u0002J(\u0010\u001e\u001a\u0004\u0018\u00010\u00152\u0006\u0010\u0016\u001a\u00020\u00172\u0006\u0010\u0018\u001a\u00020\u00192\u0006\u0010\u001f\u001a\u00020\u000b2\u0006\u0010 \u001a\u00020\u000bJ\u000e\u0010!\u001a\u00020\"2\u0006\u0010#\u001a\u00020$J\u0006\u0010%\u001a\u00020&J:\u0010\'\u001a\u0004\u0018\u00010\u00152\u0006\u0010(\u001a\u00020\u00102\u0006\u0010\u0016\u001a\u00020\u00172\u0006\u0010)\u001a\u00020\u000b2\u0006\u0010*\u001a\u00020\u000b2\u0006\u0010+\u001a\u00020\u000b2\u0006\u0010,\u001a\u00020\u000bH\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\tX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\n\u001a\u00020\u000bX\u0082T\u00a2\u0006\u0002\n\u0000R\u0016\u0010\f\u001a\n \u000e*\u0004\u0018\u00010\r0\rX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u000f\u001a\u0004\u0018\u00010\u0010X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0016\u0010\u0011\u001a\b\u0012\u0004\u0012\u00020\u00060\u0012X\u0082\u0004\u00a2\u0006\u0004\n\u0002\u0010\u0013\u00a8\u0006."}, d2 = {"Lcom/carcam/platecheck/util/GlyphClassifier;", "", "()V", "ASSET", "", "CENTER_OFFSETS", "", "HALF_WIDTHS", "LABELS", "", "SIZE", "", "input", "Ljava/nio/ByteBuffer;", "kotlin.jvm.PlatformType", "interpreter", "Lorg/tensorflow/lite/Interpreter;", "output", "", "[[F", "classify", "Lcom/carcam/platecheck/util/GlyphClassifier$Result;", "bitmap", "Landroid/graphics/Bitmap;", "lineBox", "Landroid/graphics/Rect;", "leadingDigits", "classifyAt", "slotIndex", "totalSlots", "classifyInRead", "hangulIndex", "readLength", "init", "", "context", "Landroid/content/Context;", "isReady", "", "runOne", "itp", "left", "top", "right", "bottom", "Result", "app_debug"})
public final class GlyphClassifier {
    @org.jetbrains.annotations.NotNull()
    private static final char[] LABELS = null;
    private static final int SIZE = 48;
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String ASSET = "glyph_cnn.tflite";
    @org.jetbrains.annotations.NotNull()
    private static final float[] CENTER_OFFSETS = {-0.35F, -0.15F, 0.0F, 0.15F, 0.35F};
    @org.jetbrains.annotations.NotNull()
    private static final float[] HALF_WIDTHS = {0.5F, 0.62F};
    @kotlin.jvm.Volatile()
    @org.jetbrains.annotations.Nullable()
    private static volatile org.tensorflow.lite.Interpreter interpreter;
    private static final java.nio.ByteBuffer input = null;
    @org.jetbrains.annotations.NotNull()
    private static final float[][] output = null;
    @org.jetbrains.annotations.NotNull()
    public static final com.carcam.platecheck.util.GlyphClassifier INSTANCE = null;
    
    private GlyphClassifier() {
        super();
    }
    
    public final void init(@org.jetbrains.annotations.NotNull()
    android.content.Context context) {
    }
    
    public final boolean isReady() {
        return false;
    }
    
    /**
     * Numeric-only path: ML Kit dropped the Hangul, so the box spans (leadingDigits + 1 + 4) equal
     * slots and the Hangul sits at index [leadingDigits].
     */
    @org.jetbrains.annotations.Nullable()
    public final com.carcam.platecheck.util.GlyphClassifier.Result classify(@org.jetbrains.annotations.NotNull()
    android.graphics.Bitmap bitmap, @org.jetbrains.annotations.NotNull()
    android.graphics.Rect lineBox, int leadingDigits) {
        return null;
    }
    
    /**
     * Full-read path: ML Kit read the Hangul (possibly wrong), so [readLength] characters tightly
     * fill the box and the Hangul is at [hangulIndex]. Locating it from the actual read is far more
     * robust than width estimation when ML Kit splits the line into blocks.
     */
    @org.jetbrains.annotations.Nullable()
    public final com.carcam.platecheck.util.GlyphClassifier.Result classifyInRead(@org.jetbrains.annotations.NotNull()
    android.graphics.Bitmap bitmap, @org.jetbrains.annotations.NotNull()
    android.graphics.Rect lineBox, int hangulIndex, int readLength) {
        return null;
    }
    
    private final com.carcam.platecheck.util.GlyphClassifier.Result classifyAt(android.graphics.Bitmap bitmap, android.graphics.Rect lineBox, int slotIndex, int totalSlots) {
        return null;
    }
    
    private final com.carcam.platecheck.util.GlyphClassifier.Result runOne(org.tensorflow.lite.Interpreter itp, android.graphics.Bitmap bitmap, int left, int top, int right, int bottom) {
        return null;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000,\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\f\n\u0000\n\u0002\u0010\u0007\n\u0002\b\t\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0000\b\u0086\b\u0018\u00002\u00020\u0001B\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006J\t\u0010\u000b\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\f\u001a\u00020\u0005H\u00c6\u0003J\u001d\u0010\r\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u0005H\u00c6\u0001J\u0013\u0010\u000e\u001a\u00020\u000f2\b\u0010\u0010\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u0011\u001a\u00020\u0012H\u00d6\u0001J\t\u0010\u0013\u001a\u00020\u0014H\u00d6\u0001R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\bR\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\n\u00a8\u0006\u0015"}, d2 = {"Lcom/carcam/platecheck/util/GlyphClassifier$Result;", "", "character", "", "confidence", "", "(CF)V", "getCharacter", "()C", "getConfidence", "()F", "component1", "component2", "copy", "equals", "", "other", "hashCode", "", "toString", "", "app_debug"})
    public static final class Result {
        private final char character = '\u0000';
        private final float confidence = 0.0F;
        
        public Result(char character, float confidence) {
            super();
        }
        
        public final char getCharacter() {
            return '\u0000';
        }
        
        public final float getConfidence() {
            return 0.0F;
        }
        
        public final char component1() {
            return '\u0000';
        }
        
        public final float component2() {
            return 0.0F;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.carcam.platecheck.util.GlyphClassifier.Result copy(char character, float confidence) {
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