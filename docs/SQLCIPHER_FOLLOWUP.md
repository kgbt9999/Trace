# SQLCipher follow-up (Trace Android)

**Status:** not enabled in this release. Room still uses plain SQLite (`moodlife.db`).

**Done now:** Yandex Weather API key (and similar secrets) live in `EncryptedSharedPreferences` with Android Keystore `MasterKey`, with one-time migration from Room `settings`.

## Why not SQLCipher in one pass

- No existing SQLCipher / `SupportFactory` wiring in the project.
- Opening an already-created unencrypted Room DB with SQLCipher requires an explicit re-key / export-reimport path.
- A naive switch would risk wiping or failing to open user diaries — **never acceptable**.

## Safe follow-up plan (no wipe)

1. Add `net.zetetic:android-database-sqlcipher` (or `androidx.sqlite` SQLCipher build) + Room `SupportFactory`.
2. Store a random passphrase in Android Keystore (or EncryptedSharedPreferences).
3. On first launch after upgrade:
   - Detect unencrypted `moodlife.db`.
   - Export via existing `JsonBackupExporter` (or ATTACH/sqlcipher_export).
   - Create encrypted DB, import, verify row counts.
   - Only then rename/replace files; keep the old DB until verification succeeds.
4. Instrument rollback: if import fails, keep the unencrypted DB and surface a user-visible error.
5. Document restore from `Documents/Trace/backups/` as emergency recovery.

## Non-goals

- Do not use `fallbackToDestructiveMigration()` for this change.
- Do not delete local mood/medication data because encryption migration failed.
