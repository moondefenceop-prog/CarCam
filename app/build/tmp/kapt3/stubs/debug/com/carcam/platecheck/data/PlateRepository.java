package com.carcam.platecheck.data;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000D\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\b\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\u0018\u00002\u00020\u0001:\u0001\u001dB\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u001e\u0010\r\u001a\u00020\u000e2\u0006\u0010\u000f\u001a\u00020\u00102\u0006\u0010\u0011\u001a\u00020\u0010H\u0086@\u00a2\u0006\u0002\u0010\u0012J\u0018\u0010\u0013\u001a\u0004\u0018\u00010\b2\u0006\u0010\u000f\u001a\u00020\u0010H\u0086@\u00a2\u0006\u0002\u0010\u0014J\u0016\u0010\u0015\u001a\u00020\u000e2\u0006\u0010\u0016\u001a\u00020\bH\u0086@\u00a2\u0006\u0002\u0010\u0017J\u001c\u0010\u0018\u001a\u00020\u00192\f\u0010\u001a\u001a\b\u0012\u0004\u0012\u00020\u001b0\u0007H\u0086@\u00a2\u0006\u0002\u0010\u001cR\u001d\u0010\u0005\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\b0\u00070\u0006\u00a2\u0006\b\n\u0000\u001a\u0004\b\t\u0010\nR\u000e\u0010\u000b\u001a\u00020\fX\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u001e"}, d2 = {"Lcom/carcam/platecheck/data/PlateRepository;", "", "context", "Landroid/content/Context;", "(Landroid/content/Context;)V", "allPlates", "Landroidx/lifecycle/LiveData;", "", "Lcom/carcam/platecheck/data/PlateEntity;", "getAllPlates", "()Landroidx/lifecycle/LiveData;", "dao", "Lcom/carcam/platecheck/data/PlateDao;", "addPlate", "", "plateNumber", "", "note", "(Ljava/lang/String;Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "checkPlate", "(Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "deletePlate", "plate", "(Lcom/carcam/platecheck/data/PlateEntity;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "importPlates", "Lcom/carcam/platecheck/data/PlateRepository$ImportOutcome;", "rows", "Lcom/carcam/platecheck/util/PlateImporter$Row;", "(Ljava/util/List;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "ImportOutcome", "app_debug"})
public final class PlateRepository {
    @org.jetbrains.annotations.NotNull()
    private final com.carcam.platecheck.data.PlateDao dao = null;
    @org.jetbrains.annotations.NotNull()
    private final androidx.lifecycle.LiveData<java.util.List<com.carcam.platecheck.data.PlateEntity>> allPlates = null;
    
    public PlateRepository(@org.jetbrains.annotations.NotNull()
    android.content.Context context) {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final androidx.lifecycle.LiveData<java.util.List<com.carcam.platecheck.data.PlateEntity>> getAllPlates() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object addPlate(@org.jetbrains.annotations.NotNull()
    java.lang.String plateNumber, @org.jetbrains.annotations.NotNull()
    java.lang.String note, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object deletePlate(@org.jetbrains.annotations.NotNull()
    com.carcam.platecheck.data.PlateEntity plate, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    /**
     * Bulk import from a spreadsheet. Plates already on the list keep their existing row —
     * re-importing an updated resident list must not wipe notes that were added in the app,
     * and must not create duplicates.
     */
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object importPlates(@org.jetbrains.annotations.NotNull()
    java.util.List<com.carcam.platecheck.util.PlateImporter.Row> rows, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.carcam.platecheck.data.PlateRepository.ImportOutcome> $completion) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object checkPlate(@org.jetbrains.annotations.NotNull()
    java.lang.String plateNumber, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.carcam.platecheck.data.PlateEntity> $completion) {
        return null;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000 \n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0002\b\t\n\u0002\u0010\u000b\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0000\b\u0086\b\u0018\u00002\u00020\u0001B\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0005J\t\u0010\t\u001a\u00020\u0003H\u00c6\u0003J\t\u0010\n\u001a\u00020\u0003H\u00c6\u0003J\u001d\u0010\u000b\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u0003H\u00c6\u0001J\u0013\u0010\f\u001a\u00020\r2\b\u0010\u000e\u001a\u0004\u0018\u00010\u0001H\u00d6\u0003J\t\u0010\u000f\u001a\u00020\u0003H\u00d6\u0001J\t\u0010\u0010\u001a\u00020\u0011H\u00d6\u0001R\u0011\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u0011\u0010\u0004\u001a\u00020\u0003\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\u0007\u00a8\u0006\u0012"}, d2 = {"Lcom/carcam/platecheck/data/PlateRepository$ImportOutcome;", "", "added", "", "alreadyPresent", "(II)V", "getAdded", "()I", "getAlreadyPresent", "component1", "component2", "copy", "equals", "", "other", "hashCode", "toString", "", "app_debug"})
    public static final class ImportOutcome {
        private final int added = 0;
        private final int alreadyPresent = 0;
        
        public ImportOutcome(int added, int alreadyPresent) {
            super();
        }
        
        public final int getAdded() {
            return 0;
        }
        
        public final int getAlreadyPresent() {
            return 0;
        }
        
        public final int component1() {
            return 0;
        }
        
        public final int component2() {
            return 0;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.carcam.platecheck.data.PlateRepository.ImportOutcome copy(int added, int alreadyPresent) {
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