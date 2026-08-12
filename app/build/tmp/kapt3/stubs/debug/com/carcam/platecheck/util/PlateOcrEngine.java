package com.carcam.platecheck.util;

/**
 * Shared plate-extraction logic used by both the live camera pipeline (MainActivity)
 * and the instrumented benchmark test, so benchmark results reflect production behavior.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u00006\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0018\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u0007\u001a\u00020\u0006H\u0002J\"\u0010\b\u001a\u0016\u0012\u0012\u0012\u0010\u0012\u0006\u0012\u0004\u0018\u00010\u0006\u0012\u0004\u0012\u00020\u000b0\n0\t2\u0006\u0010\f\u001a\u00020\rJ\u0014\u0010\u000e\u001a\b\u0012\u0004\u0012\u00020\u00060\t2\u0006\u0010\f\u001a\u00020\rJ(\u0010\u000f\u001a\u0014\u0012\u0010\u0012\u000e\u0012\u0004\u0012\u00020\u0006\u0012\u0004\u0012\u00020\u000b0\n0\t2\f\u0010\u0010\u001a\b\u0012\u0004\u0012\u00020\u00110\tH\u0002\u00a8\u0006\u0012"}, d2 = {"Lcom/carcam/platecheck/util/PlateOcrEngine;", "", "()V", "areStackedLines", "", "a", "Landroid/graphics/Rect;", "b", "extractPlates", "", "Lkotlin/Pair;", "", "visionText", "Lcom/google/mlkit/vision/text/Text;", "findAmbiguousDigitBlocks", "stackedLinePairs", "blocks", "Lcom/google/mlkit/vision/text/Text$TextBlock;", "app_debug"})
public final class PlateOcrEngine {
    @org.jetbrains.annotations.NotNull()
    public static final com.carcam.platecheck.util.PlateOcrEngine INSTANCE = null;
    
    private PlateOcrEngine() {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<kotlin.Pair<android.graphics.Rect, java.lang.String>> extractPlates(@org.jetbrains.annotations.NotNull()
    com.google.mlkit.vision.text.Text visionText) {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<android.graphics.Rect> findAmbiguousDigitBlocks(@org.jetbrains.annotations.NotNull()
    com.google.mlkit.vision.text.Text visionText) {
        return null;
    }
    
    private final java.util.List<kotlin.Pair<android.graphics.Rect, java.lang.String>> stackedLinePairs(java.util.List<? extends com.google.mlkit.vision.text.Text.TextBlock> blocks) {
        return null;
    }
    
    private final boolean areStackedLines(android.graphics.Rect a, android.graphics.Rect b) {
        return false;
    }
}