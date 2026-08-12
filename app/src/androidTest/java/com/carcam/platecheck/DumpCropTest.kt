package com.carcam.platecheck

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.carcam.platecheck.util.GlyphClassifier
import com.carcam.platecheck.util.GlyphPreprocess
import com.carcam.platecheck.util.PlateOcrEngine
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import org.junit.Ignore
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Diagnostic: writes the exact image the classifier was given, straight out of its own debug
 * hook. Reimplementing the crop here instead once produced a picture the classifier never saw,
 * which sent the investigation down the wrong path.
 *
 * Run with `am instrument` on an installed build; `connectedAndroidTest` uninstalls the app
 * afterwards and takes its external files directory with it.
 */
@Ignore("Diagnostic only; run explicitly with am instrument on an installed build.")
@RunWith(AndroidJUnit4::class)
class DumpCropTest {

    private val testCtx = InstrumentationRegistry.getInstrumentation().context
    private val appCtx = InstrumentationRegistry.getInstrumentation().targetContext
    private val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())

    private fun toBitmap(g: GlyphPreprocess.Gray): Bitmap {
        val px = IntArray(g.w * g.h)
        for (i in px.indices) {
            val v = g.px[i].coerceIn(0, 255)
            px[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        return Bitmap.createBitmap(px, g.w, g.h, Bitmap.Config.ARGB_8888)
    }

    @Test
    fun dump() {
        GlyphClassifier.init(appCtx)
        GlyphClassifier.debugCapture = true
        val dir = File(appCtx.getExternalFilesDir(null), "cropdump").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }

        val names = testCtx.assets.list("plates")!!.filter { it.endsWith(".png") }
        for (name in names) {
            val bmp = testCtx.assets.open("plates/$name").use { BitmapFactory.decodeStream(it) }
            val text = Tasks.await(recognizer.process(InputImage.fromBitmap(bmp, 0)))
            val found = PlateOcrEngine.extractPlates(text).firstOrNull { it.first != null } ?: continue
            val read = found.second
            val idx = read.indexOfFirst { it in '가'..'힣' }
            if (idx < 0) continue

            val result = GlyphClassifier.classifyInRead(bmp, found.first!!, idx, read.length)
            val crop = GlyphClassifier.lastCrop ?: continue
            Log.i(
                "CropDump",
                "$name mlkit=$read -> ${result?.character}(${"%.2f".format(result?.confidence ?: 0f)}) " +
                    GlyphClassifier.lastGeometry
            )
            val base = name.removeSuffix(".png")
            FileOutputStream(File(dir, "$base.png")).use {
                toBitmap(crop).compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        GlyphClassifier.debugCapture = false
        Log.i("CropDump", "written to ${dir.absolutePath}")
    }
}
