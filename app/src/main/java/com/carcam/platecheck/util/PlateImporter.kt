package com.carcam.platecheck.util

import android.content.Context
import android.net.Uri
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedReader
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Reads a resident-vehicle list out of a spreadsheet the operator already keeps.
 *
 * Supports .csv/.txt and .xlsx. An .xlsx file is a ZIP of XML parts, so it is parsed directly
 * rather than pulling in Apache POI, which would add tens of megabytes and a method-count
 * problem to an app whose only use for it is reading two columns.
 *
 * Column convention: first column = plate number, second (optional) = note. A header row is
 * skipped when its first cell does not look like a plate.
 */
object PlateImporter {

    data class Row(val plate: String, val note: String)

    data class Result(
        val rows: List<Row>,
        val skipped: Int,          // lines that held no usable plate
        val error: String? = null
    )

    fun read(context: Context, uri: Uri): Result {
        val name = displayName(context, uri).lowercase()
        return try {
            context.contentResolver.openInputStream(uri).use { input ->
                if (input == null) return Result(emptyList(), 0, "파일을 열 수 없습니다")
                val parsed = if (name.endsWith(".xlsx")) readXlsx(input) else readDelimited(input)
                normalize(parsed)
            }
        } catch (t: Throwable) {
            Result(emptyList(), 0, t.message ?: "파일을 읽지 못했습니다")
        }
    }

    private fun displayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) return c.getString(idx) ?: ""
        }
        return uri.lastPathSegment ?: ""
    }

    /** Keep rows whose first cell parses as a plate; drop headers, blank lines and totals. */
    private fun normalize(raw: List<List<String>>): Result {
        val rows = mutableListOf<Row>()
        var skipped = 0
        val seen = HashSet<String>()
        for (cells in raw) {
            val plate = cells.getOrNull(0)?.trim().orEmpty().replace(" ", "")
            if (plate.isEmpty()) continue
            if (!KoreanPlateRecognizer.isValidPlate(plate)) { skipped++; continue }
            if (!seen.add(plate)) continue
            rows.add(Row(plate, cells.getOrNull(1)?.trim().orEmpty()))
        }
        return Result(rows, skipped)
    }

    private fun readDelimited(input: InputStream): List<List<String>> {
        val text = input.bufferedReader(Charsets.UTF_8).use(BufferedReader::readText)
        // Excel's "CSV (UTF-8)" export writes a BOM; left in place it becomes part of the
        // first plate and every row of the file fails to match.
        val clean = text.removePrefix("﻿")
        val delim = if (clean.lineSequence().first().count { it == '\t' } >
            clean.lineSequence().first().count { it == ',' }) '\t' else ','
        return clean.lineSequence()
            .filter { it.isNotBlank() }
            .map { splitCsvLine(it, delim) }
            .toList()
    }

    /** Split one CSV line, honouring quoted cells so a note containing the delimiter survives. */
    private fun splitCsvLine(line: String, delim: Char): List<String> {
        val out = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                quoted && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> { cell.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == delim && !quoted -> { out.add(cell.toString()); cell.setLength(0) }
                else -> cell.append(c)
            }
            i++
        }
        out.add(cell.toString())
        return out
    }

    // ---- .xlsx ----------------------------------------------------------------------------
    // Cell values are either inline or an index into a workbook-wide shared string table, so
    // both parts have to be read before the sheet can be interpreted.

    private fun readXlsx(input: InputStream): List<List<String>> {
        var sharedXml: ByteArray? = null
        var sheetXml: ByteArray? = null
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                when {
                    entry.name == "xl/sharedStrings.xml" -> sharedXml = zip.readBytes()
                    // Whichever sheet part comes first is the first worksheet in practice.
                    sheetXml == null && entry.name.startsWith("xl/worksheets/sheet") &&
                        entry.name.endsWith(".xml") -> sheetXml = zip.readBytes()
                }
                zip.closeEntry()
            }
        }
        val sheet = sheetXml ?: return emptyList()
        val shared = sharedXml?.let { parseSharedStrings(it) } ?: emptyList()
        return parseSheet(sheet, shared)
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val out = mutableListOf<String>()
        val parser = Xml.newPullParser()
        parser.setInput(bytes.inputStream(), null)
        val sb = StringBuilder()
        var inItem = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "si" -> { inItem = true; sb.setLength(0) }
                }
                XmlPullParser.TEXT -> if (inItem) sb.append(parser.text)
                XmlPullParser.END_TAG -> if (parser.name == "si") {
                    out.add(sb.toString()); inItem = false
                }
            }
            event = parser.next()
        }
        return out
    }

    private fun parseSheet(bytes: ByteArray, shared: List<String>): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val parser = Xml.newPullParser()
        parser.setInput(bytes.inputStream(), null)
        var row: MutableList<String>? = null
        var cellType: String? = null
        var cellCol = 0
        var inValue = false
        val value = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> row = mutableListOf()
                    "c" -> {
                        cellType = parser.getAttributeValue(null, "t")
                        cellCol = columnIndex(parser.getAttributeValue(null, "r"))
                        value.setLength(0)
                    }
                    "v", "t" -> inValue = true
                }
                XmlPullParser.TEXT -> if (inValue) value.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "v", "t" -> inValue = false
                    "c" -> row?.let { r ->
                        val text = if (cellType == "s") {
                            shared.getOrNull(value.toString().trim().toIntOrNull() ?: -1).orEmpty()
                        } else value.toString()
                        // Empty cells are omitted from the XML, so pad to the cell's real
                        // column or the note would slide into the plate position.
                        while (r.size < cellCol) r.add("")
                        r.add(text)
                    }
                    "row" -> { row?.let { rows.add(it) }; row = null }
                }
            }
            event = parser.next()
        }
        return rows
    }

    /** "B7" -> 1. Zero-based column from an A1-style cell reference. */
    private fun columnIndex(ref: String?): Int {
        if (ref.isNullOrEmpty()) return 0
        var n = 0
        for (ch in ref) {
            if (!ch.isLetter()) break
            n = n * 26 + (ch.uppercaseChar() - 'A' + 1)
        }
        return (n - 1).coerceAtLeast(0)
    }
}
