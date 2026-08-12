package com.carcam.platecheck.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * On-device 40-class classifier for the plate usage glyph (용도기호).
 *
 * The model is trained through the same crop and binarisation [GlyphPreprocess] applies here;
 * feeding it anything else costs several points, which is how a clean 나 used to come back as
 * 다. On the labelled real-plate set this combination reads 31/31, against 18/28 for the
 * original equal-width slot with a confidence-scored sweep.
 *
 * Note what is deliberately absent: there is no sweep over candidate windows scored by
 * confidence. A crop that clips a glyph in half outscores the whole glyph, so choosing the
 * most confident window actively selects the broken one.
 */
object GlyphClassifier {
    data class Result(val character: Char, val confidence: Float)

    // Class order == ml/labels.npy == VALID_MIDDLE set.
    private val LABELS = "가나다라마거너더러머버서어저고노도로모보소오조구누두루무부수우주아자바사하허호배".toCharArray()

    /** Characters whose initial consonant encloses a counter: ㅁ, ㅂ, ㅇ, ㅎ. */
    private val CLOSED_GLYPHS = "마머모무바버보부배아어오우하허호".toSet()

    private const val SIZE = 48
    private const val ASSET = "glyph_cnn.tflite"
    private const val HALF_W = 0.62f

    /** Diagnostics: when set, [lastCrop] holds the exact image the model was last given.
     *  Instrumented tests dump it — reimplementing the crop to inspect it has already once
     *  shown a picture the classifier never saw. */
    @Volatile var debugCapture = false
    @Volatile var lastCrop: GlyphPreprocess.Gray? = null
        private set
    @Volatile var lastGeometry: String = ""
        private set

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
     * Numeric-only path: ML Kit dropped the Hangul, so the box spans (leadingDigits + 1 + 4)
     * equal slots and the Hangul sits at index [leadingDigits].
     */
    fun classify(bitmap: Bitmap, lineBox: Rect, leadingDigits: Int): Result? {
        if (leadingDigits !in 2..3) return null
        return classifyAt(bitmap, lineBox, leadingDigits, leadingDigits + 5)
    }

    /**
     * Full-read path: ML Kit read the Hangul (possibly wrong), so [readLength] characters fill
     * the box and the Hangul is at [hangulIndex].
     */
    fun classifyInRead(bitmap: Bitmap, lineBox: Rect, hangulIndex: Int, readLength: Int): Result? {
        if (hangulIndex < 0 || readLength < 5 || hangulIndex >= readLength) return null
        return classifyAt(bitmap, lineBox, hangulIndex, readLength)
    }

    /** Sub-rectangle of a [GlyphPreprocess.Gray]. */
    private fun crop(g: GlyphPreprocess.Gray, x0: Int, y0: Int, x1: Int, y1: Int): GlyphPreprocess.Gray? {
        val l = x0.coerceIn(0, g.w); val r = x1.coerceIn(0, g.w)
        val t = y0.coerceIn(0, g.h); val b = y1.coerceIn(0, g.h)
        if (r - l < 2 || b - t < 2) return null
        val px = IntArray((r - l) * (b - t))
        for (y in t until b) System.arraycopy(g.px, y * g.w + l, px, (y - t) * (r - l), r - l)
        return GlyphPreprocess.Gray(r - l, b - t, px)
    }

    private fun classifyAt(bitmap: Bitmap, lineBox: Rect, slotIndex: Int, totalSlots: Int): Result? {
        val itp = interpreter ?: return null
        if (totalSlots <= 0 || lineBox.width() <= 0 || lineBox.height() <= 0) return null

        // ML Kit's box is looser than the text it found, and slot pitch is width divided by the
        // character count, so trusting it directly inflates the pitch and the crop spills into
        // the neighbouring characters. Re-derive the box from the ink first.
        val rawWhole = GlyphPreprocess.fromBitmap(
            bitmap,
            lineBox.left.coerceIn(0, bitmap.width), lineBox.top.coerceIn(0, bitmap.height),
            lineBox.right.coerceIn(0, bitmap.width), lineBox.bottom.coerceIn(0, bitmap.height)
        ) ?: return null
        // Straighten first. Column profiles run the full height of the band, so on a tilted
        // plate every column mixes the top of one character with the bottom of the next: the
        // valleys between characters fill in and the crop lands between them.
        val whole = GlyphPreprocess.deskew(rawWhole)

        // Component-derived box first; the ink-profile tighten is the fallback when too
        // few components survive (small or broken glyphs).
        val tight = GlyphPreprocess.textBox(whole) ?: GlyphPreprocess.tightenBox(whole)
        val bx0 = tight?.get(0) ?: 0
        val by0 = tight?.get(1) ?: 0
        val bx1 = tight?.get(2) ?: whole.w
        val by1 = tight?.get(3) ?: whole.h
        if (by1 - by0 < 6 || bx1 - bx0 < 8) return null
        val boxLeft = lineBox.left + bx0
        val top = lineBox.top + by0
        val boxRight = lineBox.left + bx1
        val bot = lineBox.top + by1

        // Everything from here works inside the straightened image.
        val textBand = crop(whole, bx0, by0, bx1, by1) ?: return null

        // Prefer counting in from the trailing digits. Equal slots assume the box covers every
        // character that was read, and it does not always: one measured frame reported
        // "10허7399" from a box that started at the 0, so slot 2 fell in the gap after the
        // glyph and the model was handed blank plate.
        val slotPitch = (bx1 - bx0).toFloat() / totalSlots
        val slotCx = (bx0 + (slotIndex + 0.5f) * slotPitch)
        val fromRight = if (slotIndex == totalSlots - 5) {
            GlyphPreprocess.hangulFromRight(textBand)
        } else null
        // Accept the right-anchored answer only near the slot estimate. It fixes a box that
        // dropped the leading digit — about one pitch of shift — but a stray run on the right
        // can otherwise throw it several characters off.
        val useRight = fromRight != null &&
            Math.abs((bx0 + fromRight[0]) - slotCx) <= slotPitch * 1.3f
        val pitch = if (useRight) fromRight!![1] else slotPitch
        val estimatedCx = if (useRight) bx0 + fromRight!![0] else slotCx

        // Work on a band wide enough to hold the neighbouring characters the valley search
        // needs, but no wider — the profile is the expensive part.
        val bandLeft = (estimatedCx - pitch * 2f).toInt().coerceAtLeast(0)
        val bandRight = (estimatedCx + pitch * 2f).toInt().coerceAtMost(whole.w)
        val band = crop(whole, bandLeft, by0, bandRight, by1) ?: return null

        val cxInBand = estimatedCx - bandLeft
        val snap = GlyphPreprocess.snapToGlyph(band, 0, band.h, cxInBand, pitch)
        val centre = if (snap != null) (snap[0] + snap[1]) / 2f else cxInBand

        val half = pitch * HALF_W
        val l = (centre - half).toInt().coerceAtLeast(0)
        val r = (centre + half).toInt().coerceAtMost(band.w)
        if (r - l < 4) return null

        val cropPx = IntArray((r - l) * band.h)
        for (y in 0 until band.h) {
            System.arraycopy(band.px, y * band.w + l, cropPx, y * (r - l), r - l)
        }
        val trimmed = GlyphPreprocess.trimDarkBackground(
            GlyphPreprocess.Gray(r - l, band.h, cropPx)
        )
        val binary = GlyphPreprocess.otsu(trimmed)
        if (debugCapture) {
            lastCrop = binary
            lastGeometry = "box=%d,%d,%d,%d crop=%d,%d,%d,%d pitch=%.1f right=%b".format(
                boxLeft, top, boxRight, bot,
                lineBox.left + bandLeft + l, top, lineBox.left + bandLeft + r, bot,
                pitch, useRight)
        }

        val vector = GlyphPreprocess.toModelInput(binary, SIZE)
        input.rewind()
        for (v in vector) input.putFloat(v)
        input.rewind()
        synchronized(this) { itp.run(input, output) }

        val probs = output[0]
        // Rule out closed-consonant characters when the image plainly has no counter. Without
        // it a 40-way softmax, which cannot say "none of these", asserts strokes that are not
        // in the picture — a clean 고 came back as 모 at 0.93.
        val allowClosed = GlyphPreprocess.holeCount(binary) > 0
        var bi = -1
        var bv = -1f
        for (i in LABELS.indices) {
            if (!allowClosed && LABELS[i] in CLOSED_GLYPHS) continue
            if (probs[i] > bv) { bv = probs[i]; bi = i }
        }
        if (bi < 0) return null
        return Result(LABELS[bi], bv)
    }
}
