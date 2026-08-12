package com.carcam.platecheck.util;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000@\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0007\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0007\n\u0002\u0010\u0012\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0016\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u00042\u0006\u0010\u0006\u001a\u00020\u0007J*\u0010\b\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u00042\u0006\u0010\t\u001a\u00020\n2\b\b\u0002\u0010\u000b\u001a\u00020\u00072\b\b\u0002\u0010\f\u001a\u00020\rJ\u0018\u0010\u000e\u001a\u0004\u0018\u00010\u00042\u0006\u0010\u000f\u001a\u00020\u00102\u0006\u0010\u0011\u001a\u00020\rJ&\u0010\u0012\u001a\u00020\n2\u0006\u0010\u0013\u001a\u00020\n2\u0006\u0010\u0014\u001a\u00020\r2\u0006\u0010\u0015\u001a\u00020\r2\u0006\u0010\u0011\u001a\u00020\rJ\u000e\u0010\u0016\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u0004J\u0010\u0010\u0017\u001a\u00020\u00182\u0006\u0010\u0019\u001a\u00020\u001aH\u0002\u00a8\u0006\u001b"}, d2 = {"Lcom/carcam/platecheck/util/ImageUtils;", "", "()V", "adjustContrast", "Landroid/graphics/Bitmap;", "src", "contrast", "", "cropAndUpscale", "box", "Landroid/graphics/Rect;", "paddingRatio", "targetMaxDim", "", "imageProxyToUprightBitmap", "imageProxy", "Landroidx/camera/core/ImageProxy;", "rotationDegrees", "rotateRect", "rect", "bufferWidth", "bufferHeight", "toGrayscale", "yuv420888ToNv21", "", "image", "Landroid/media/Image;", "app_debug"})
public final class ImageUtils {
    @org.jetbrains.annotations.NotNull()
    public static final com.carcam.platecheck.util.ImageUtils INSTANCE = null;
    
    private ImageUtils() {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final android.graphics.Bitmap adjustContrast(@org.jetbrains.annotations.NotNull()
    android.graphics.Bitmap src, float contrast) {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final android.graphics.Rect rotateRect(@org.jetbrains.annotations.NotNull()
    android.graphics.Rect rect, int bufferWidth, int bufferHeight, int rotationDegrees) {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final android.graphics.Bitmap toGrayscale(@org.jetbrains.annotations.NotNull()
    android.graphics.Bitmap src) {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final android.graphics.Bitmap cropAndUpscale(@org.jetbrains.annotations.NotNull()
    android.graphics.Bitmap src, @org.jetbrains.annotations.NotNull()
    android.graphics.Rect box, float paddingRatio, int targetMaxDim) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final android.graphics.Bitmap imageProxyToUprightBitmap(@org.jetbrains.annotations.NotNull()
    androidx.camera.core.ImageProxy imageProxy, int rotationDegrees) {
        return null;
    }
    
    private final byte[] yuv420888ToNv21(android.media.Image image) {
        return null;
    }
}