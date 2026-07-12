package com.carcam.platecheck

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.media.ExifInterface
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.carcam.platecheck.util.GlyphClassifier
import com.carcam.platecheck.util.ImageUtils
import com.carcam.platecheck.util.KoreanPlateRecognizer
import com.carcam.platecheck.util.PlateOcrEngine
import com.carcam.platecheck.util.PlateGlyphTemplateMatcher
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import org.junit.Assume.assumeTrue
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit

/**
 * Benchmarks plate recognition accuracy + latency against a labeled photo set in
 * app/src/androidTest/assets/plates/ (filename, minus extension and trailing "_N", = ground truth plate).
 *
 * Run: gradle connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.carcam.platecheck.PlateRecognitionBenchmarkTest
 * Results (per-config summary + per-case CSV) are logged to logcat under tag "PlateBenchmark".
 */
@RunWith(AndroidJUnit4::class)
class PlateRecognitionBenchmarkTest {

    private var glyphMode = "cnn"
    private val CNN_THRESHOLD = 0.5f

    private data class OcrConfig(
        val name: String,
        val maxDim: Int,
        val grayscale: Boolean = false,
        val contrast: Float? = null,
        // When pass 1 (on the maxDim-scaled frame) fails: if it found a digit-bearing block that
        // didn't parse as a plate, crop+upscale that region from the full-res source and retry;
        // if it found nothing at all, retry once on the untouched full-res source.
        val twoPass: Boolean = false,
        // How large (longest side, px) the crop+zoom pass upscales the cropped plate region to.
        val zoomTargetDim: Int = 800,
        // Preprocessing applied to the crop itself (independent of pass-1 preprocessing above).
        // Defaults match production (MainActivity.runZoomPass): contrast+40%, no grayscale.
        val zoomContrast: Float? = 1.4f,
        val zoomGrayscale: Boolean = false
    )

    private data class CaseResult(
        val file: String,
        val expected: String,
        val actual: String?,
        // Old behavior: exact string match (what checkPlate() did before the digits-only fallback).
        val exactMatch: Boolean,
        // New behavior: matches what checkPlate() now does — digits-only comparison, tolerant of a
        // misread middle Hangul character.
        val digitMatch: Boolean,
        val latencyMs: Long,
        val rawText: String = ""
    )

    // Candidate configs to compare against the current production baseline.
    // Baseline mirrors MainActivity: color image downscaled to the analyzer's 960x540 target resolution.
    private val configs = listOf(
        OcrConfig(name = "baseline_960_color", maxDim = 960),
        OcrConfig(name = "hires_1600_color", maxDim = 1600),
        OcrConfig(name = "baseline_960_grayscale", maxDim = 960, grayscale = true),
        OcrConfig(name = "baseline_960_contrast", maxDim = 960, contrast = 1.4f),
        OcrConfig(name = "hires_1600_contrast", maxDim = 1600, contrast = 1.4f),
        OcrConfig(name = "two_pass_960_color", maxDim = 960, twoPass = true),
        OcrConfig(name = "two_pass_960_contrast", maxDim = 960, contrast = 1.4f, twoPass = true),
        OcrConfig(name = "two_pass_960_contrast_zoom1400", maxDim = 960, contrast = 1.4f, twoPass = true, zoomTargetDim = 1400),
        OcrConfig(name = "two_pass_960_contrast_zoom2000", maxDim = 960, contrast = 1.4f, twoPass = true, zoomTargetDim = 2000),
        OcrConfig(name = "two_pass_zoomGray", maxDim = 960, contrast = 1.4f, twoPass = true, zoomContrast = null, zoomGrayscale = true),
        OcrConfig(name = "two_pass_zoomGray_contrast", maxDim = 960, contrast = 1.4f, twoPass = true, zoomContrast = 1.4f, zoomGrayscale = true),
        OcrConfig(name = "two_pass_zoomColorOnly", maxDim = 960, contrast = 1.4f, twoPass = true, zoomContrast = null, zoomGrayscale = false),
    )

    @Test
    fun benchmarkAllConfigs() {
        // Use the instrumentation's own context (test APK), not targetContext (app APK) —
        // assets/plates/ is packaged into the androidTest APK, which only the test context can see.
        val context = InstrumentationRegistry.getInstrumentation().context
        val requestedFile = InstrumentationRegistry.getArguments().getString("file")
        val cases = loadLabeledCases(context).filter { requestedFile == null || it.first == requestedFile }
        assumeTrue(
            "No labeled photos found in assets/plates/ — add photos before running this benchmark.",
            cases.isNotEmpty()
        )
        Log.i(TAG, "Loaded ${cases.size} labeled test cases")

        val csv = StringBuilder("config,file,expected,actual,exact_match,digit_match,latency_ms\n")
        val summaries = mutableListOf<String>()

        // Middle-glyph recovery method: -e glyph cnn|template (default cnn).
        glyphMode = InstrumentationRegistry.getArguments().getString("glyph") ?: "cnn"
        if (glyphMode == "cnn") GlyphClassifier.init(InstrumentationRegistry.getInstrumentation().targetContext)
        Log.i(TAG, "Middle-glyph mode = $glyphMode (cnn ready=${GlyphClassifier.isReady()})")

        val requestedConfig = InstrumentationRegistry.getArguments().getString("config")
        for (config in configs.filter { requestedConfig == null || it.name == requestedConfig }) {
            val results = if (config.twoPass) {
                runTwoPassConfig(context, config, cases)
            } else {
                runConfig(context, config, cases)
            }
            val exactAcc = 100.0 * results.count { it.exactMatch } / results.size
            val digitAcc = 100.0 * results.count { it.digitMatch } / results.size
            val latencies = results.map { it.latencyMs }.sorted()
            val avg = latencies.average()
            val p50 = latencies[latencies.size / 2]
            val p95 = latencies[(latencies.size * 0.95).toInt().coerceAtMost(latencies.size - 1)]

            val summary = "[%s] exact=%.1f%% (%d/%d)  digitMatch(=DB lookup)=%.1f%% (%d/%d)  avg=%.0fms  p50=%dms  p95=%dms".format(
                config.name, exactAcc, results.count { it.exactMatch }, results.size,
                digitAcc, results.count { it.digitMatch }, results.size, avg, p50, p95
            )
            Log.i(TAG, summary)
            summaries.add(summary)

            for (r in results) {
                if (!r.digitMatch) {
                    Log.i(TAG, "  [${config.name}] MISS file=${r.file} expected=${r.expected} actual=${r.actual ?: "(none)"} rawText=[${r.rawText}]")
                }
                csv.append("${config.name},${r.file},${r.expected},${r.actual ?: ""},${r.exactMatch},${r.digitMatch},${r.latencyMs}\n")
            }
        }

        Log.i(TAG, "===== SUMMARY =====\n" + summaries.joinToString("\n"))
        Log.i(TAG, "===== CSV =====\n$csv")
    }

    /**
     * Diagnostic: run the glyph CNN on the middle slot of EVERY plate (not just numeric-only reads)
     * to see whether always using it to correct the middle char is a safe global win — i.e. does it
     * fix ML Kit's valid-but-wrong reads (머→허) without breaking the ones ML Kit gets right?
     */
    /**
     * Harvest labeled real glyph crops (same pipeline the app feeds the CNN) into the app's files
     * dir for fine-tuning. Each plate yields several offset crops. Filename: <label>__<file>__<i>.png
     * Pull with: adb exec-out run-as com.carcam.platecheck tar c -C files harvest > harvest.tar
     */
    @Ignore("Diagnostic only; run explicitly. Harvests labeled glyph crops for CNN fine-tuning.")
    @Test
    fun harvestGlyphs() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = java.io.File(target.filesDir, "harvest").apply { mkdirs() }
        val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
        var saved = 0
        try {
            val warmup = Bitmap.createBitmap(100, 60, Bitmap.Config.ARGB_8888)
            runCatching { Tasks.await(recognizer.process(InputImage.fromBitmap(warmup, 0)), 30, TimeUnit.SECONDS) }
            for ((file, expected) in loadLabeledCases(context)) {
                val label = expected.firstOrNull { it in '가'..'힣' } ?: continue
                val bmp = resizeToMaxDim(loadBitmapWithExifRotation(context, "plates/$file"), 960)
                val text = Tasks.await(recognizer.process(InputImage.fromBitmap(bmp, 0)), 15, TimeUnit.SECONDS)
                val box = (PlateOcrEngine.extractPlates(text).mapNotNull { it.first } +
                    PlateOcrEngine.findAmbiguousDigitBlocks(text)).maxByOrNull { it.width() } ?: continue
                val cand = PlateOcrEngine.extractPlates(text).maxByOrNull { (it.first?.width() ?: 0) }?.second ?: ""
                val digits = KoreanPlateRecognizer.digitsOnly(cand).ifEmpty { KoreanPlateRecognizer.digitsOnly(expected) }
                if (digits.length !in 6..8) continue
                val leading = digits.length - 4 - if (digits.length == 8) 1 else 0
                val totalSlots = leading + 5
                val pitch = box.width().toFloat() / totalSlots
                val baseCx = box.left + (leading + 0.5f) * pitch
                val top = (box.top - box.height() * 0.08f).toInt().coerceAtLeast(0)
                val bottom = (box.bottom + box.height() * 0.08f).toInt().coerceAtMost(bmp.height)
                var i = 0
                for (dx in floatArrayOf(-0.2f, -0.1f, 0f, 0.1f, 0.2f)) for (hw in floatArrayOf(0.5f, 0.6f)) {
                    val cx = baseCx + dx * pitch; val half = pitch * hw
                    val l = (cx - half).toInt().coerceAtLeast(0); val r = (cx + half).toInt().coerceAtMost(bmp.width)
                    if (r - l < 8 || bottom - top < 8) continue
                    val crop = Bitmap.createBitmap(bmp, l, top, r - l, bottom - top)
                    val f = java.io.File(dir, "${label}__${file.substringBeforeLast(".")}__${i}.png")
                    java.io.FileOutputStream(f).use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    i++; saved++
                }
            }
            Log.i(TAG, "HARVEST saved=$saved crops to ${dir.absolutePath}")
        } finally { recognizer.close() }
    }

    @Ignore("Diagnostic only; run explicitly. Showed synthetic-only CNN is unreliable for global middle correction (~48% on real plates).")
    @Test
    fun experimentCnnMiddle() {
        val context = InstrumentationRegistry.getInstrumentation().context
        GlyphClassifier.init(InstrumentationRegistry.getInstrumentation().targetContext)
        val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
        var mlkitOk = 0; var cnnOk = 0; var n = 0
        try {
            val warmup = Bitmap.createBitmap(100, 60, Bitmap.Config.ARGB_8888)
            runCatching { Tasks.await(recognizer.process(InputImage.fromBitmap(warmup, 0)), 30, TimeUnit.SECONDS) }
            for ((file, expected) in loadLabeledCases(context)) {
                val expMid = expected.firstOrNull { it in '가'..'힣' } ?: continue
                val bmp = resizeToMaxDim(loadBitmapWithExifRotation(context, "plates/$file"), 960)
                val text = Tasks.await(recognizer.process(InputImage.fromBitmap(bmp, 0)), 15, TimeUnit.SECONDS)
                val (box, cand) = PlateOcrEngine.extractPlates(text).mapNotNull { (b, t) -> b?.let { it to t } }
                    .maxByOrNull { it.first.width() } ?: continue
                val digits = KoreanPlateRecognizer.digitsOnly(cand)
                if (digits.length !in 6..8) continue
                val leading = digits.length - 4 - if (digits.length == 8) 1 else 0
                val mlMid = cand.firstOrNull { it in '가'..'힣' }
                val cnn = GlyphClassifier.classify(bmp, box, leading)
                n++
                if (mlMid == expMid) mlkitOk++
                if (cnn?.character == expMid) cnnOk++
                Log.i(TAG, "CNNMID $file exp=$expMid mlkit=${mlMid ?: "(none)"} cnn=${cnn?.character}(${"%.2f".format(cnn?.confidence)}) " +
                    "${if (cnn?.character==expMid) "CNN-ok" else ""}${if (mlMid==expMid) " ML-ok" else ""}")
            }
            Log.i(TAG, "CNNMID SUMMARY n=$n  mlkit-middle-correct=$mlkitOk  cnn-middle-correct=$cnnOk")
        } finally { recognizer.close() }
    }

    private data class ReOcrStrategy(
        val name: String,
        val zoomDim: Int,
        val contrast: Float?,
        val grayscale: Boolean,
        // Downscale-then-upscale factor to attenuate moiré before the zoom (1f = disabled).
        val moireBlur: Float = 1f
    )

    /**
     * Diagnostic: for the frames where pass-1 fails to read the middle Hangul (bare digits or a
     * misread char), crop the plate region from full-res and RE-RUN ML Kit with various
     * preprocessing. Logs what each strategy reads so we can find one that recovers e.g. '러'.
     * Run: gradle connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.carcam.platecheck.PlateRecognitionBenchmarkTest#experimentReOcr
     */
    /**
     * Feeds a CLEANED, full-plate crop (not the tiny glyph slot) to ML Kit: contrast-stretch,
     * adaptive binarization, and Sobel. Tests whether binarizing the plate before OCR recovers '러'.
     * Run: adb ... class=...#experimentBinarizedReOcr
     */
    @Ignore("Diagnostic only; run explicitly. Showed ML Kit itself never reads this '러' (reads '리'/digit).")
    @Test
    fun experimentBinarizedReOcr() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val cases = loadLabeledCases(context).filter { it.second == "154러7070" }
        val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
        try {
            val warmup = Bitmap.createBitmap(100, 60, Bitmap.Config.ARGB_8888)
            runCatching { Tasks.await(recognizer.process(InputImage.fromBitmap(warmup, 0)), 30, TimeUnit.SECONDS) }
            for ((file, _) in cases) {
                val fullRes = loadBitmapWithExifRotation(context, "plates/$file")
                val pass1 = resizeToMaxDim(fullRes, 960)
                val scale = fullRes.width.toFloat() / pass1.width
                val t1 = Tasks.await(recognizer.process(InputImage.fromBitmap(pass1, 0)), 15, TimeUnit.SECONDS)
                val box = (PlateOcrEngine.extractPlates(t1).mapNotNull { it.first } + PlateOcrEngine.findAmbiguousDigitBlocks(t1))
                    .maxByOrNull { it.width() } ?: continue
                val fr = Rect((box.left*scale).toInt(),(box.top*scale).toInt(),(box.right*scale).toInt(),(box.bottom*scale).toInt())
                val crop = ImageUtils.cropAndUpscale(fullRes, fr, targetMaxDim = 1000)
                val gray = toGray(crop)

                val variants = linkedMapOf<String, Bitmap>(
                    "contrastDenoise" to stretchAndBlur(gray),
                    "adaptiveBinary" to adaptiveBinary(stretchAndBlur(gray)),
                    "adaptiveBinaryNoBlur" to adaptiveBinary(gray)
                )
                for ((name, bmp) in variants) {
                    val t2 = Tasks.await(recognizer.process(InputImage.fromBitmap(bmp, 0)), 15, TimeUnit.SECONDS)
                    val plates = PlateOcrEngine.extractPlates(t2)
                    val strict = plates.any { KoreanPlateRecognizer.extractPlateNumber(it.second) != null }
                    // Also try the TEMPLATE matcher on this (cleaned) bitmap, using ML Kit's digit box.
                    var tmpl = "n/a"
                    val pbox = plates.mapNotNull { it.first }.maxByOrNull { it.width() }
                    if (pbox != null) {
                        val digits = KoreanPlateRecognizer.digitsOnly(plates.first { it.first == pbox }.second)
                        if (digits.length in 6..8) {
                            val leading = digits.length - 4 - if (digits.length == 8) 1 else 0
                            val m = PlateGlyphTemplateMatcher.matchModernPlate(bmp, pbox, leading)
                            tmpl = "$m confident=${m != null && PlateGlyphTemplateMatcher.isConfident(m)}"
                        }
                    }
                    Log.i(TAG, "BINREOCR file=$file [$name] raw=[${t2.text.replace("\n","|")}] plates=[${plates.joinToString{it.second}}] strict러=$strict TEMPLATE=$tmpl")
                }
            }
        } finally { recognizer.close() }
    }

    private fun toGray(src: Bitmap): Bitmap {
        val bmp = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawBitmap(src, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) }) })
        return bmp
    }

    private fun stretchAndBlur(gray: Bitmap): Bitmap {
        val w = gray.width; val h = gray.height
        val px = IntArray(w*h); gray.getPixels(px,0,w,0,0,w,h)
        val lum = IntArray(w*h) { android.graphics.Color.red(px[it]) }
        val hist = IntArray(256); for (v in lum) hist[v]++
        val cut = (w*h*0.02).toInt(); var lo=0; var hi=255; var acc=0
        for (t in 0..255){acc+=hist[t]; if(acc>cut){lo=t;break}}; acc=0
        for (t in 255 downTo 0){acc+=hist[t]; if(acc>cut){hi=t;break}}
        val scale = if (hi>lo) 255f/(hi-lo) else 1f
        val stretched = IntArray(w*h){ (((lum[it]-lo)*scale).toInt()).coerceIn(0,255) }
        // 3x3 box blur
        val out = IntArray(w*h)
        for (y in 0 until h) for (x in 0 until w){ var s=0; var n=0
            for (dy in -1..1) for (dx in -1..1){ val ny=y+dy; val nx=x+dx; if(ny in 0 until h && nx in 0 until w){s+=stretched[ny*w+nx];n++}}
            val v=s/n; out[y*w+x]=(0xFF shl 24) or (v shl 16) or (v shl 8) or v }
        return Bitmap.createBitmap(out,w,h,Bitmap.Config.ARGB_8888)
    }

    private fun adaptiveBinary(gray: Bitmap): Bitmap {
        val w=gray.width; val h=gray.height
        val px=IntArray(w*h); gray.getPixels(px,0,w,0,0,w,h)
        val g=IntArray(w*h){ android.graphics.Color.red(px[it]) }
        val integral=LongArray((w+1)*(h+1))
        for (y in 0 until h) for (x in 0 until w) integral[(y+1)*(w+1)+(x+1)]=g[y*w+x]+integral[y*(w+1)+(x+1)]+integral[(y+1)*(w+1)+x]-integral[y*(w+1)+x]
        val r=maxOf(8, minOf(w,h)/12); val c=10
        val out=IntArray(w*h)
        for (y in 0 until h) for (x in 0 until w){
            val x0=maxOf(0,x-r); val y0=maxOf(0,y-r); val x1=minOf(w-1,x+r); val y1=minOf(h-1,y+r)
            val area=(x1-x0+1)*(y1-y0+1)
            val sum=integral[(y1+1)*(w+1)+(x1+1)]-integral[y0*(w+1)+(x1+1)]-integral[(y1+1)*(w+1)+x0]+integral[y0*(w+1)+x0]
            val v=if(g[y*w+x] < sum/area - c) 0 else 255
            out[y*w+x]=(0xFF shl 24) or (v shl 16) or (v shl 8) or v }
        return Bitmap.createBitmap(out,w,h,Bitmap.Config.ARGB_8888)
    }

    @Ignore("Diagnostic only; run explicitly. Proved ML Kit re-OCR cannot recover this '러' under moire.")
    @Test
    fun experimentReOcr() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val cases = loadLabeledCases(context)
        assumeTrue("No labeled photos found.", cases.isNotEmpty())

        val strategies = listOf(
            ReOcrStrategy("crop1400_color_noC", 1400, null, false),
            ReOcrStrategy("crop1400_c14", 1400, 1.4f, false),
            ReOcrStrategy("crop2000_c14", 2000, 1.4f, false),
            ReOcrStrategy("crop2400_c16", 2400, 1.6f, false),
            ReOcrStrategy("crop1400_gray_c16", 1400, 1.6f, true),
            ReOcrStrategy("crop1600_moire_c14", 1600, 1.4f, false, moireBlur = 0.5f),
        )

        val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
        try {
            val warmup = Bitmap.createBitmap(100, 60, Bitmap.Config.ARGB_8888)
            runCatching { Tasks.await(recognizer.process(InputImage.fromBitmap(warmup, 0)), 30, TimeUnit.SECONDS) }

            for ((file, expected) in cases) {
                val fullRes = loadBitmapWithExifRotation(context, "plates/$file")
                val pass1 = resizeToMaxDim(fullRes, 960)
                val scale = fullRes.width.toFloat() / pass1.width
                val pass1Text = Tasks.await(recognizer.process(InputImage.fromBitmap(pass1, 0)), 15, TimeUnit.SECONDS)

                val pass1Plates = PlateOcrEngine.extractPlates(pass1Text)
                val pass1Extracted = pass1Plates.joinToString(",") { it.second }
                // A middle char is "recovered" already if any candidate parses strictly (valid usage Hangul).
                val alreadyGood = pass1Plates.any { KoreanPlateRecognizer.extractPlateNumber(it.second) != null }
                Log.i(TAG, "REOCR file=$file expected=$expected pass1=[${pass1Text.text.replace("\n", "|")}] extracted=[$pass1Extracted] alreadyGood=$alreadyGood")
                if (alreadyGood) continue

                // Candidate plate boxes to zoom into: prefer boxes of digit-bearing plate candidates,
                // fall back to ambiguous digit blocks.
                val boxes = (pass1Plates.mapNotNull { it.first } + PlateOcrEngine.findAmbiguousDigitBlocks(pass1Text))
                    .distinct()
                for (box in boxes) {
                    val fr = Rect(
                        (box.left * scale).toInt(), (box.top * scale).toInt(),
                        (box.right * scale).toInt(), (box.bottom * scale).toInt()
                    )
                    for (s in strategies) {
                        var src = fullRes
                        if (s.moireBlur < 1f) {
                            val dw = (fullRes.width * s.moireBlur).toInt().coerceAtLeast(1)
                            val dh = (fullRes.height * s.moireBlur).toInt().coerceAtLeast(1)
                            src = Bitmap.createScaledBitmap(Bitmap.createScaledBitmap(fullRes, dw, dh, true), fullRes.width, fullRes.height, true)
                        }
                        var crop = ImageUtils.cropAndUpscale(src, fr, targetMaxDim = s.zoomDim)
                        if (s.grayscale) crop = ImageUtils.toGrayscale(crop)
                        s.contrast?.let { crop = ImageUtils.adjustContrast(crop, it) }
                        val t2 = Tasks.await(recognizer.process(InputImage.fromBitmap(crop, 0)), 15, TimeUnit.SECONDS)
                        val ex = PlateOcrEngine.extractPlates(t2).joinToString(",") { it.second }
                        val strict = PlateOcrEngine.extractPlates(t2).any { KoreanPlateRecognizer.extractPlateNumber(it.second) != null }
                        Log.i(TAG, "  [${s.name}] raw=[${t2.text.replace("\n", "|")}] extracted=[$ex] strictMiddle=$strict")
                    }
                }
            }
        } finally {
            recognizer.close()
        }
    }

    /**
     * Diagnostic: dump the exact Hangul-slot crops the template matcher evaluates, so we can eyeball
     * whether the geometry actually isolates '러' or cuts off its vertical vowel stroke.
     * Saves PNGs to the test app's external files dir; pull with adb afterwards.
     */
    @Ignore("Diagnostic only; run explicitly. Dumps Hangul-slot crops for offline inspection.")
    @Test
    fun dumpGlyphCrops() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val cases = loadLabeledCases(context).filter { it.second.contains("러") }
        val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = java.io.File(targetContext.filesDir, "glyphdump").apply { mkdirs() }
        try {
            val warmup = Bitmap.createBitmap(100, 60, Bitmap.Config.ARGB_8888)
            runCatching { Tasks.await(recognizer.process(InputImage.fromBitmap(warmup, 0)), 30, TimeUnit.SECONDS) }
            for ((idx, case) in cases.withIndex()) {
                val (file, _) = case
                val bmp = resizeToMaxDim(loadBitmapWithExifRotation(context, "plates/$file"), 960)
                val text = Tasks.await(recognizer.process(InputImage.fromBitmap(bmp, 0)), 15, TimeUnit.SECONDS)
                val box = PlateOcrEngine.extractPlates(text).mapNotNull { it.first }
                    .maxByOrNull { it.width() } ?: continue
                val tag = "case$idx"  // ASCII name so adb run-as can cat it back
                // Save the whole plate-line box, then the width-derived Hangul slot at three offsets.
                saveCrop(bmp, box, java.io.File(dir, "${tag}_line.png"))
                val slotPitch = box.width() / 8f
                val baseCenterX = box.left + 3.5f * slotPitch
                for (dx in floatArrayOf(-0.2f, 0f, 0.2f)) {
                    val cx = baseCenterX + dx * slotPitch
                    val crop = Rect(
                        (cx - slotPitch * 0.55f).toInt().coerceAtLeast(0),
                        (box.top - box.height() * 0.08f).toInt().coerceAtLeast(0),
                        (cx + slotPitch * 0.55f).toInt().coerceAtMost(bmp.width),
                        (box.bottom + box.height() * 0.08f).toInt().coerceAtMost(bmp.height)
                    )
                    saveCrop(bmp, crop, java.io.File(dir, "${tag}_slot_${dx}.png"))
                }
                Log.i(TAG, "DUMP $file box=$box -> ${dir.absolutePath}")
            }
        } finally {
            recognizer.close()
        }
    }

    private fun saveCrop(src: Bitmap, box: Rect, dest: java.io.File) {
        val l = box.left.coerceIn(0, src.width - 1); val t = box.top.coerceIn(0, src.height - 1)
        val r = box.right.coerceIn(l + 1, src.width); val b = box.bottom.coerceIn(t + 1, src.height)
        val crop = Bitmap.createBitmap(src, l, t, r - l, b - t)
        java.io.FileOutputStream(dest).use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun runConfig(context: Context, config: OcrConfig, cases: List<Pair<String, String>>): List<CaseResult> {
        val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
        val results = mutableListOf<CaseResult>()
        try {
            // Warm up the model (first call may trigger on-device model load) outside of timed measurements.
            val warmup = Bitmap.createBitmap(100, 60, Bitmap.Config.ARGB_8888)
            runCatching { Tasks.await(recognizer.process(InputImage.fromBitmap(warmup, 0)), 30, TimeUnit.SECONDS) }

            for ((file, expected) in cases) {
                var bitmap = loadBitmapWithExifRotation(context, "plates/$file")
                bitmap = resizeToMaxDim(bitmap, config.maxDim)
                if (config.grayscale) bitmap = toGrayscale(bitmap)
                config.contrast?.let { bitmap = ImageUtils.adjustContrast(bitmap, it) }

                val image = InputImage.fromBitmap(bitmap, 0)
                val start = SystemClock.elapsedRealtime()
                val visionText = Tasks.await(recognizer.process(image), 15, TimeUnit.SECONDS)
                val latency = SystemClock.elapsedRealtime() - start

                if (expected == "154러7070") {
                    visionText.textBlocks.forEach { block ->
                        Log.i(TAG, "BLOCK text=${block.text.replace("\n", "|")} box=${block.boundingBox}")
                        block.lines.forEach { line ->
                            Log.i(TAG, " LINE text=${line.text} box=${line.boundingBox}")
                            line.elements.forEach { element -> Log.i(TAG, "  ELEMENT text=${element.text} box=${element.boundingBox}") }
                        }
                    }
                }

                val candidates = applyTemplateFallback(bitmap, visionText)
                val expectedDigits = KoreanPlateRecognizer.digitsOnly(expected)
                val exactMatch = candidates.contains(expected)
                val digitMatch = exactMatch || candidates.any { KoreanPlateRecognizer.candidateDigitKeys(it).contains(expectedDigits) }
                val actual = candidates.firstOrNull { it == expected }
                    ?: candidates.firstOrNull { KoreanPlateRecognizer.candidateDigitKeys(it).contains(expectedDigits) }
                    ?: candidates.firstOrNull()
                val rawText = visionText.text.replace("\n", "|")

                results.add(CaseResult(file, expected, actual, exactMatch, digitMatch, latency, rawText))
            }
        } finally {
            recognizer.close()
        }
        return results
    }

    // Pass 1 on the maxDim-scaled frame (fast path, same as runConfig). If it fails to yield a
    // plate, pass 2 either crops+zooms a digit-bearing block (mapped back to full-res coordinates)
    // or, if pass 1 saw nothing at all, retries once on the untouched full-res source.
    private fun runTwoPassConfig(context: Context, config: OcrConfig, cases: List<Pair<String, String>>): List<CaseResult> {
        val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
        val results = mutableListOf<CaseResult>()
        try {
            val warmup = Bitmap.createBitmap(100, 60, Bitmap.Config.ARGB_8888)
            runCatching { Tasks.await(recognizer.process(InputImage.fromBitmap(warmup, 0)), 30, TimeUnit.SECONDS) }

            for ((file, expected) in cases) {
                val fullRes = loadBitmapWithExifRotation(context, "plates/$file")
                var pass1Bitmap = resizeToMaxDim(fullRes, config.maxDim)
                if (config.grayscale) pass1Bitmap = toGrayscale(pass1Bitmap)
                config.contrast?.let { pass1Bitmap = ImageUtils.adjustContrast(pass1Bitmap, it) }
                val scaleToFullRes = fullRes.width.toFloat() / pass1Bitmap.width

                val start = SystemClock.elapsedRealtime()

                val pass1Text = Tasks.await(recognizer.process(InputImage.fromBitmap(pass1Bitmap, 0)), 15, TimeUnit.SECONDS)
                var candidates = applyTemplateFallback(pass1Bitmap, pass1Text)
                var rawText = "pass1:" + pass1Text.text.replace("\n", "|")

                if (candidates.isEmpty()) {
                    val ambiguousBlocks = PlateOcrEngine.findAmbiguousDigitBlocks(pass1Text)
                    if (ambiguousBlocks.isNotEmpty()) {
                        for (box in ambiguousBlocks) {
                            val fullResBox = Rect(
                                (box.left * scaleToFullRes).toInt(),
                                (box.top * scaleToFullRes).toInt(),
                                (box.right * scaleToFullRes).toInt(),
                                (box.bottom * scaleToFullRes).toInt()
                            )
                            var crop = ImageUtils.cropAndUpscale(fullRes, fullResBox, targetMaxDim = config.zoomTargetDim)
                            if (config.zoomGrayscale) crop = ImageUtils.toGrayscale(crop)
                            config.zoomContrast?.let { crop = ImageUtils.adjustContrast(crop, it) }
                            val pass2Text = Tasks.await(recognizer.process(InputImage.fromBitmap(crop, 0)), 15, TimeUnit.SECONDS)
                            val pass2Candidates = PlateOcrEngine.extractPlates(pass2Text).map { it.second }
                            if (pass2Candidates.isNotEmpty()) {
                                candidates = pass2Candidates
                                rawText = "pass2-crop:" + pass2Text.text.replace("\n", "|")
                                break
                            }
                        }
                    } else {
                        val pass2Text = Tasks.await(recognizer.process(InputImage.fromBitmap(fullRes, 0)), 15, TimeUnit.SECONDS)
                        val pass2Candidates = PlateOcrEngine.extractPlates(pass2Text).map { it.second }
                        if (pass2Candidates.isNotEmpty()) {
                            candidates = pass2Candidates
                            rawText = "pass2-native:" + pass2Text.text.replace("\n", "|")
                        }
                    }
                }

                val latency = SystemClock.elapsedRealtime() - start

                val expectedDigits = KoreanPlateRecognizer.digitsOnly(expected)
                val exactMatch = candidates.contains(expected)
                val digitMatch = exactMatch || candidates.any { KoreanPlateRecognizer.candidateDigitKeys(it).contains(expectedDigits) }
                val actual = candidates.firstOrNull { it == expected }
                    ?: candidates.firstOrNull { KoreanPlateRecognizer.candidateDigitKeys(it).contains(expectedDigits) }
                    ?: candidates.firstOrNull()

                results.add(CaseResult(file, expected, actual, exactMatch, digitMatch, latency, rawText))
            }
        } finally {
            recognizer.close()
        }
        return results
    }

    private fun loadLabeledCases(context: Context): List<Pair<String, String>> {
        val files = context.assets.list("plates")?.filter {
            it.endsWith(".jpg", true) || it.endsWith(".jpeg", true) || it.endsWith(".png", true)
        } ?: emptyList()
        return files.map { file ->
            val base = file.substringBeforeLast(".")
            val expected = base.replace(Regex("_\\d+$"), "")
            file to expected
        }
    }

    private fun applyTemplateFallback(bitmap: Bitmap, text: com.google.mlkit.vision.text.Text): List<String> {
        return PlateOcrEngine.extractPlates(text).map { (box, candidate) ->
            val digits = KoreanPlateRecognizer.digitsOnly(candidate)
            if (box != null && candidate.none { it in '가'..'힣' } && digits.length in 6..8) {
                val leading = digits.length - 4 - if (digits.length == 8) 1 else 0
                if (glyphMode == "cnn" && GlyphClassifier.isReady()) {
                    val r = GlyphClassifier.classify(bitmap, box, leading)
                    Log.i(TAG, "CNN raw=$candidate result=$r box=$box")
                    if (r != null && r.confidence >= CNN_THRESHOLD) {
                        digits.take(leading) + r.character + digits.takeLast(4)
                    } else candidate
                } else {
                    val match = PlateGlyphTemplateMatcher.matchModernPlate(bitmap, box, leading)
                    Log.i(TAG, "TEMPLATE raw=$candidate match=$match box=$box")
                    if (match != null && PlateGlyphTemplateMatcher.isConfident(match)) {
                        digits.take(leading) + match.character + digits.takeLast(4)
                    } else candidate
                }
            } else candidate
        }
    }

    private fun loadBitmapWithExifRotation(context: Context, assetPath: String): Bitmap {
        val raw = context.assets.open(assetPath).use { it.readBytes() }
        val bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.size)
        val orientation = ExifInterface(ByteArrayInputStream(raw))
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        }
        return if (!matrix.isIdentity) {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else bitmap
    }

    private fun resizeToMaxDim(bitmap: Bitmap, maxDim: Int): Bitmap {
        val scale = maxDim.toFloat() / maxOf(bitmap.width, bitmap.height)
        if (scale >= 1f) return bitmap
        val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    private fun toGrayscale(src: Bitmap): Bitmap {
        val bmp = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint()
        paint.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        canvas.drawBitmap(src, 0f, 0f, paint)
        return bmp
    }

    companion object {
        private const val TAG = "PlateBenchmark"
    }
}
