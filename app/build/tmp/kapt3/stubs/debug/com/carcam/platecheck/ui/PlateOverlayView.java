package com.carcam.platecheck.ui;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000V\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0007\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0002\u0010\u000b\n\u0002\b\u0003\u0018\u00002\u00020\u0001:\u0001\"B\u001b\b\u0007\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\n\b\u0002\u0010\u0004\u001a\u0004\u0018\u00010\u0005\u00a2\u0006\u0002\u0010\u0006J\u0010\u0010\u0012\u001a\u00020\b2\u0006\u0010\u0013\u001a\u00020\u0014H\u0002J\u0006\u0010\u0015\u001a\u00020\u0016J\u0010\u0010\u0017\u001a\u00020\b2\u0006\u0010\u0013\u001a\u00020\u0014H\u0002J\u0010\u0010\u0018\u001a\u00020\u00162\u0006\u0010\u0019\u001a\u00020\u001aH\u0014J0\u0010\u001b\u001a\u00020\u00162 \u0010\u001c\u001a\u001c\u0012\u0018\u0012\u0016\u0012\u0004\u0012\u00020\u001e\u0012\u0004\u0012\u00020\u001f\u0012\u0006\u0012\u0004\u0018\u00010 0\u001d0\u00102\u0006\u0010!\u001a\u00020\u001eR\u000e\u0010\u0007\u001a\u00020\bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\n\u001a\u00020\bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000b\u001a\u00020\bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\f\u001a\u00020\bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\r\u001a\u00020\bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000e\u001a\u00020\bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0014\u0010\u000f\u001a\b\u0012\u0004\u0012\u00020\u00110\u0010X\u0082\u000e\u00a2\u0006\u0002\n\u0000\u00a8\u0006#"}, d2 = {"Lcom/carcam/platecheck/ui/PlateOverlayView;", "Landroid/view/View;", "context", "Landroid/content/Context;", "attrs", "Landroid/util/AttributeSet;", "(Landroid/content/Context;Landroid/util/AttributeSet;)V", "boxPaintPending", "Landroid/graphics/Paint;", "boxPaintRegistered", "boxPaintUnregistered", "labelBgPaintPending", "labelBgPaintRegistered", "labelBgPaintUnregistered", "labelTextPaint", "plateBoxes", "", "Lcom/carcam/platecheck/ui/PlateOverlayView$PlateBox;", "boxPaint", "paintColor", "", "clear", "", "labelBgPaint", "onDraw", "canvas", "Landroid/graphics/Canvas;", "setPlateBoxes", "boxes", "Lkotlin/Triple;", "Landroid/graphics/Rect;", "", "", "visibleRegion", "PlateBox", "app_debug"})
public final class PlateOverlayView extends android.view.View {
    @org.jetbrains.annotations.NotNull()
    private final android.graphics.Paint boxPaintPending = null;
    @org.jetbrains.annotations.NotNull()
    private final android.graphics.Paint boxPaintRegistered = null;
    @org.jetbrains.annotations.NotNull()
    private final android.graphics.Paint boxPaintUnregistered = null;
    @org.jetbrains.annotations.NotNull()
    private final android.graphics.Paint labelBgPaintPending = null;
    @org.jetbrains.annotations.NotNull()
    private final android.graphics.Paint labelBgPaintRegistered = null;
    @org.jetbrains.annotations.NotNull()
    private final android.graphics.Paint labelBgPaintUnregistered = null;
    @org.jetbrains.annotations.NotNull()
    private final android.graphics.Paint labelTextPaint = null;
    @org.jetbrains.annotations.NotNull()
    private java.util.List<com.carcam.platecheck.ui.PlateOverlayView.PlateBox> plateBoxes;
    
    @kotlin.jvm.JvmOverloads()
    public PlateOverlayView(@org.jetbrains.annotations.NotNull()
    android.content.Context context, @org.jetbrains.annotations.Nullable()
    android.util.AttributeSet attrs) {
        super(null);
    }
    
    public final void setPlateBoxes(@org.jetbrains.annotations.NotNull()
    java.util.List<kotlin.Triple<android.graphics.Rect, java.lang.String, java.lang.Boolean>> boxes, @org.jetbrains.annotations.NotNull()
    android.graphics.Rect visibleRegion) {
    }
    
    public final void clear() {
    }
    
    @java.lang.Override()
    protected void onDraw(@org.jetbrains.annotations.NotNull()
    android.graphics.Canvas canvas) {
    }
    
    private final android.graphics.Paint boxPaint(int paintColor) {
        return null;
    }
    
    private final android.graphics.Paint labelBgPaint(int paintColor) {
        return null;
    }
    
    @kotlin.jvm.JvmOverloads()
    public PlateOverlayView(@org.jetbrains.annotations.NotNull()
    android.content.Context context) {
        super(null);
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000&\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u000f\n\u0002\u0010\b\n\u0002\b\u0002\b\u0082\b\u0018\u00002\u00020\u0001B\u001f\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\b\u0010\u0006\u001a\u0004\u0018\u00010\u0007\u00a2\u0006\u0002\u0010\bJ\t\u0010\u000f\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\u0010\u001a\u00020\u0005H\u00c6\u0003J\u0010\u0010\u0011\u001a\u0004\u0018\u00010\u0007H\u00c6\u0003\u00a2\u0006\u0002\u0010\tJ.\u0010\u0012\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00052\n\b\u0002\u0010\u0006\u001a\u0004\u0018\u00010\u0007H\u00c6\u0001\u00a2\u0006\u0002\u0010\u0013J\u0013\u0010\u0014\u001a\u00020\u00072\b\u0010\u0015\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u0016\u001a\u00020\u0017H\u00d6\u0001J\t\u0010\u0018\u001a\u00020\u0005H\u00d6\u0001R\u0015\u0010\u0006\u001a\u0004\u0018\u00010\u0007\u00a2\u0006\n\n\u0002\u0010\n\u001a\u0004\b\u0006\u0010\tR\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u0011\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000e\u00a8\u0006\u0019"}, d2 = {"Lcom/carcam/platecheck/ui/PlateOverlayView$PlateBox;", "", "rect", "Landroid/graphics/RectF;", "text", "", "isRegistered", "", "(Landroid/graphics/RectF;Ljava/lang/String;Ljava/lang/Boolean;)V", "()Ljava/lang/Boolean;", "Ljava/lang/Boolean;", "getRect", "()Landroid/graphics/RectF;", "getText", "()Ljava/lang/String;", "component1", "component2", "component3", "copy", "(Landroid/graphics/RectF;Ljava/lang/String;Ljava/lang/Boolean;)Lcom/carcam/platecheck/ui/PlateOverlayView$PlateBox;", "equals", "other", "hashCode", "", "toString", "app_debug"})
    static final class PlateBox {
        @org.jetbrains.annotations.NotNull()
        private final android.graphics.RectF rect = null;
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String text = null;
        @org.jetbrains.annotations.Nullable()
        private final java.lang.Boolean isRegistered = null;
        
        public PlateBox(@org.jetbrains.annotations.NotNull()
        android.graphics.RectF rect, @org.jetbrains.annotations.NotNull()
        java.lang.String text, @org.jetbrains.annotations.Nullable()
        java.lang.Boolean isRegistered) {
            super();
        }
        
        @org.jetbrains.annotations.NotNull()
        public final android.graphics.RectF getRect() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String getText() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.Boolean isRegistered() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final android.graphics.RectF component1() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String component2() {
            return null;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.Boolean component3() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.carcam.platecheck.ui.PlateOverlayView.PlateBox copy(@org.jetbrains.annotations.NotNull()
        android.graphics.RectF rect, @org.jetbrains.annotations.NotNull()
        java.lang.String text, @org.jetbrains.annotations.Nullable()
        java.lang.Boolean isRegistered) {
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