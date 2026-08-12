package com.carcam.platecheck.util;

/**
 * Reads a resident-vehicle list out of a spreadsheet the operator already keeps.
 *
 * Supports .csv/.txt and .xlsx. An .xlsx file is a ZIP of XML parts, so it is parsed directly
 * rather than pulling in Apache POI, which would add tens of megabytes and a method-count
 * problem to an app whose only use for it is reading two columns.
 *
 * Column convention: first column = plate number, second (optional) = note. A header row is
 * skipped when its first cell does not look like a plate.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000L\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0002\b\u0002\n\u0002\u0010\u0012\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\f\n\u0002\b\u0003\b\u00c6\u0002\u0018\u00002\u00020\u0001:\u0002\u001e\u001fB\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0012\u0010\u0003\u001a\u00020\u00042\b\u0010\u0005\u001a\u0004\u0018\u00010\u0006H\u0002J\u0018\u0010\u0007\u001a\u00020\u00062\u0006\u0010\b\u001a\u00020\t2\u0006\u0010\n\u001a\u00020\u000bH\u0002J\u001c\u0010\f\u001a\u00020\r2\u0012\u0010\u000e\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00060\u000f0\u000fH\u0002J\u0016\u0010\u0010\u001a\b\u0012\u0004\u0012\u00020\u00060\u000f2\u0006\u0010\u0011\u001a\u00020\u0012H\u0002J*\u0010\u0013\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00060\u000f0\u000f2\u0006\u0010\u0011\u001a\u00020\u00122\f\u0010\u0014\u001a\b\u0012\u0004\u0012\u00020\u00060\u000fH\u0002J\u0016\u0010\u0015\u001a\u00020\r2\u0006\u0010\b\u001a\u00020\t2\u0006\u0010\n\u001a\u00020\u000bJ\u001c\u0010\u0016\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00060\u000f0\u000f2\u0006\u0010\u0017\u001a\u00020\u0018H\u0002J\u001c\u0010\u0019\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00060\u000f0\u000f2\u0006\u0010\u0017\u001a\u00020\u0018H\u0002J\u001e\u0010\u001a\u001a\b\u0012\u0004\u0012\u00020\u00060\u000f2\u0006\u0010\u001b\u001a\u00020\u00062\u0006\u0010\u001c\u001a\u00020\u001dH\u0002\u00a8\u0006 "}, d2 = {"Lcom/carcam/platecheck/util/PlateImporter;", "", "()V", "columnIndex", "", "ref", "", "displayName", "context", "Landroid/content/Context;", "uri", "Landroid/net/Uri;", "normalize", "Lcom/carcam/platecheck/util/PlateImporter$Result;", "raw", "", "parseSharedStrings", "bytes", "", "parseSheet", "shared", "read", "readDelimited", "input", "Ljava/io/InputStream;", "readXlsx", "splitCsvLine", "line", "delim", "", "Result", "Row", "app_debug"})
public final class PlateImporter {
    @org.jetbrains.annotations.NotNull()
    public static final com.carcam.platecheck.util.PlateImporter INSTANCE = null;
    
    private PlateImporter() {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.carcam.platecheck.util.PlateImporter.Result read(@org.jetbrains.annotations.NotNull()
    android.content.Context context, @org.jetbrains.annotations.NotNull()
    android.net.Uri uri) {
        return null;
    }
    
    private final java.lang.String displayName(android.content.Context context, android.net.Uri uri) {
        return null;
    }
    
    /**
     * Keep rows whose first cell parses as a plate; drop headers, blank lines and totals.
     */
    private final com.carcam.platecheck.util.PlateImporter.Result normalize(java.util.List<? extends java.util.List<java.lang.String>> raw) {
        return null;
    }
    
    private final java.util.List<java.util.List<java.lang.String>> readDelimited(java.io.InputStream input) {
        return null;
    }
    
    /**
     * Split one CSV line, honouring quoted cells so a note containing the delimiter survives.
     */
    private final java.util.List<java.lang.String> splitCsvLine(java.lang.String line, char delim) {
        return null;
    }
    
    private final java.util.List<java.util.List<java.lang.String>> readXlsx(java.io.InputStream input) {
        return null;
    }
    
    private final java.util.List<java.lang.String> parseSharedStrings(byte[] bytes) {
        return null;
    }
    
    private final java.util.List<java.util.List<java.lang.String>> parseSheet(byte[] bytes, java.util.List<java.lang.String> shared) {
        return null;
    }
    
    /**
     * "B7" -> 1. Zero-based column from an A1-style cell reference.
     */
    private final int columnIndex(java.lang.String ref) {
        return 0;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000*\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0002\b\f\n\u0002\u0010\u000b\n\u0002\b\u0004\b\u0086\b\u0018\u00002\u00020\u0001B\'\u0012\f\u0010\u0002\u001a\b\u0012\u0004\u0012\u00020\u00040\u0003\u0012\u0006\u0010\u0005\u001a\u00020\u0006\u0012\n\b\u0002\u0010\u0007\u001a\u0004\u0018\u00010\b\u00a2\u0006\u0002\u0010\tJ\u000f\u0010\u0010\u001a\b\u0012\u0004\u0012\u00020\u00040\u0003H\u00c6\u0003J\t\u0010\u0011\u001a\u00020\u0006H\u00c6\u0003J\u000b\u0010\u0012\u001a\u0004\u0018\u00010\bH\u00c6\u0003J/\u0010\u0013\u001a\u00020\u00002\u000e\b\u0002\u0010\u0002\u001a\b\u0012\u0004\u0012\u00020\u00040\u00032\b\b\u0002\u0010\u0005\u001a\u00020\u00062\n\b\u0002\u0010\u0007\u001a\u0004\u0018\u00010\bH\u00c6\u0001J\u0013\u0010\u0014\u001a\u00020\u00152\b\u0010\u0016\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u0017\u001a\u00020\u0006H\u00d6\u0001J\t\u0010\u0018\u001a\u00020\bH\u00d6\u0001R\u0013\u0010\u0007\u001a\u0004\u0018\u00010\b\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000bR\u0017\u0010\u0002\u001a\b\u0012\u0004\u0012\u00020\u00040\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\f\u0010\rR\u0011\u0010\u0005\u001a\u00020\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\u000f\u00a8\u0006\u0019"}, d2 = {"Lcom/carcam/platecheck/util/PlateImporter$Result;", "", "rows", "", "Lcom/carcam/platecheck/util/PlateImporter$Row;", "skipped", "", "error", "", "(Ljava/util/List;ILjava/lang/String;)V", "getError", "()Ljava/lang/String;", "getRows", "()Ljava/util/List;", "getSkipped", "()I", "component1", "component2", "component3", "copy", "equals", "", "other", "hashCode", "toString", "app_debug"})
    public static final class Result {
        @org.jetbrains.annotations.NotNull()
        private final java.util.List<com.carcam.platecheck.util.PlateImporter.Row> rows = null;
        private final int skipped = 0;
        @org.jetbrains.annotations.Nullable()
        private final java.lang.String error = null;
        
        public Result(@org.jetbrains.annotations.NotNull()
        java.util.List<com.carcam.platecheck.util.PlateImporter.Row> rows, int skipped, @org.jetbrains.annotations.Nullable()
        java.lang.String error) {
            super();
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.util.List<com.carcam.platecheck.util.PlateImporter.Row> getRows() {
            return null;
        }
        
        public final int getSkipped() {
            return 0;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.String getError() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.util.List<com.carcam.platecheck.util.PlateImporter.Row> component1() {
            return null;
        }
        
        public final int component2() {
            return 0;
        }
        
        @org.jetbrains.annotations.Nullable()
        public final java.lang.String component3() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.carcam.platecheck.util.PlateImporter.Result copy(@org.jetbrains.annotations.NotNull()
        java.util.List<com.carcam.platecheck.util.PlateImporter.Row> rows, int skipped, @org.jetbrains.annotations.Nullable()
        java.lang.String error) {
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
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\"\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0002\b\t\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0002\b\u0086\b\u0018\u00002\u00020\u0001B\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0005J\t\u0010\t\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\n\u001a\u00020\u0003H\u00c6\u0003J\u001d\u0010\u000b\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u0003H\u00c6\u0001J\u0013\u0010\f\u001a\u00020\r2\b\u0010\u000e\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u000f\u001a\u00020\u0010H\u00d6\u0001J\t\u0010\u0011\u001a\u00020\u0003H\u00d6\u0001R\u0011\u0010\u0004\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\u0007\u00a8\u0006\u0012"}, d2 = {"Lcom/carcam/platecheck/util/PlateImporter$Row;", "", "plate", "", "note", "(Ljava/lang/String;Ljava/lang/String;)V", "getNote", "()Ljava/lang/String;", "getPlate", "component1", "component2", "copy", "equals", "", "other", "hashCode", "", "toString", "app_debug"})
    public static final class Row {
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String plate = null;
        @org.jetbrains.annotations.NotNull()
        private final java.lang.String note = null;
        
        public Row(@org.jetbrains.annotations.NotNull()
        java.lang.String plate, @org.jetbrains.annotations.NotNull()
        java.lang.String note) {
            super();
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String getPlate() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final java.lang.String getNote() {
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
        
        @org.jetbrains.annotations.NotNull()
        public final com.carcam.platecheck.util.PlateImporter.Row copy(@org.jetbrains.annotations.NotNull()
        java.lang.String plate, @org.jetbrains.annotations.NotNull()
        java.lang.String note) {
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