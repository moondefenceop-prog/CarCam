package com.carcam.platecheck.util;

/**
 * Lightweight fallback for the single Hangul usage glyph on a modern one-line plate.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000^\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u0014\n\u0000\n\u0002\u0010\b\n\u0002\b\u0003\n\u0002\u0010 \n\u0002\u0010\f\n\u0000\n\u0002\u0010$\n\u0002\u0010\u0018\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\u0007\n\u0002\b\u0003\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0002\b\b\b\u00c6\u0002\u0018\u00002\u00020\u0001:\u00011B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0018\u0010\u0019\u001a\u00020\u001a2\u0006\u0010\u001b\u001a\u00020\u000e2\u0006\u0010\u001c\u001a\u00020\u000eH\u0002J\u000e\u0010\u001d\u001a\u00020\u001e2\u0006\u0010\u001f\u001a\u00020 J2\u0010!\u001a\u0004\u0018\u00010 2\u0006\u0010\"\u001a\u00020#2\u0006\u0010$\u001a\u00020\u00062\u0006\u0010%\u001a\u00020\u00062\u0006\u0010&\u001a\u00020\u00062\u0006\u0010\'\u001a\u00020\u0006H\u0002J \u0010(\u001a\u0004\u0018\u00010 2\u0006\u0010\"\u001a\u00020#2\u0006\u0010)\u001a\u00020*2\u0006\u0010+\u001a\u00020\u0006J\u0012\u0010,\u001a\u0004\u0018\u00010\u000e2\u0006\u0010-\u001a\u00020#H\u0002J\u0018\u0010.\u001a\u00020\u000e2\u0006\u0010/\u001a\u00020\u000b2\u0006\u00100\u001a\u00020\u0014H\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\u0006X\u0082T\u00a2\u0006\u0002\n\u0000R\u0014\u0010\t\u001a\b\u0012\u0004\u0012\u00020\u000b0\nX\u0082\u0004\u00a2\u0006\u0002\n\u0000R-\u0010\f\u001a\u0014\u0012\u0004\u0012\u00020\u000b\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u000e0\n0\r8BX\u0082\u0084\u0002\u00a2\u0006\f\n\u0004\b\u0011\u0010\u0012\u001a\u0004\b\u000f\u0010\u0010R)\u0010\u0013\u001a\u0010\u0012\f\u0012\n \u0015*\u0004\u0018\u00010\u00140\u00140\n8BX\u0082\u0084\u0002\u00a2\u0006\f\n\u0004\b\u0018\u0010\u0012\u001a\u0004\b\u0016\u0010\u0017\u00a8\u00062"}, d2 = {"Lcom/carcam/platecheck/util/PlateGlyphTemplateMatcher;", "", "()V", "CENTER_OFFSET_FACTORS", "", "H", "", "HALF_WIDTH_FACTORS", "W", "glyphs", "", "", "templates", "", "", "getTemplates", "()Ljava/util/Map;", "templates$delegate", "Lkotlin/Lazy;", "typefaces", "Landroid/graphics/Typeface;", "kotlin.jvm.PlatformType", "getTypefaces", "()Ljava/util/List;", "typefaces$delegate", "dice", "", "a", "b", "isConfident", "", "match", "Lcom/carcam/platecheck/util/PlateGlyphTemplateMatcher$Match;", "matchCrop", "bitmap", "Landroid/graphics/Bitmap;", "left", "top", "right", "bottom", "matchModernPlate", "lineBox", "Landroid/graphics/Rect;", "leadingDigits", "normalize", "source", "render", "char", "typeface", "Match", "app_debug"})
public final class PlateGlyphTemplateMatcher {
    private static final int W = 48;
    private static final int H = 72;
    @org.jetbrains.annotations.NotNull()
    private static final java.util.List<java.lang.Character> glyphs = null;
    @org.jetbrains.annotations.NotNull()
    private static final kotlin.Lazy typefaces$delegate = null;
    @org.jetbrains.annotations.NotNull()
    private static final kotlin.Lazy templates$delegate = null;
    @org.jetbrains.annotations.NotNull()
    private static final float[] CENTER_OFFSET_FACTORS = {-0.5F, -0.35F, -0.2F, -0.1F, 0.0F, 0.1F, 0.2F, 0.35F, 0.5F};
    @org.jetbrains.annotations.NotNull()
    private static final float[] HALF_WIDTH_FACTORS = {0.45F, 0.55F, 0.66F};
    @org.jetbrains.annotations.NotNull()
    public static final com.carcam.platecheck.util.PlateGlyphTemplateMatcher INSTANCE = null;
    
    private PlateGlyphTemplateMatcher() {
        super();
    }
    
    private final java.util.List<android.graphics.Typeface> getTypefaces() {
        return null;
    }
    
    private final java.util.Map<java.lang.Character, java.util.List<boolean[]>> getTemplates() {
        return null;
    }
    
    /**
     * [lineBox] is ML Kit's box for the full plate line. [leadingDigits] is normally 3.
     * Modern plates have 8 visual slots: 3 digits, one Hangul, and 4 digits.
     */
    @org.jetbrains.annotations.Nullable()
    public final com.carcam.platecheck.util.PlateGlyphTemplateMatcher.Match matchModernPlate(@org.jetbrains.annotations.NotNull()
    android.graphics.Bitmap bitmap, @org.jetbrains.annotations.NotNull()
    android.graphics.Rect lineBox, int leadingDigits) {
        return null;
    }
    
    private final com.carcam.platecheck.util.PlateGlyphTemplateMatcher.Match matchCrop(android.graphics.Bitmap bitmap, int left, int top, int right, int bottom) {
        return null;
    }
    
    public final boolean isConfident(@org.jetbrains.annotations.NotNull()
    com.carcam.platecheck.util.PlateGlyphTemplateMatcher.Match match) {
        return false;
    }
    
    private final boolean[] render(char p0_1526187, android.graphics.Typeface typeface) {
        return null;
    }
    
    private final boolean[] normalize(android.graphics.Bitmap source) {
        return null;
    }
    
    private final float dice(boolean[] a, boolean[] b) {
        return 0.0F;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000,\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\f\n\u0000\n\u0002\u0010\u0007\n\u0002\b\f\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0000\b\u0086\b\u0018\u00002\u00020\u0001B\u001d\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0007J\t\u0010\r\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u000e\u001a\u00020\u0005H\u00c6\u0003J\t\u0010\u000f\u001a\u00020\u0005H\u00c6\u0003J\'\u0010\u0010\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00052\b\b\u0002\u0010\u0006\u001a\u00020\u0005H\u00c6\u0001J\u0013\u0010\u0011\u001a\u00020\u00122\b\u0010\u0013\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u0014\u001a\u00020\u0015H\u00d6\u0001J\t\u0010\u0016\u001a\u00020\u0017H\u00d6\u0001R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\tR\u0011\u0010\u0006\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000bR\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\f\u0010\u000b\u00a8\u0006\u0018"}, d2 = {"Lcom/carcam/platecheck/util/PlateGlyphTemplateMatcher$Match;", "", "character", "", "score", "", "margin", "(CFF)V", "getCharacter", "()C", "getMargin", "()F", "getScore", "component1", "component2", "component3", "copy", "equals", "", "other", "hashCode", "", "toString", "", "app_debug"})
    public static final class Match {
        private final char character = '\u0000';
        private final float score = 0.0F;
        private final float margin = 0.0F;
        
        public Match(char character, float score, float margin) {
            super();
        }
        
        public final char getCharacter() {
            return '\u0000';
        }
        
        public final float getScore() {
            return 0.0F;
        }
        
        public final float getMargin() {
            return 0.0F;
        }
        
        public final char component1() {
            return '\u0000';
        }
        
        public final float component2() {
            return 0.0F;
        }
        
        public final float component3() {
            return 0.0F;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.carcam.platecheck.util.PlateGlyphTemplateMatcher.Match copy(char character, float score, float margin) {
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