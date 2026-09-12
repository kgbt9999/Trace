package com.moodlife.app.data.secure

import android.content.Context
import net.sqlcipher.database.SQLiteDatabase
import java.io.File

/**
 * One-shot migration of an existing plaintext Room DB to SQLCipher.
 * Keeps `moodlife.db.bak` until the encrypted file is verified.
 * Does not log passphrase or health data.
 */
object DatabaseEncryptionMigrator {

    const val DB_NAME = "moodlife.db"

    /**
     * If a plaintext [DB_NAME] exists, encrypt it in place (via temp file + rename).
     * No-op when the file is missing (fresh install) or already encrypted.
     */
    fun migrateIfNeeded(context: Context, passphrase: CharArray) {
        val dbFile = context.getDatabasePath(DB_NAME)
        if (!dbFile.exists() || dbFile.length() == 0L) return
        if (!isPlaintextSqlite(dbFile)) return

        val bakFile = File(dbFile.parentFile, "$DB_NAME.bak")
        val tempFile = File(dbFile.parentFile, "$DB_NAME.encrypted.tmp")

        // Discard stale temp from a previous interrupted attempt.
        if (tempFile.exists()) tempFile.delete()

        // Keep a recoverable copy of plaintext until encryption succeeds.
        dbFile.copyTo(bakFile, overwrite = true)

        deleteSidecars(tempFile)
        encryptPlaintextTo(dbFile, tempFile, passphrase)

        if (!tempFile.exists() || tempFile.length() == 0L) {
            tempFile.delete()
            deleteSidecars(tempFile)
            error("SQLCipher export produced an empty database")
        }

        // Swap: remove plaintext (+ WAL/SHM), move encrypted into place.
        deleteSidecars(dbFile)
        if (!dbFile.delete()) {
            tempFile.delete()
            deleteSidecars(tempFile)
            error("Could not replace plaintext database")
        }
        if (!tempFile.renameTo(dbFile)) {
            // Restore from bak so the user keeps data.
            bakFile.copyTo(dbFile, overwrite = true)
            tempFile.delete()
            error("Could not rename encrypted database into place")
        }
        deleteSidecars(tempFile)

        verifyEncrypted(dbFile, passphrase)
        bakFile.delete()
        deleteSidecars(bakFile)
    }

    private fun encryptPlaintextTo(plaintext: File, encryptedOut: File, passphrase: CharArray) {
        // Open plaintext with empty key (SQLCipher opens standard SQLite this way).
        val db = SQLiteDatabase.openDatabase(
            plaintext.absolutePath,
            "",
            null,
            SQLiteDatabase.OPEN_READWRITE,
        )
        try {
            val outPath = encryptedOut.absolutePath.replace("'", "''")
            // Passphrase is hex-only from DatabasePassphraseStore — safe in SQL literal.
            val keyLiteral = String(passphrase).replace("'", "''")
            db.rawExecSQL("ATTACH DATABASE '$outPath' AS encrypted KEY '$keyLiteral';")
            db.rawExecSQL("SELECT sqlcipher_export('encrypted');")
            db.rawExecSQL("DETACH DATABASE encrypted;")
        } finally {
            db.close()
        }
    }

    private fun verifyEncrypted(dbFile: File, passphrase: CharArray) {
        val db = SQLiteDatabase.openDatabase(
            dbFile.absolutePath,
            passphrase,
            null,
            SQLiteDatabase.OPEN_READONLY,
        )
        try {
            db.rawQuery("SELECT count(*) FROM sqlite_master", null).use { cursor ->
                if (!cursor.moveToFirst()) error("Encrypted database verification failed")
            }
        } finally {
            db.close()
        }
    }

    private fun isPlaintextSqlite(file: File): Boolean {
        return try {
            // Standard Android SQLite cannot open SQLCipher files.
            android.database.sqlite.SQLiteDatabase.openDatabase(
                file.absolutePath,
                null,
                android.database.sqlite.SQLiteDatabase.OPEN_READONLY,
            ).use { db ->
                db.rawQuery("SELECT count(*) FROM sqlite_master", null).use { it.moveToFirst() }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun deleteSidecars(dbFile: File) {
        File(dbFile.path + "-wal").delete()
        File(dbFile.path + "-shm").delete()
        File(dbFile.path + "-journal").delete()
    }
}
