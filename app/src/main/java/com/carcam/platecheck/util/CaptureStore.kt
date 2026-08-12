package com.carcam.platecheck.util

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream

/**
 * Collects frames the operator says were read wrongly, together with the correct plate.
 *
 * A misread that a person can see but the pipeline cannot is only actionable with the picture
 * *and* the right answer attached; without the label a captured frame is just another image.
 * Files are named after the correct plate, which is the same convention the offline evaluation
 * set uses, so a pulled capture drops straight into it and becomes a regression case.
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
     * Save [bitmap] labelled with [correctPlate], recording what the app had read.
     * Returns null if writing failed — callers surface that rather than silently losing it.
     */
    fun save(context: Context, bitmap: Bitmap, correctPlate: String, appRead: String): Saved? {
        // A recycled bitmap throws from compress() *after* the file exists, which is how empty
        // PNGs with no manifest row appeared. Refuse up front instead.
        if (bitmap.isRecycled) return null
        var created: File? = null
        return runCatching {
            val dir = dir(context)
            // Same name pattern as the evaluation set: "154러7070_3.png". Suffix on collision so
            // repeat captures of one plate become separate cases instead of overwriting.
            val safe = correctPlate.replace(Regex("[\\\\/:*?\"<>|\\s]"), "")
            var file = File(dir, "$safe.png")
            var n = 1
            while (file.exists()) file = File(dir, "${safe}_$n.png").also { n++ }
            created = file
            FileOutputStream(file).use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) { "compress failed" }
            }

            val manifest = File(dir, MANIFEST)
            if (!manifest.exists()) {
                // BOM so Excel opens the Hangul correctly.
                manifest.writeText("﻿파일,정답,앱인식,시각\r\n", Charsets.UTF_8)
            }
            manifest.appendText(
                "${file.name},$correctPlate,$appRead,${System.currentTimeMillis()}\r\n",
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
