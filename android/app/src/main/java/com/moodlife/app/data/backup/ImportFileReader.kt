package com.moodlife.app.data.backup

import java.io.BufferedInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Reads a picked import file as text. Flo's data export (and many "share" flows) deliver a
 * ZIP with JSON inside; reading that as plain text produced binary garbage and the import
 * silently found nothing. ZIPs are unpacked and their .json / .csv entries returned.
 */
object ImportFileReader {

    data class TextFile(val name: String, val text: String)

    /** Guard against loading something huge by mistake (a photo, a video). */
    private const val MAX_BYTES = 50L * 1024 * 1024

    fun read(stream: InputStream, displayName: String = "file"): List<TextFile> {
        val input = BufferedInputStream(stream)
        input.mark(4)
        val magic = ByteArray(2)
        val n = input.read(magic)
        input.reset()
        val isZip = n == 2 && magic[0] == 'P'.code.toByte() && magic[1] == 'K'.code.toByte()
        if (!isZip) {
            return listOf(TextFile(displayName, readLimited(input).trim().removePrefix("\uFEFF")))
        }
        val out = mutableListOf<TextFile>()
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                val lower = name.lowercase()
                if (!entry.isDirectory && (lower.endsWith(".json") || lower.endsWith(".csv"))) {
                    out += TextFile(name, readLimited(zip).trim().removePrefix("\uFEFF"))
                }
                entry = zip.nextEntry
            }
        }
        return out
    }

    private fun readLimited(stream: InputStream): String {
        val buf = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val r = stream.read(chunk)
            if (r < 0) break
            total += r
            require(total <= MAX_BYTES) { "Файл больше 50 МБ" }
            buf.write(chunk, 0, r)
        }
        return buf.toString(Charsets.UTF_8.name())
    }
}
