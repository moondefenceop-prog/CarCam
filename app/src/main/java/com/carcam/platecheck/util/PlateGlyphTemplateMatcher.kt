package com.carcam.platecheck.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import kotlin.math.max

/** Lightweight fallback for the single Hangul usage glyph on a modern one-line plate. */
object PlateGlyphTemplateMatcher {
    data class Match(val character: Char, val score: Float, val margin: Float)

    private const val W = 48
    private const val H = 72
    private val glyphs = listOf(
        '가', '나', '다', '라', '마', '거', '너', '더', '러', '머', '버', '서', '어', '저',
        '고', '노', '도', '로', '모', '보', '소', '오', '조', '구', '누', '두', '루', '무',
        '부', '수', '우', '주', '아', '자', '바', '사', '하', '허', '호', '배'
    )
    private val typefaces by lazy {
        listOf(
            Typeface.create("sans-serif-condensed", Typeface.BOLD),
            Typeface.create("sans-serif", Typeface.BOLD),
            Typeface.DEFAULT_BOLD
        ).distinct()
    }
    private val templates by lazy {
        glyphs.associateWith { char -> typefaces.map { render(char, it) } }
    }

    // ML Kit's plate-line box jitters: it often drops the leftmost digit from the geometry (but not
    // the text), and the exact left/right edges shift frame to frame. A single-point estimate of the
    // Hangul slot is therefore only accurate on clean, head-on shots — on noisier live-camera frames
    // it can be off by up to ~half a slot, which is what pushes the match below the confidence margin.
    // Sweep the slot's horizontal center (in units of the character pitch) and its width, keeping the
    // most confident hit, so residual geometry error still lands a well-aligned crop.
    private val CENTER_OFFSET_FACTORS = floatArrayOf(-0.5f, -0.35f, -0.2f, -0.1f, 0f, 0.1f, 0.2f, 0.35f, 0.5f)
    private val HALF_WIDTH_FACTORS = floatArrayOf(0.45f, 0.55f, 0.66f)

    /**
     * [lineBox] is ML Kit's box for the full plate line. [leadingDigits] is normally 3.
     * Modern plates have 8 visual slots: 3 digits, one Hangul, and 4 digits.
     */
    fun matchModernPlate(bitmap: Bitmap, lineBox: Rect, leadingDigits: Int): Match? {
        if (leadingDigits !in 2..3 || lineBox.width() <= 0 || lineBox.height() <= 0) return null
        // Locate the Hangul from the box WIDTH, not its height: the line box spans all N character
        // slots (leadingDigits + 1 Hangul + 4 trailing), so the pitch is width/N and the Hangul sits
        // at index [leadingDigits]. This is far more accurate than a height-derived slot guess, which
        // drifts by several pixels per slot and pushed the crop off the glyph on real-camera frames.
        val totalSlots = leadingDigits + 5
        val slotPitch = lineBox.width().toFloat() / totalSlots
        val baseCenterX = lineBox.left + (leadingDigits + 0.5f) * slotPitch
        val top = (lineBox.top - lineBox.height() * 0.08f).toInt().coerceAtLeast(0)
        val bottom = (lineBox.bottom + lineBox.height() * 0.08f).toInt().coerceAtMost(bitmap.height)
        if (bottom - top < 8) return null

        var best: Match? = null
        for (dxFactor in CENTER_OFFSET_FACTORS) {
            val centerX = baseCenterX + dxFactor * slotPitch
            for (hwFactor in HALF_WIDTH_FACTORS) {
                val halfWidth = slotPitch * hwFactor
                val left = (centerX - halfWidth).toInt().coerceAtLeast(0)
                val right = (centerX + halfWidth).toInt().coerceAtMost(bitmap.width)
                if (right - left < 8) continue
                val candidate = matchCrop(bitmap, left, top, right, bottom) ?: continue
                // Prefer the crop that maximizes confidence overall: a well-centered glyph scores
                // high AND separates cleanly from the runner-up, so score+margin captures both.
                if (best == null || candidate.score + candidate.margin > best.score + best.margin) {
                    best = candidate
                }
            }
        }
        return best
    }

    private fun matchCrop(bitmap: Bitmap, left: Int, top: Int, right: Int, bottom: Int): Match? {
        val observed = normalize(Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top))
            ?: return null
        val ranked = glyphs.map { char ->
            char to templates.getValue(char).maxOf { dice(observed, it) }
        }.sortedByDescending { it.second }
        val best = ranked[0]
        val second = ranked[1]
        return Match(best.first, best.second, best.second - second.second)
    }

    fun isConfident(match: Match): Boolean = match.score >= 0.60f && match.margin >= 0.05f

    private fun render(char: Char, typeface: Typeface): BooleanArray {
        val bitmap = Bitmap.createBitmap(96, 144, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 112f
            this.typeface = typeface
            textAlign = Paint.Align.CENTER
        }
        val y = 72f - (paint.ascent() + paint.descent()) / 2f
        canvas.drawText(char.toString(), 48f, y, paint)
        return normalize(bitmap) ?: BooleanArray(W * H)
    }

    private fun normalize(source: Bitmap): BooleanArray? {
        val width = source.width
        val height = source.height
        val gray = IntArray(width * height)
        val histogram = IntArray(256)
        var sum = 0L
        for (y in 0 until height) for (x in 0 until width) {
            val c = source.getPixel(x, y)
            val g = (Color.red(c) * 30 + Color.green(c) * 59 + Color.blue(c) * 11) / 100
            gray[y * width + x] = g
            histogram[g]++
            sum += g
        }
        var backgroundWeight = 0
        var backgroundSum = 0L
        var bestVariance = -1.0
        var threshold = 128
        val total = width * height
        for (t in 0..255) {
            backgroundWeight += histogram[t]
            if (backgroundWeight == 0) continue
            val foregroundWeight = total - backgroundWeight
            if (foregroundWeight == 0) break
            backgroundSum += t.toLong() * histogram[t]
            val meanB = backgroundSum.toDouble() / backgroundWeight
            val meanF = (sum - backgroundSum).toDouble() / foregroundWeight
            val variance = backgroundWeight.toDouble() * foregroundWeight * (meanB - meanF) * (meanB - meanF)
            if (variance > bestVariance) { bestVariance = variance; threshold = t }
        }
        var left = width; var top = height; var right = -1; var bottom = -1
        for (y in 0 until height) for (x in 0 until width) {
            if (gray[y * width + x] < threshold) {
                left = minOf(left, x); right = maxOf(right, x); top = minOf(top, y); bottom = maxOf(bottom, y)
            }
        }
        if (right <= left || bottom <= top) return null
        // Ignore thin crop-edge artifacts from neighboring digits.
        val padX = max(1, (right - left + 1) / 18)
        left = (left + padX).coerceAtMost(right)
        right = (right - padX).coerceAtLeast(left)
        val out = BooleanArray(W * H)
        val inkW = right - left + 1
        val inkH = bottom - top + 1
        for (oy in 0 until H) for (ox in 0 until W) {
            val sx = left + ox * inkW / W
            val sy = top + oy * inkH / H
            out[oy * W + ox] = gray[sy * width + sx] < threshold
        }
        return out
    }

    private fun dice(a: BooleanArray, b: BooleanArray): Float {
        var intersection = 0
        var countA = 0
        var countB = 0
        for (i in a.indices) {
            if (a[i]) countA++
            if (b[i]) countB++
            if (a[i] && b[i]) intersection++
        }
        return if (countA + countB == 0) 0f else 2f * intersection / (countA + countB)
    }
}
