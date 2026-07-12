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
                // Save the whole plate-line box, then the estimated Hangul slot at three offsets.
                saveCrop(bmp, box, java.io.File(dir, "${tag}_line.png"))
                val slotWidth = box.height() * 0.70f
                val baseCenterX = box.right - 4.5f * slotWidth
                for (dx in floatArrayOf(-0.3f, 0f, 0.3f)) {
                    val cx = baseCenterX + dx * slotWidth
                    val crop = Rect(
                        (cx - slotWidth * 0.66f).toInt().coerceAtLeast(0),
                        (box.top - box.height() * 0.08f).toInt().coerceAtLeast(0),
                        (cx + slotWidth * 0.66f).toInt().coerceAtMost(bmp.width),
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
                val match = PlateGlyphTemplateMatcher.matchModernPlate(bitmap, box, leading)
                Log.i(TAG, "TEMPLATE raw=$candidate match=$match box=$box")
                if (match != null && PlateGlyphTemplateMatcher.isConfident(match)) {
                    digits.take(leading) + match.character + digits.takeLast(4)
                } else candidate
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
