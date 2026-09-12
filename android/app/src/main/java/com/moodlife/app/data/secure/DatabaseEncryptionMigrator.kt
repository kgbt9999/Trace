package com.moodlife.app.data.secure

import android.content.Context
import android.util.Log
import net.sqlcipher.database.SQLiteDatabase
import java.io.File

/**
 * One-shot migration of an existing plaintext Room DB to SQLCipher.
 * Keeps `moodlife.db.bak` until the encrypted file is verified.
 * Does not log passphrase or health data.
 *
 * Failures never throw to the caller — launch must remain possible via plaintext fallback.
 */
object DatabaseEncryptionMigrator {

    const val DB_NAME = "moodlife.db"

    private const val TAG = "DbEncryption"
    private const val STATE_PREFS = "db_encryption_state"
    private const val KEY_SKIP_MIGRATION = "skip_plaintext_migration"

    /**
     * @return true if [DB_NAME] is encrypted (or missing — Room+SQLCipher will create it),
     *         false if plaintext must be used (migration failed / restored).
     */
    fun migrateIfNeeded(context: Context, passphrase: CharArray): Boolean {
        val dbFile = context.getDatabasePath(DB_NAME)
        dbFile.parentFile?.mkdirs()

        if (!dbFile.exists() || dbFile.length() == 0L) {
            // Fresh install: Room will create an encrypted DB via SupportFactory.
            return true
        }
        if (!isPlaintextSqlite(dbFile)) {
            return true
        }

        val state = context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
        if (state.getBoolean(KEY_SKIP_MIGRATION, false)) {
            Log.i(TAG, "Skipping plaintext→SQLCipher migration (previous failure)")
            return false
        }

        val bakFile = File(dbFile.parentFile, "$DB_NAME.bak")
        val tempFile = File(dbFile.parentFile, "moodlife_enc_tmp.db")

        return try {
            cleanupTemp(tempFile)

            // Room defaults to WAL; SQLCipher export needs a single main file.
            preparePlaintextForExport(dbFile)

            dbFile.copyTo(bakFile, overwrite = true)

            encryptPlaintextTo(dbFile, tempFile, passphrase)

            if (!tempFile.exists() || tempFile.length() == 0L) {
                cleanupTemp(tempFile)
                Log.e(TAG, "SQLCipher export produced an empty database")
                markSkip(state)
                return false
            }

            deleteSidecars(dbFile)
            if (!dbFile.delete()) {
                cleanupTemp(tempFile)
                Log.e(TAG, "Could not replace plaintext database")
                markSkip(state)
                return false
            }
            if (!tempFile.renameTo(dbFile)) {
                val copied = try {
                    tempFile.copyTo(dbFile, overwrite = true)
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "Could not move encrypted database into place", e)
                    false
                }
                cleanupTemp(tempFile)
                if (!copied) {
                    restoreFromBak(bakFile, dbFile)
                    markSkip(state)
                    return false
                }
            }
            deleteSidecars(tempFile)

            try {
                verifyEncrypted(dbFile, passphrase)
            } catch (e: Exception) {
                Log.e(TAG, "Encrypted database verification failed", e)
                restoreFromBak(bakFile, dbFile)
                markSkip(state)
                return false
            }

            bakFile.delete()
            deleteSidecars(bakFile)
            state.edit().remove(KEY_SKIP_MIGRATION).apply()
            true
        } catch (e: Exception) {
            Log.e(TAG, "SQLCipher migration failed; keeping plaintext", e)
            cleanupTemp(tempFile)
            if (!dbFile.exists() || dbFile.length() == 0L) {
                restoreFromBak(bakFile, dbFile)
            }
            if (dbFile.exists() && !isPlaintextSqlite(dbFile) && bakFile.exists()) {
                restoreFromBak(bakFile, dbFile)
            }
            markSkip(state)
            false
        }
    }

    /** Restore plaintext from `.bak` after a failed encrypted open. */
    fun restorePlaintextBackup(context: Context): Boolean {
        val dbFile = context.getDatabasePath(DB_NAME)
        val bakFile = File(dbFile.parentFile, "$DB_NAME.bak")
        if (!bakFile.exists()) return false
        return try {
            restoreFromBak(bakFile, dbFile)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore plaintext backup", e)
            false
        }
    }

    fun isPlaintextDatabase(context: Context): Boolean {
        val dbFile = context.getDatabasePath(DB_NAME)
        if (!dbFile.exists() || dbFile.length() == 0L) return false
        return isPlaintextSqlite(dbFile)
    }

    private fun markSkip(state: android.content.SharedPreferences) {
        state.edit().putBoolean(KEY_SKIP_MIGRATION, true).apply()
    }

    private fun preparePlaintextForExport(dbFile: File) {
        android.database.sqlite.SQLiteDatabase.openDatabase(
            dbFile.absolutePath,
            null,
            android.database.sqlite.SQLiteDatabase.OPEN_READWRITE,
        ).use { db ->
            // PRAGMA that return rows must use rawQuery on Android framework SQLite.
            db.rawQuery("PRAGMA wal_checkpoint(FULL)", null)?.use { while (it.moveToNext()) { /* drain */ } }
            db.rawQuery("PRAGMA journal_mode=DELETE", null)?.use { while (it.moveToNext()) { /* drain */ } }
        }
        deleteSidecars(dbFile)
    }

    /**
     * Create an encrypted DB, then pull plaintext into it via sqlcipher_export.
     * Reverse of ATTACH-encrypted-from-plaintext — more reliable on SQLCipher 4.x Android.
     */
    private fun encryptPlaintextTo(plaintext: File, encryptedOut: File, passphrase: CharArray) {
        encryptedOut.parentFile?.mkdirs()
        if (encryptedOut.exists()) encryptedOut.delete()
        deleteSidecars(encryptedOut)

        val enc = SQLiteDatabase.openOrCreateDatabase(encryptedOut.absolutePath, passphrase, null)
        try {
            val inPath = plaintext.absolutePath.replace("'", "''")
            enc.rawExecSQL("ATTACH DATABASE '$inPath' AS plaintext KEY '';")
            enc.rawExecSQL("SELECT sqlcipher_export('main', 'plaintext');")
            enc.rawExecSQL("DETACH DATABASE plaintext;")
        } finally {
            enc.close()
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

    private fun restoreFromBak(bakFile: File, dbFile: File) {
        if (!bakFile.exists()) return
        deleteSidecars(dbFile)
        if (dbFile.exists()) dbFile.delete()
        bakFile.copyTo(dbFile, overwrite = true)
        deleteSidecars(dbFile)
    }

    private fun cleanupTemp(tempFile: File) {
        tempFile.delete()
        deleteSidecars(tempFile)
    }

    private fun deleteSidecars(dbFile: File) {
        File(dbFile.path + "-wal").delete()
        File(dbFile.path + "-shm").delete()
        File(dbFile.path + "-journal").delete()
    }
}
