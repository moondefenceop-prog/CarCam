package com.carcam.platecheck.ui;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000<\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0002\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u000e\u0010\u0010\u001a\u00020\u00112\u0006\u0010\u0012\u001a\u00020\u0006J\u000e\u0010\u0013\u001a\u00020\u00112\u0006\u0010\u0012\u001a\u00020\u0006J\u0006\u0010\u0014\u001a\u00020\u0011J\u0006\u0010\u0015\u001a\u00020\u0011J\u0018\u0010\u0016\u001a\u00020\u00172\u0006\u0010\u0012\u001a\u00020\u00062\b\b\u0002\u0010\u0018\u001a\u00020\u0006R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0019\u0010\u0007\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\t0\b\u00a2\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000bR\u000e\u0010\f\u001a\u00020\rX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0019\u0010\u000e\u001a\n\u0012\u0006\u0012\u0004\u0018\u00010\t0\b\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u000b\u00a8\u0006\u0019"}, d2 = {"Lcom/carcam/platecheck/ui/MainViewModel;", "Landroidx/lifecycle/AndroidViewModel;", "app", "Landroid/app/Application;", "(Landroid/app/Application;)V", "lastCheckedPlate", "", "manualResult", "Landroidx/lifecycle/MutableLiveData;", "Lcom/carcam/platecheck/ui/ScanResult;", "getManualResult", "()Landroidx/lifecycle/MutableLiveData;", "repository", "Lcom/carcam/platecheck/data/PlateRepository;", "scanResult", "getScanResult", "checkManual", "", "plateNumber", "checkPlate", "clearManualResult", "clearResult", "registerPlate", "Lkotlinx/coroutines/Job;", "note", "app_debug"})
public final class MainViewModel extends androidx.lifecycle.AndroidViewModel {
    @org.jetbrains.annotations.NotNull()
    private final com.carcam.platecheck.data.PlateRepository repository = null;
    @org.jetbrains.annotations.NotNull()
    private final androidx.lifecycle.MutableLiveData<com.carcam.platecheck.ui.ScanResult> scanResult = null;
    @org.jetbrains.annotations.NotNull()
    private final androidx.lifecycle.MutableLiveData<com.carcam.platecheck.ui.ScanResult> manualResult = null;
    @org.jetbrains.annotations.NotNull()
    private java.lang.String lastCheckedPlate = "";
    
    public MainViewModel(@org.jetbrains.annotations.NotNull()
    android.app.Application app) {
        super(null);
    }
    
    @org.jetbrains.annotations.NotNull()
    public final androidx.lifecycle.MutableLiveData<com.carcam.platecheck.ui.ScanResult> getScanResult() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final androidx.lifecycle.MutableLiveData<com.carcam.platecheck.ui.ScanResult> getManualResult() {
        return null;
    }
    
    /**
     * Look up a hand-typed plate. Same matching rules as a scan, so a plate registered with
     * a mistyped usage glyph still resolves by its digits.
     */
    public final void checkManual(@org.jetbrains.annotations.NotNull()
    java.lang.String plateNumber) {
    }
    
    public final void clearManualResult() {
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.Job registerPlate(@org.jetbrains.annotations.NotNull()
    java.lang.String plateNumber, @org.jetbrains.annotations.NotNull()
    java.lang.String note) {
        return null;
    }
    
    public final void checkPlate(@org.jetbrains.annotations.NotNull()
    java.lang.String plateNumber) {
    }
    
    public final void clearResult() {
    }
}