package com.example.library.data

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler

data class SheetTable(val headers: List<String>, val rows: List<List<String>>)

class UnsupportedFileException(message: String) : Exception(message)

object ExcelReader {
    fun read(input: InputStream, fileName: String?): SheetTable {
        val bytes = input.readBytes()
        val isZip = bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
        val isOldExcel = bytes.size > 4 && bytes[0] == 0xD0.toByte() && bytes[1] == 0xCF.toByte()
        return when {
            isZip -> readXlsx(bytes)
            isOldExcel -> throw UnsupportedFileException(
                "This is an old .xls file. Open it in Excel and use Save As > .xlsx, then import that."
            )
            fileName?.lowercase()?.endsWith(".numbers") == true ->
                throw UnsupportedFileException("Export the file as .xlsx or .csv first.")
            else -> readCsv(decodeText(bytes))
        }
    }

    fun readXlsx(bytes: ByteArray): SheetTable {
        val files = HashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name.trimStart('/')
                if (name.endsWith(".xml") || name.endsWith(".rels")) files[name] = zip.readBytes()
            }
        }
        if (files.keys.none { it.startsWith("xl/") }) {
            throw UnsupportedFileException("This file doesn't look like an Excel workbook.")
        }
        val shared = files["xl/sharedStrings.xml"]?.let { parseSharedStrings(it) } ?: emptyList()
        val sheetPath = firstSheetPath(files)
            ?: files.keys.filter { it.startsWith("xl/worksheets/") && it.endsWith(".xml") }.minOrNull()
            ?: throw UnsupportedFileException("No sheet found in this workbook.")
        val sheet = files[sheetPath] ?: throw UnsupportedFileException("No sheet found in this workbook.")
        return toTable(parseSheet(sheet, shared))
    }

    private fun firstSheetPath(files: Map<String, ByteArray>): String? {
        val workbook = files["xl/workbook.xml"] ?: return null
        val rels = files["xl/_rels/workbook.xml.rels"] ?: return null
        var firstRid: String? = null
        parse(workbook, object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String?, qName: String, a: Attributes) {
                if (firstRid == null && local(qName) == "sheet") {
                    firstRid = (0 until a.length).firstOrNull { local(a.getQName(it)) == "id" }?.let { a.getValue(it) }
                }
            }
        })
        val rid = firstRid ?: return null
        var target: String? = null
        parse(rels, object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String?, qName: String, a: Attributes) {
                if (local(qName) == "Relationship" && a.getValue("Id") == rid) target = a.getValue("Target")
            }
        })
        val t = target ?: return null
        return if (t.startsWith("/")) t.trimStart('/') else "xl/" + t.removePrefix("./")
    }

    private fun parseSharedStrings(xml: ByteArray): List<String> {
        val out = ArrayList<String>()
        parse(xml, object : DefaultHandler() {
            val sb = StringBuilder()
            var inSi = false
            var inT = false
            var inPhonetic = false
            override fun startElement(uri: String?, localName: String?, qName: String, a: Attributes) {
                when (local(qName)) {
                    "si" -> { inSi = true; sb.setLength(0) }
                    "t" -> inT = true
                    "rPh" -> inPhonetic = true
                }
            }
            override fun endElement(uri: String?, localName: String?, qName: String) {
                when (local(qName)) {
                    "si" -> { inSi = false; out.add(sb.toString()) }
                    "t" -> inT = false
                    "rPh" -> inPhonetic = false
                }
            }
            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (inSi && inT && !inPhonetic) sb.appendRange(ch, start, start + length)
            }
        })
        return out
    }

    private fun parseSheet(xml: ByteArray, shared: List<String>): List<Map<Int, String>> {
        val rows = ArrayList<Map<Int, String>>()
        parse(xml, object : DefaultHandler() {
            var row: MutableMap<Int, String>? = null
            var col = -1
            var nextCol = 0
            var type: String? = null
            var inValue = false
            val sb = StringBuilder()

            override fun startElement(uri: String?, localName: String?, qName: String, a: Attributes) {
                when (local(qName)) {
                    "row" -> { row = HashMap(); nextCol = 0 }
                    "c" -> {
                        val ref = a.getValue("r")
                        col = if (ref != null) columnIndex(ref) else nextCol
                        nextCol = col + 1
                        type = a.getValue("t")
                        sb.setLength(0)
                    }
                    "v" -> inValue = true
                    "t" -> if (type == "inlineStr") inValue = true
                }
            }

            override fun endElement(uri: String?, localName: String?, qName: String) {
                when (local(qName)) {
                    "v", "t" -> inValue = false
                    "c" -> {
                        val raw = sb.toString()
                        val text = when (type) {
                            "s" -> raw.trim().toIntOrNull()?.let { shared.getOrNull(it) } ?: ""
                            "b" -> if (raw.trim() == "1") "TRUE" else "FALSE"
                            "inlineStr", "str", "e" -> raw
                            else -> formatNumber(raw)
                        }
                        if (text.isNotBlank() && col >= 0) row?.put(col, text.trim())
                    }
                    "row" -> { row?.let { rows.add(it) }; row = null }
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (inValue) sb.appendRange(ch, start, start + length)
            }
        })
        return rows
    }

    internal fun formatNumber(raw: String): String {
        val s = raw.trim()
        val d = s.toDoubleOrNull() ?: return s
        if (d == Math.floor(d) && !d.isInfinite() && Math.abs(d) < 1e15) return d.toLong().toString()
        return s
    }

    internal fun columnIndex(cellRef: String): Int {
        var n = 0
        for (ch in cellRef) {
            if (ch in 'A'..'Z') n = n * 26 + (ch - 'A' + 1)
            else if (ch in 'a'..'z') n = n * 26 + (ch - 'a' + 1)
            else break
        }
        return n - 1
    }

    private fun toTable(sparse: List<Map<Int, String>>): SheetTable {
        val nonEmpty = sparse.filter { it.isNotEmpty() }
        if (nonEmpty.isEmpty()) return SheetTable(emptyList(), emptyList())
        val headerMap = nonEmpty.first()
        val width = nonEmpty.maxOf { (it.keys.maxOrNull() ?: -1) + 1 }
        val headers = (0 until width).map { headerMap[it] ?: "" }
        val rows = nonEmpty.drop(1).map { r -> (0 until width).map { r[it] ?: "" } }
        return SheetTable(headers, rows)
    }

    private fun decodeText(bytes: ByteArray): String {
        val utf8 = Charsets.UTF_8.newDecoder()
        return try {
            utf8.decode(java.nio.ByteBuffer.wrap(bytes)).toString().removePrefix("﻿")
        } catch (e: java.nio.charset.CharacterCodingException) {
            String(bytes, charset("windows-1250"))
        }
    }

    fun readCsv(text: String): SheetTable {
        val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() } ?: return SheetTable(emptyList(), emptyList())
        val delimiter = listOf(';', ',', '\t').maxByOrNull { d -> firstLine.count { it == d } } ?: ','
        val records = ArrayList<List<String>>()
        var field = StringBuilder()
        var record = ArrayList<String>()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { field.append('"'); i++ } else inQuotes = false
                } else field.append(c)
            } else when (c) {
                '"' -> inQuotes = true
                delimiter -> { record.add(field.toString()); field = StringBuilder() }
                '\r' -> {}
                '\n' -> { record.add(field.toString()); records.add(record); record = ArrayList(); field = StringBuilder() }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || record.isNotEmpty()) { record.add(field.toString()); records.add(record) }
        val clean = records.map { r -> r.map { it.trim() } }.filter { r -> r.any { it.isNotEmpty() } }
        if (clean.isEmpty()) return SheetTable(emptyList(), emptyList())
        val width = clean.maxOf { it.size }
        val padded = clean.map { r -> r + List(width - r.size) { "" } }
        return SheetTable(padded.first(), padded.drop(1))
    }

    private fun parse(xml: ByteArray, handler: DefaultHandler) {
        val factory = SAXParserFactory.newInstance()
        factory.isNamespaceAware = false
        factory.newSAXParser().parse(ByteArrayInputStream(xml), handler)
    }

    private fun local(qName: String): String = qName.substringAfter(':')
}
