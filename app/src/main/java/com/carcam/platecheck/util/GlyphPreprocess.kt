package com.carcam.platecheck.util

import android.graphics.Bitmap
import android.graphics.Color

/**
 * The crop and preprocessing the glyph classifier expects, ported from `ml/glyph_crop.py`.
 *
 * Training and inference must agree on the picture, and the offline work showed that almost
 * every "misclassification" was really the model being handed the wrong one. Each step below
 * fixes a specific failure that was measured, so they are not interchangeable with the
 * obvious alternatives:
 *
 *  - the glyph is located by the **valleys** between characters, not by grouping ink, because
 *    a Hangul glyph is several disconnected parts (나 = ㄴ + ㅏ) while blur bridges neighbours
 *    into one blob — grouping either splits one glyph or swallows two;
 *  - the crop takes **no vertical margin** beyond the text line, since the plate frame sits
 *    just outside it and a full-width bar reads as a stroke (나 + bar above = 다);
 *  - rows much darker than the plate face are dropped, or a tilted plate's background wedge
 *    dominates the threshold and erases the glyph;
 *  - the crop is binarised, which is what lets a worn plate read at all.
 *
 * No OpenCV in this app, so the thresholds are implemented directly: an integral image for
 * the adaptive mean, a histogram for Otsu, and a flood fill for counting enclosed counters.
 */
object GlyphPreprocess {

    /** Grayscale rectangle, values 0..255. */
    class Gray(val w: Int, val h: Int, val px: IntArray) {
        operator fun get(x: Int, y: Int) = px[y * w + x]
    }

    fun fromBitmap(bmp: Bitmap, left: Int, top: Int, right: Int, bottom: Int): Gray? {
        val l = left.coerceIn(0, bmp.width); val r = right.coerceIn(0, bmp.width)
        val t = top.coerceIn(0, bmp.height); val b = bottom.coerceIn(0, bmp.height)
        val w = r - l; val h = b - t
        if (w < 2 || h < 2) return null
        val argb = IntArray(w * h)
        bmp.getPixels(argb, 0, w, l, t, w, h)
        val out = IntArray(w * h)
        for (i in argb.indices) {
            val c = argb[i]
            out[i] = (Color.red(c) * 30 + Color.green(c) * 59 + Color.blue(c) * 11) / 100
        }
        return Gray(w, h, out)
    }

    /**
     * Adaptive mean threshold, inverted: true where ink is. Matches OpenCV's
     * ADAPTIVE_THRESH_MEAN_C with THRESH_BINARY_INV, block 31, C 15.
     */
    fun inkMask(g: Gray, block: Int = 31, c: Int = 15): BooleanArray {
        val w = g.w; val h = g.h
        // Integral image so each window mean is four lookups regardless of block size.
        val integral = LongArray((w + 1) * (h + 1))
        for (y in 0 until h) {
            var rowSum = 0L
            for (x in 0 until w) {
                rowSum += g[x, y]
                integral[(y + 1) * (w + 1) + (x + 1)] = integral[y * (w + 1) + (x + 1)] + rowSum
            }
        }
        val half = block / 2
        val mask = BooleanArray(w * h)
        for (y in 0 until h) {
            val y0 = (y - half).coerceAtLeast(0); val y1 = (y + half).coerceAtMost(h - 1)
            for (x in 0 until w) {
                val x0 = (x - half).coerceAtLeast(0); val x1 = (x + half).coerceAtMost(w - 1)
                val area = (x1 - x0 + 1).toLong() * (y1 - y0 + 1)
                val sum = integral[(y1 + 1) * (w + 1) + (x1 + 1)] -
                    integral[y0 * (w + 1) + (x1 + 1)] -
                    integral[(y1 + 1) * (w + 1) + x0] +
                    integral[y0 * (w + 1) + x0]
                mask[y * w + x] = g[x, y] < (sum / area) - c
            }
        }
        return mask
    }

    /**
     * Straighten a tilted text band.
     *
     * Everything downstream profiles columns across the full height of the band, so on a
     * tilted plate each column mixes the top of one character with the bottom of the next.
     * The valleys between characters fill in, the glyph cannot be separated, and the crop
     * lands between characters — which is exactly what happened on a plate photographed at an
     * angle.
     *
     * The skew is read off the text itself: the vertical centre of ink drifts linearly across
     * a tilted line, so a least-squares fit of that drift gives the angle.
     */
    fun deskew(g: Gray, maxSlope: Float = 0.45f): Gray {
        if (g.w < 12 || g.h < 8) return g
        val mask = inkMask(g)
        var n = 0
        var sx = 0.0; var sy = 0.0; var sxx = 0.0; var sxy = 0.0
        for (x in 0 until g.w) {
            var sum = 0L; var count = 0
            for (y in 0 until g.h) if (mask[y * g.w + x]) { sum += y; count++ }
            if (count < 2) continue
            val cy = sum.toDouble() / count
            n++; sx += x; sy += cy; sxx += x.toDouble() * x; sxy += x * cy
        }
        if (n < 8) return g
        val denom = n * sxx - sx * sx
        if (Math.abs(denom) < 1e-6) return g
        val slope = ((n * sxy - sx * sy) / denom).toFloat()
        // Below this the shear costs more in resampling than it recovers; above it, the fit is
        // being driven by something that is not a text line.
        if (Math.abs(slope) < 0.03f || Math.abs(slope) > maxSlope) return g

        // A horizontal shear is enough: it straightens the baseline without rotating strokes
        // into each other, and needs only a per-column vertical shift.
        val out = IntArray(g.w * g.h)
        val midX = g.w / 2f
        for (x in 0 until g.w) {
            val shift = slope * (x - midX)
            for (y in 0 until g.h) {
                val srcY = y + shift
                val y0 = Math.floor(srcY.toDouble()).toInt()
                val f = (srcY - y0)
                val a = if (y0 in 0 until g.h) g[x, y0] else 255
                val b = if (y0 + 1 in 0 until g.h) g[x, y0 + 1] else 255
                out[y * g.w + x] = (a * (1 - f) + b * f).toInt().coerceIn(0, 255)
            }
        }
        return Gray(g.w, g.h, out)
    }

    /**
     * Locate the usage glyph by counting in from the RIGHT, and return (centre, pitch).
     *
     * Dividing the box into equal slots assumes the box covers every character that was read.
     * It often does not: on a measured frame ML Kit reported "10허7399" but its box began at
     * the 0, covering six characters for a seven-character read. Slot 2 then landed in the gap
     * between the glyph and the digits, and the classifier was asked to identify blank plate.
     *
     * The last four characters are always digits, never split into parts, and sit at an even
     * pitch — so they make a dependable ruler. The glyph is whatever ink lies immediately left
     * of them, agglomerated while it still fits one character width.
     *
     * Returns null when the columns do not look like a plate line, leaving the caller to fall
     * back to the slot estimate.
     */
    fun hangulFromRight(g: Gray): FloatArray? {
        if (g.w < 16 || g.h < 8) return null
        val mask = inkMask(g)
        val minInk = (g.h * 0.10f).toInt().coerceAtLeast(1)
        val on = BooleanArray(g.w)
        for (x in 0 until g.w) {
            var c = 0
            for (y in 0 until g.h) if (mask[y * g.w + x]) c++
            on[x] = c >= minInk
        }
        val runs = ArrayList<IntArray>()
        var i = 0
        while (i < g.w) {
            if (on[i]) {
                var j = i
                while (j + 1 < g.w && on[j + 1]) j++
                if (j - i + 1 >= 2) runs.add(intArrayOf(i, j))
                i = j + 1
            } else i++
        }
        if (runs.size < 5) return null

        val tail = runs.takeLast(4)
        val centres = tail.map { (it[0] + it[1]) / 2f }
        val gaps = centres.zipWithNext { a, b -> b - a }.sorted()
        val pitch = gaps[gaps.size / 2]
        if (pitch <= 2f) return null
        // Digits of one plate line are evenly spaced; anything else is not the tail we want.
        if (gaps.first() < pitch * 0.55f || gaps.last() > pitch * 1.8f) return null

        val tailLeft = tail.first()[0]
        var idx = runs.indexOfFirst { it[0] == tailLeft } - 1
        if (idx < 0) return null
        var left = runs[idx][0]
        var right = runs[idx][1]
        // A Hangul glyph is several disconnected parts; pull in neighbours while the result
        // still fits inside one character.
        while (idx - 1 >= 0 && (right - runs[idx - 1][0] + 1) <= pitch * 1.05f) {
            idx--
            left = runs[idx][0]
        }
        if (right - left < 2) return null
        return floatArrayOf((left + right) / 2f, pitch)
    }

    /**
     * Shrink a text box onto the glyph rows and columns actually inside it.
     *
     * ML Kit's box is looser than the text: on a measured frame it was 131x38 where the ink
     * spans 103x28. Slot pitch is the box width divided by the character count, so a loose box
     * inflates the pitch and the crop then reaches into the neighbouring characters — that is
     * what made the ported pipeline read 버 as 배 while the offline one, which derives the box
     * from the glyph components themselves, read it correctly.
     *
     * Rows that are nearly all ink are the plate's border bar, not text, and are excluded:
     * keeping them would defeat the whole point of cropping without a vertical margin.
     *
     * Returns (left, top, right, bottom) relative to [g], or null if nothing text-like is found.
     */
    /**
     * Derive the text box from the glyph components inside [g], the way the offline pipeline
     * does, instead of trusting the OCR box.
     *
     * Measured on real frames, ML Kit's box runs about 25% wider than the characters: it takes
     * in the plate border and its margins. Slot pitch is width over character count, so that
     * inflates the pitch by the same fraction and the crop then reaches a quarter of a
     * character into each neighbour — enough to turn 조 into 소 at full confidence.
     *
     * Components are kept only when they look like one character of a single line: similar
     * height to their neighbours, taller than wide, not a hairline, not a solid block. That is
     * what excludes the border, the bolts and the country badge.
     *
     * Returns (left, top, right, bottom) relative to [g], or null.
     */
    fun textBox(g: Gray): IntArray? {
        if (g.w < 8 || g.h < 8) return null
        val mask = inkMask(g)
        val label = IntArray(g.w * g.h) { -1 }
        data class Comp(var x0: Int, var y0: Int, var x1: Int, var y1: Int, var area: Int)
        val comps = ArrayList<Comp>()
        val queue = ArrayDeque<Int>()
        for (start in 0 until g.w * g.h) {
            if (!mask[start] || label[start] >= 0) continue
            val id = comps.size
            val c = Comp(start % g.w, start / g.w, start % g.w, start / g.w, 0)
            label[start] = id; queue.addLast(start)
            while (queue.isNotEmpty()) {
                val i = queue.removeLast()
                val x = i % g.w; val y = i / g.w
                c.area++
                if (x < c.x0) c.x0 = x; if (x > c.x1) c.x1 = x
                if (y < c.y0) c.y0 = y; if (y > c.y1) c.y1 = y
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = x + dx; val ny = y + dy
                    if (nx in 0 until g.w && ny in 0 until g.h) {
                        val j = ny * g.w + nx
                        if (mask[j] && label[j] < 0) { label[j] = id; queue.addLast(j) }
                    }
                }
            }
            comps.add(c)
        }
        val glyphs = comps.filter { c ->
            val w = c.x1 - c.x0 + 1
            val h = c.y1 - c.y0 + 1
            h >= g.h * 0.25 && h <= g.h * 0.98 &&
                w <= h * 1.6 && w >= h * 0.08 &&
                c.area >= 0.05 * w * h && c.area <= 0.90 * w * h
        }
        if (glyphs.size < 3) return null
        val heights = glyphs.map { it.y1 - it.y0 + 1 }.sorted()
        val mh = heights[heights.size / 2]
        val centres = glyphs.map { (it.y0 + it.y1) / 2 }.sorted()
        val mc = centres[centres.size / 2]
        val kept = glyphs.filter { c ->
            val h = c.y1 - c.y0 + 1
            h >= mh * 0.65 && h <= mh * 1.35 && Math.abs((c.y0 + c.y1) / 2 - mc) <= mh * 0.5
        }
        if (kept.size < 3) return null
        return intArrayOf(
            kept.minOf { it.x0 }, kept.minOf { it.y0 },
            kept.maxOf { it.x1 } + 1, kept.maxOf { it.y1 } + 1
        )
    }

    fun tightenBox(g: Gray): IntArray? {
        if (g.w < 4 || g.h < 4) return null
        val mask = inkMask(g)
        val rowInk = IntArray(g.h)
        for (y in 0 until g.h) {
            var c = 0
            for (x in 0 until g.w) if (mask[y * g.w + x]) c++
            rowInk[y] = c
        }
        var top = -1; var bot = -1
        for (y in 0 until g.h) {
            val frac = rowInk[y].toFloat() / g.w
            if (frac in 0.04f..0.60f) { if (top < 0) top = y; bot = y }
        }
        if (top < 0 || bot - top < 3) return null

        val colInk = IntArray(g.w)
        for (x in 0 until g.w) {
            var c = 0
            for (y in top..bot) if (mask[y * g.w + x]) c++
            colInk[x] = c
        }
        val minCol = ((bot - top + 1) * 0.06f).toInt().coerceAtLeast(1)
        var left = -1; var right = -1
        for (x in 0 until g.w) if (colInk[x] >= minCol) { if (left < 0) left = x; right = x }
        if (left < 0 || right - left < 3) return null
        return intArrayOf(left, top, right + 1, bot + 1)
    }

    /**
     * Locate the character cell around slot centre [cx] within rows [top]..[bot] of [g],
     * returning (left, right) in [g]'s coordinates, or null.
     */
    fun snapToGlyph(g: Gray, top: Int, bot: Int, cx: Float, pitch: Float): IntArray? {
        if (pitch <= 0f || bot - top < 4) return null
        val x0 = (cx - pitch * 1.2f).toInt().coerceIn(0, g.w)
        val x1 = (cx + pitch * 1.2f).toInt().coerceIn(0, g.w)
        if (x1 - x0 < 6) return null

        val mask = inkMask(g)
        val n = x1 - x0
        val prof = FloatArray(n)
        for (x in x0 until x1) {
            var count = 0
            for (y in top until bot) if (mask[y * g.w + x]) count++
            prof[x - x0] = count.toFloat()
        }
        if (prof.all { it <= 0f }) return null

        // Smooth, or single-pixel gaps in a stroke read as character boundaries.
        val k = (pitch * 0.08f).toInt().coerceAtLeast(1)
        val smooth = FloatArray(n)
        for (i in 0 until n) {
            var sum = 0f; var cnt = 0
            for (j in (i - k / 2)..(i + k / 2)) if (j in 0 until n) { sum += prof[j]; cnt++ }
            smooth[i] = if (cnt > 0) sum / cnt else prof[i]
        }

        val ci = (cx.toInt() - x0).coerceIn(0, n - 1)
        fun valley(lo: Float, hi: Float): Int? {
            val a = lo.toInt().coerceIn(0, n); val b = hi.toInt().coerceIn(0, n)
            if (b - a < 1) return null
            var best = a
            for (i in a until b) if (smooth[i] < smooth[best]) best = i
            return best
        }
        // The boundary sits about half a pitch out; searching a band around that survives both
        // a split glyph and one merged into its neighbour by blur.
        val l = valley(ci - pitch * 0.80f, ci - pitch * 0.28f) ?: return null
        val r = valley(ci + pitch * 0.28f, ci + pitch * 0.80f) ?: return null
        if (r - l < 3) return null
        return intArrayOf(x0 + l, x0 + r)
    }

    /**
     * Drop rows of dark scene sitting outside the plate. An axis-aligned box around a tilted
     * plate traps wedges of background that would otherwise dominate the threshold.
     */
    fun trimDarkBackground(g: Gray, keep: Float = 0.55f): Gray {
        if (g.h < 6) return g
        val sorted = g.px.clone().also { it.sort() }
        val plate = sorted[(sorted.size * 85 / 100).coerceAtMost(sorted.size - 1)]
        if (plate <= 1) return g
        val rowMedian = IntArray(g.h)
        val row = IntArray(g.w)
        for (y in 0 until g.h) {
            System.arraycopy(g.px, y * g.w, row, 0, g.w)
            row.sort()
            rowMedian[y] = row[g.w / 2]
        }
        var first = -1; var last = -1
        for (y in 0 until g.h) {
            if (rowMedian[y] >= plate * keep) { if (first < 0) first = y; last = y }
        }
        if (first < 0 || last - first + 1 < maxOf(4, g.h * 35 / 100)) return g
        val nh = last - first + 1
        val px = IntArray(g.w * nh)
        System.arraycopy(g.px, first * g.w, px, 0, g.w * nh)
        return Gray(g.w, nh, px)
    }

    /** Otsu threshold; returns 0/255 with ink at 0 (dark strokes on white). */
    fun otsu(g: Gray): Gray {
        val hist = IntArray(256)
        for (v in g.px) hist[v.coerceIn(0, 255)]++
        val total = g.px.size
        var sum = 0.0
        for (i in 0 until 256) sum += i.toDouble() * hist[i]
        var sumB = 0.0; var wB = 0; var best = 0.0; var threshold = 127
        for (t in 0 until 256) {
            wB += hist[t]
            if (wB == 0) continue
            val wF = total - wB
            if (wF == 0) break
            sumB += t.toDouble() * hist[t]
            val mB = sumB / wB
            val mF = (sum - sumB) / wF
            val between = wB.toDouble() * wF * (mB - mF) * (mB - mF)
            if (between > best) { best = between; threshold = t }
        }
        val out = IntArray(g.px.size)
        for (i in g.px.indices) out[i] = if (g.px[i] <= threshold) 0 else 255
        return Gray(g.w, g.h, out)
    }

    /**
     * Enclosed counters in a binarised glyph. Of the 40 usage characters only those whose
     * initial consonant is ㅁ, ㅂ, ㅇ or ㅎ have one, so a count of zero rules those out — the
     * check that stops the model asserting a closed shape the image does not contain.
     */
    fun holeCount(binary: Gray, minFrac: Float = 0.012f): Int {
        val w = binary.w; val h = binary.h
        if (w < 4 || h < 4) return 0
        // Close first: a one-pixel break in a stroke would otherwise open a counter.
        val k = (minOf(w, h) * 0.03f).toInt().coerceAtLeast(1)
        val ink = BooleanArray(w * h)
        for (i in binary.px.indices) ink[i] = binary.px[i] < 128
        val dil = BooleanArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            var any = false
            var dy = -k
            while (dy <= k && !any) {
                var dx = -k
                while (dx <= k) {
                    val nx = x + dx; val ny = y + dy
                    if (nx in 0 until w && ny in 0 until h && ink[ny * w + nx]) { any = true; break }
                    dx++
                }
                dy++
            }
            dil[y * w + x] = any
        }
        val closed = BooleanArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            var all = true
            var dy = -k
            while (dy <= k && all) {
                var dx = -k
                while (dx <= k) {
                    val nx = x + dx; val ny = y + dy
                    if (nx in 0 until w && ny in 0 until h && !dil[ny * w + nx]) { all = false; break }
                    dx++
                }
                dy++
            }
            closed[y * w + x] = all
        }

        // Background reachable from the border is outside; anything else is a counter.
        val seen = BooleanArray(w * h)
        val stack = ArrayDeque<Int>()
        fun push(i: Int) { if (!seen[i] && !closed[i]) { seen[i] = true; stack.addLast(i) } }
        for (x in 0 until w) { push(x); push((h - 1) * w + x) }
        for (y in 0 until h) { push(y * w); push(y * w + w - 1) }
        while (stack.isNotEmpty()) {
            val i = stack.removeLast()
            val x = i % w; val y = i / w
            if (x > 0) push(i - 1); if (x < w - 1) push(i + 1)
            if (y > 0) push(i - w); if (y < h - 1) push(i + w)
        }

        val minArea = (w * h * minFrac).toInt().coerceAtLeast(4)
        var holes = 0
        val visited = BooleanArray(w * h)
        for (start in 0 until w * h) {
            if (closed[start] || seen[start] || visited[start]) continue
            var area = 0
            stack.addLast(start); visited[start] = true
            while (stack.isNotEmpty()) {
                val i = stack.removeLast(); area++
                val x = i % w; val y = i / w
                fun step(j: Int) { if (!closed[j] && !visited[j]) { visited[j] = true; stack.addLast(j) } }
                if (x > 0) step(i - 1); if (x < w - 1) step(i + 1)
                if (y > 0) step(i - w); if (y < h - 1) step(i + w)
            }
            if (area >= minArea) holes++
        }
        return holes
    }

    /** Resize to [size]x[size] with box averaging, then per-image standardisation. */
    fun toModelInput(g: Gray, size: Int): FloatArray {
        val out = FloatArray(size * size)
        for (oy in 0 until size) {
            val y0 = oy * g.h / size; val y1 = ((oy + 1) * g.h / size).coerceAtLeast(y0 + 1)
            for (ox in 0 until size) {
                val x0 = ox * g.w / size; val x1 = ((ox + 1) * g.w / size).coerceAtLeast(x0 + 1)
                var sum = 0; var n = 0
                for (y in y0 until minOf(y1, g.h)) for (x in x0 until minOf(x1, g.w)) {
                    sum += g[x, y]; n++
                }
                out[oy * size + ox] = if (n > 0) sum.toFloat() / n else 0f
            }
        }
        var mean = 0.0
        for (v in out) mean += v
        mean /= out.size
        var variance = 0.0
        for (v in out) variance += (v - mean) * (v - mean)
        val std = Math.sqrt(variance / out.size).toFloat() + 1e-6f
        for (i in out.indices) out[i] = ((out[i] - mean) / std).toFloat()
        return out
    }
}
