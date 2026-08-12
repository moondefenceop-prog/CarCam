package com.carcam.platecheck.util

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Collects frames the operator flags as misread.
 *
 * One tap, no typing: this is used one-handed at a barrier with a driver waiting, and the
 * correct plate can be read off the picture afterwards by whoever analyses it. Asking for it
 * at capture time buys nothing and costs the operator the moment they were trying to catch.
 *
 * What the app read is recorded instead — that is the part which cannot be recovered later,
 * since a frame alone does not say what the pipeline made of it.
 */
object CaptureStore {

    private const val DIR = "verify"
    private const val MANIFEST = "labels.csv"

    data class Saved(val file: File, val total: Int)

    fun dir(context: Context): File =
        File(context.getExternalFilesDir(null), DIR).apply { mkdirs() }

    fun count(context: Context): Int =
        dir(context).listFiles { f -> f.extension.equals("png", true) }?.size ?: 0

    /**
     * Save [bitmap] with what the app read. Returns null if writing failed — callers surface
     * that rather than silently losing it.
     */
    fun save(context: Context, bitmap: Bitmap, appRead: String): Saved? {
        // A recycled bitmap throws from compress() *after* the file exists, which is how empty
        // PNGs with no manifest row appeared. Refuse up front instead.
        if (bitmap.isRecycled) return null
        var created: File? = null
        return runCatching {
            val dir = dir(context)
            // Named by time and by what the app read, never by a claimed answer: the file is
            // evidence of a misreading, and calling it by the plate it is *supposed* to be
            // would make a wrong reading look like ground truth once the folder is pulled.
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.KOREA).format(Date())
            val safe = appRead.replace(Regex("[\\\\/:*?\"<>|\\s]"), "").ifEmpty { "none" }
            var file = File(dir, "cap_${stamp}_$safe.png")
            var n = 1
            while (file.exists()) file = File(dir, "cap_${stamp}_${safe}_$n.png").also { n++ }
            created = file
            FileOutputStream(file).use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) { "compress failed" }
            }

            val manifest = File(dir, MANIFEST)
            if (!manifest.exists()) {
                // BOM so Excel opens the Hangul correctly.
                manifest.writeText("﻿파일,앱인식,시각\r\n", Charsets.UTF_8)
            }
            manifest.appendText(
                "${file.name},$appRead,${System.currentTimeMillis()}\r\n",
                Charsets.UTF_8
            )
            Saved(file, count(context))
        }.getOrElse {
            // Never leave a zero-byte PNG behind: it looks like a capture but has no manifest
            // row, and it silently pollutes the evaluation set when the folder is pulled.
            created?.delete()
            null
        }
    }

    /** Remove empty PNGs left by earlier failed saves. */
    fun purgeEmpty(context: Context): Int {
        val bad = dir(context).listFiles { f ->
            f.extension.equals("png", true) && f.length() == 0L
        } ?: return 0
        bad.forEach { it.delete() }
        return bad.size
    }

    fun clear(context: Context) {
        dir(context).listFiles()?.forEach { it.delete() }
    }
}
