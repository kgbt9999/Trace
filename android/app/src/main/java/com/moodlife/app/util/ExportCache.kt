package com.moodlife.app.util

import android.content.Context
import java.io.File

/** App-private cache subdirectory exposed via FileProvider (`file_paths.xml`). */
object ExportCache {
    fun dir(context: Context): File {
        val dir = File(context.cacheDir, "exports")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
}
