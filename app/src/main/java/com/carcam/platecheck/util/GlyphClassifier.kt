package com.carcam.platecheck.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * On-device 40-class classifier for the plate usage glyph (용도기호), trained to cover ML Kit's
 * ㅓ-column blind spot (러→리/로/digit). ML Kit still detects the plate and reads the digits;
 * this only decides the single middle Hangul from the digit line's geometry.
 *
 * Preprocessing MUST match ml/train.py: grayscale slot crop → 48x48 → per-image standardization.
 */
object GlyphClassifier {
    data class Result(val character: Char, val confidence: Float)

    // Class order == ml/labels.npy == VALID_MIDDLE set. Index i of the model output maps to LABELS[i].
    private val LABELS = "가나다라마거너더러머버서어저고노도로모보소오조구누두루무부수우주아자바사하허호배".toCharArray()
    private const val SIZE = 48
    private const val ASSET = "glyph_cnn.tflite"

    // Same slot sweep the template matcher uses: the classifier is translation-robust, but a small
    // sweep still helps recover from ML Kit's jittery line box. Keep the most confident hit.
    private val CENTER_OFFSETS = floatArrayOf(-0.35f, -0.15f, 0f, 0.15f, 0.35f)
    private val HALF_WIDTHS = floatArrayOf(0.5f, 0.62f)

    @Volatile private var interpreter: Interpreter? = null
    private val input = ByteBuffer.allocateDirect(SIZE * SIZE * 4).order(ByteOrder.nativeOrder())
    private val output = Array(1) { FloatArray(LABELS.size) }

    fun init(context: Context) {
        if (interpreter != null) return
        synchronized(this) {
            if (interpreter != null) return
            val fd = context.assets.openFd(ASSET)
            fd.use {
                val model = java.io.FileInputStream(it.fileDescriptor).channel
                    .map(FileChannel.MapMode.READ_ONLY, it.startOffset, it.declaredLength)
                interpreter = Interpreter(model, Interpreter.Options().apply { numThreads = 2 })
            }
        }
    }

    fun isReady() = interpreter != null

    /**
     * Numeric-only path: ML Kit dropped the Hangul, so the box spans (leadingDigits + 1 + 4) equal
     * slots and the Hangul sits at index [leadingDigits].
     */
    fun classify(bitmap: Bitmap, lineBox: Rect, leadingDigits: Int): Result? {
        if (leadingDigits !in 2..3) return null
        return classifyAt(bitmap, lineBox, leadingDigits, leadingDigits + 5)
    }

    /**
     * Full-read path: ML Kit read the Hangul (possibly wrong), so [readLength] characters tightly
     * fill the box and the Hangul is at [hangulIndex]. Locating it from the actual read is far more
     * robust than width estimation when ML Kit splits the line into blocks.
     */
    fun classifyInRead(bitmap: Bitmap, lineBox: Rect, hangulIndex: Int, readLength: Int): Result? {
        if (hangulIndex < 0 || readLength < 5 || hangulIndex >= readLength) return null
        return classifyAt(bitmap, lineBox, hangulIndex, readLength)
    }

    private fun classifyAt(bitmap: Bitmap, lineBox: Rect, slotIndex: Int, totalSlots: Int): Result? {
        val itp = interpreter ?: return null
        if (totalSlots <= 0 || lineBox.width() <= 0 || lineBox.height() <= 0) return null
        val slotPitch = lineBox.width().toFloat() / totalSlots
        val baseCenterX = lineBox.left + (slotIndex + 0.5f) * slotPitch
        val top = (lineBox.top - lineBox.height() * 0.08f).toInt().coerceAtLeast(0)
        val bottom = (lineBox.bottom + lineBox.height() * 0.08f).toInt().coerceAtMost(bitmap.height)
        if (bottom - top < 8) return null

        var best: Result? = null
        for (dx in CENTER_OFFSETS) {
            val cx = baseCenterX + dx * slotPitch
            for (hw in HALF_WIDTHS) {
                val half = slotPitch * hw
                val left = (cx - half).toInt().coerceAtLeast(0)
                val right = (cx + half).toInt().coerceAtMost(bitmap.width)
                if (right - left < 8) continue
                val r = runOne(itp, bitmap, left, top, right, bottom) ?: continue
                if (best == null || r.confidence > best.confidence) best = r
            }
        }
        return best
    }

    private fun runOne(itp: Interpreter, bitmap: Bitmap, left: Int, top: Int, right: Int, bottom: Int): Result? {
        val w = right - left; val h = bottom - top
        if (w < 1 || h < 1) return null
        // Bilinear (averaging) downscale to SIZExSIZE — must match training's cv2.resize. Plain
        // nearest-neighbour sampling aliases high-res crops (e.g. portrait frames), distorting the
        // ㅓ tick into ㅗ (러→로); averaging preserves the stroke shape.
        val cropped = Bitmap.createBitmap(bitmap, left, top, w, h)
        val scaled = Bitmap.createScaledBitmap(cropped, SIZE, SIZE, true)
        val px = IntArray(SIZE * SIZE)
        scaled.getPixels(px, 0, SIZE, 0, 0, SIZE, SIZE)
        if (scaled != cropped) scaled.recycle()
        cropped.recycle()

        val gray = FloatArray(SIZE * SIZE)
        var sum = 0.0; var sumSq = 0.0
        for (i in px.indices) {
            val c = px[i]
            val g = (Color.red(c) * 30 + Color.green(c) * 59 + Color.blue(c) * 11) / 100f
            gray[i] = g; sum += g; sumSq += g.toDouble() * g
        }
        val n = SIZE * SIZE
        val mean = (sum / n).toFloat()
        val std = Math.sqrt(sumSq / n - (sum / n) * (sum / n)).toFloat() + 1e-6f
        input.rewind()
        for (v in gray) input.putFloat((v - mean) / std)
        input.rewind()
        itp.run(input, output)

        var bi = 0; var bv = output[0][0]
        for (i in 1 until LABELS.size) if (output[0][i] > bv) { bv = output[0][i]; bi = i }
        return Result(LABELS[bi], bv)
    }
}
