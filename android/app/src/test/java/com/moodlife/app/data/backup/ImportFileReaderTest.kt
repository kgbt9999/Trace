package com.moodlife.app.data.backup

import com.moodlife.app.domain.FloParse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ImportFileReaderTest {

    private val floJson = """{"operationalData":{"point_events_manual_v2":[{"date":"2026-09-01","category":"Period","subcategory":"Medium"}]}}"""

    @Test
    fun `plain json with BOM is read as text`() {
        val files = ImportFileReader.read(("﻿" + floJson).byteInputStream())
        assertEquals(1, files.size)
        assertTrue(files.single().text.startsWith("{"))
    }

    @Test
    fun `zip export is unpacked and only json or csv entries are returned`() {
        val bytes = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("flo/data.json")); zip.write(floJson.toByteArray()); zip.closeEntry()
                zip.putNextEntry(ZipEntry("readme.txt")); zip.write("hi".toByteArray()); zip.closeEntry()
            }
        }.toByteArray()
        val files = ImportFileReader.read(bytes.inputStream())
        assertEquals(listOf("flo/data.json"), files.map { it.name })
        assertTrue(FloParse.parseJson(files.single().text).errors.none { it.contains("Неверный JSON") })
    }
}
