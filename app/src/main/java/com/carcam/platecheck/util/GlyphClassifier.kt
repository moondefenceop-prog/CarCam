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

    /** Classifies the usage glyph on a plate line. [leadingDigits] is the count before the Hangul. */
    fun classify(bitmap: Bitmap, lineBox: Rect, leadingDigits: Int): Result? {
        val itp = interpreter ?: return null
        if (leadingDigits !in 2..3 || lineBox.width() <= 0 || lineBox.height() <= 0) return null
        val totalSlots = leadingDigits + 5
        val slotPitch = lineBox.width().toFloat() / totalSlots
        val baseCenterX = lineBox.left + (leadingDigits + 0.5f) * slotPitch
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
        // Nearest-neighbour resample the grayscale slot into SIZExSIZE, collecting stats for standardization.
        val gray = FloatArray(SIZE * SIZE)
        var sum = 0.0; var sumSq = 0.0
        for (oy in 0 until SIZE) {
            val sy = top + oy * h / SIZE
            for (ox in 0 until SIZE) {
                val sx = left + ox * w / SIZE
                val c = bitmap.getPixel(sx, sy)
                val g = (Color.red(c) * 30 + Color.green(c) * 59 + Color.blue(c) * 11) / 100f
                gray[oy * SIZE + ox] = g
                sum += g; sumSq += g.toDouble() * g
            }
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
