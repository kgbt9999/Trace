package com.moodlife.app.data.repository

import com.moodlife.app.data.local.dao.DayNoteDao
import com.moodlife.app.data.local.dao.MedicationDao
import com.moodlife.app.data.local.dao.MedicationLogDao
import com.moodlife.app.data.local.entity.DayNoteEntity
import com.moodlife.app.data.local.entity.MedicationEntity
import com.moodlife.app.data.local.entity.MedicationLogEntity
import com.moodlife.app.util.MedsUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MedicationRepository @Inject constructor(
    private val medicationDao: MedicationDao,
    private val medicationLogDao: MedicationLogDao,
) {
    fun observeActive() = medicationDao.observeActive()

    fun observeAll() = medicationDao.observeAll()

    fun observeVisibleInRange(from: String, to: String) =
        medicationDao.observeVisibleInRange(from, to)

    suspend fun getById(id: String): MedicationEntity? = medicationDao.getById(id)

    fun observeLogsForDate(date: String): Flow<List<MedicationLogEntity>> =
        medicationLogDao.observeForDate(date)

    fun observeLogsRange(from: String, to: String): Flow<List<MedicationLogEntity>> =
        medicationLogDao.observeRange(from, to)

    suspend fun addMedication(name: String, dosage: String? = null): MedicationEntity {
        val now = System.currentTimeMillis()
        val med = MedicationEntity(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            dosage = dosage?.trim()?.takeIf { it.isNotEmpty() },
            intakeTimes = MedsUtils.serializeIntakeTimes(listOf("morning", "evening")),
            createdAt = now,
            updatedAt = now,
        )
        medicationDao.upsert(med)
        return med
    }

    suspend fun addMedicationDetailed(
        name: String,
        dosage: String?,
        intakeTimes: List<String>,
        isRegular: Boolean = true,
    ): MedicationEntity? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        val now = System.currentTimeMillis()
        val med = MedicationEntity(
            id = UUID.randomUUID().toString(),
            name = trimmed,
            dosage = dosage?.trim()?.takeIf { it.isNotEmpty() },
            intakeTimes = MedsUtils.serializeIntakeTimes(intakeTimes),
            isRegular = isRegular,
            createdAt = now,
            updatedAt = now,
        )
        medicationDao.upsert(med)
        if (isRegular) ensureWeekAhead(med.id)
        return med
    }

    /** For continuous meds: create empty day logs for today..+6 so Today is checkboxes only. */
    suspend fun ensureWeekAhead(medicationId: String, days: Int = 7) {
        val med = medicationDao.getById(medicationId) ?: return
        if (!med.isActive || !med.isRegular) return
        val today = com.moodlife.app.util.DateUtils.todayIso()
        val now = System.currentTimeMillis()
        for (i in 0 until days) {
            val date = com.moodlife.app.util.DateUtils.addDays(today, i.toLong())
            if (medicationLogDao.getByMedAndDate(medicationId, date) != null) continue
            medicationLogDao.upsert(newDayLog(med, date, now))
        }
    }

    suspend fun ensureAllRegularWeekAhead() {
        medicationDao.observeActive().first().filter { it.isRegular }.forEach { ensureWeekAhead(it.id) }
    }

    /**
     * Updates catalog defaults. Never rewrites past day logs.
     * Propagates name/times (and optionally dosage) to today + future day snapshots only.
     */
    suspend fun updateMedication(med: MedicationEntity, propagateDosage: Boolean = false) {
        medicationDao.upsert(med.copy(updatedAt = System.currentTimeMillis()))
        propagateCatalogToFuture(med, propagateDosage = propagateDosage)
        if (med.isActive && med.isRegular) {
            ensureWeekAhead(med.id)
        }
    }

    suspend fun deactivate(med: MedicationEntity) {
        medicationDao.upsert(med.copy(isActive = false, updatedAt = System.currentTimeMillis()))
        // Keep historical logs; do not delete rows on hide/rename.
    }

    suspend fun toggleSlot(
        medicationId: String,
        date: String,
        slotKey: String,
        moodEntryId: String?,
    ) {
        val med = medicationDao.getById(medicationId) ?: return
        val existing = medicationLogDao.getByMedAndDate(medicationId, date)
        val scheduled = MedsUtils.parseIntakeTimes(effectiveIntakeTimes(med, existing))
        val now = System.currentTimeMillis()
        val slots = existing?.let { MedsUtils.parseSlotsTaken(it.slotsTaken).toMutableMap() }
            ?: mutableMapOf()
        val currentlyTaken = MedsUtils.isSlotTaken(
            taken = existing?.taken == true,
            slotsTakenJson = existing?.slotsTaken,
            slotId = slotKey,
            scheduled = scheduled,
        )
        slots[slotKey] = !currentlyTaken
        val allTaken = scheduled.all { slots[it] == true }
        val log = MedicationLogEntity(
            id = existing?.id ?: UUID.randomUUID().toString(),
            medicationId = medicationId,
            moodEntryId = moodEntryId ?: existing?.moodEntryId,
            date = date,
            taken = allTaken,
            slotsTaken = MedsUtils.serializeSlotsTaken(slots),
            dosageOverride = existing?.dosageOverride ?: med.dosage,
            nameSnapshot = existing?.nameSnapshot ?: med.name,
            intakeTimesSnapshot = existing?.intakeTimesSnapshot ?: med.intakeTimes,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        medicationLogDao.upsert(log)
    }

    /**
     * Sets dosage for a single calendar day only.
     * Does not change [MedicationEntity.dosage] (catalog default).
     */
    suspend fun updateLogDosage(medicationId: String, date: String, dosage: String?) {
        updateDaySnapshot(
            medicationId = medicationId,
            date = date,
            dosage = dosage,
            clearDosage = dosage.isNullOrBlank(),
        )
    }

    /**
     * Updates per-day journal snapshot (name / dose / intake times) without touching catalog.
     * Creates a log row if missing.
     */
    suspend fun updateDaySnapshot(
        medicationId: String,
        date: String,
        name: String? = null,
        dosage: String? = null,
        clearDosage: Boolean = false,
        intakeTimes: List<String>? = null,
        updateDosage: Boolean = true,
    ) {
        val med = medicationDao.getById(medicationId) ?: return
        val existing = medicationLogDao.getByMedAndDate(medicationId, date)
        val now = System.currentTimeMillis()
        val override = when {
            !updateDosage -> existing?.dosageOverride ?: med.dosage
            clearDosage -> null
            else -> dosage?.trim()?.takeIf { it.isNotEmpty() } ?: existing?.dosageOverride ?: med.dosage
        }
        medicationLogDao.upsert(
            MedicationLogEntity(
                id = existing?.id ?: UUID.randomUUID().toString(),
                medicationId = medicationId,
                moodEntryId = existing?.moodEntryId,
                date = date,
                taken = existing?.taken == true,
                slotsTaken = existing?.slotsTaken ?: "{}",
                dosageOverride = override,
                nameSnapshot = name?.trim()?.takeIf { it.isNotEmpty() }
                    ?: existing?.nameSnapshot
                    ?: med.name,
                intakeTimesSnapshot = intakeTimes?.let { MedsUtils.serializeIntakeTimes(it) }
                    ?: existing?.intakeTimesSnapshot
                    ?: med.intakeTimes,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            ),
        )
    }

    /**
     * Catalog change from a given date: updates defaults and propagates to [fromDate] + future only.
     * Past day logs stay as historical snapshots.
     */
    suspend fun updateSchemeFromDate(
        med: MedicationEntity,
        fromDate: String,
        propagateDosage: Boolean = true,
    ) {
        medicationDao.upsert(med.copy(updatedAt = System.currentTimeMillis()))
        val now = System.currentTimeMillis()
        val future = medicationLogDao.listFromDate(med.id, fromDate)
        for (log in future) {
            medicationLogDao.upsert(
                log.copy(
                    nameSnapshot = med.name,
                    intakeTimesSnapshot = med.intakeTimes,
                    dosageOverride = if (propagateDosage) med.dosage else (log.dosageOverride ?: med.dosage),
                    updatedAt = now,
                ),
            )
        }
        if (med.isActive && med.isRegular) {
            ensureWeekAhead(med.id)
        }
    }

    /**
     * Removes the journal row for one medication on one date.
     * Does not change the catalog medication or other days.
     */
    suspend fun deleteDayLog(medicationId: String, date: String) {
        medicationLogDao.deleteByMedAndDate(medicationId, date)
    }

    fun effectiveName(med: MedicationEntity, log: MedicationLogEntity?): String =
        log?.nameSnapshot?.takeIf { it.isNotBlank() } ?: med.name

    /** Effective dosage shown for a day: log override/snapshot, else catalog. */
    fun effectiveDosage(med: MedicationEntity, log: MedicationLogEntity?): String? =
        log?.dosageOverride?.takeIf { it.isNotBlank() } ?: med.dosage

    fun effectiveIntakeTimes(med: MedicationEntity, log: MedicationLogEntity?): String =
        log?.intakeTimesSnapshot?.takeIf { it.isNotBlank() } ?: med.intakeTimes

    private suspend fun propagateCatalogToFuture(med: MedicationEntity, propagateDosage: Boolean) {
        val today = com.moodlife.app.util.DateUtils.todayIso()
        val now = System.currentTimeMillis()
        val future = medicationLogDao.listFromDate(med.id, today)
        for (log in future) {
            medicationLogDao.upsert(
                log.copy(
                    nameSnapshot = med.name,
                    intakeTimesSnapshot = med.intakeTimes,
                    dosageOverride = if (propagateDosage) {
                        med.dosage
                    } else {
                        log.dosageOverride ?: med.dosage
                    },
                    updatedAt = now,
                ),
            )
        }
    }

    private fun newDayLog(med: MedicationEntity, date: String, now: Long): MedicationLogEntity =
        MedicationLogEntity(
            id = UUID.randomUUID().toString(),
            medicationId = med.id,
            moodEntryId = null,
            date = date,
            taken = false,
            slotsTaken = "{}",
            dosageOverride = med.dosage,
            nameSnapshot = med.name,
            intakeTimesSnapshot = med.intakeTimes,
            createdAt = now,
            updatedAt = now,
        )
}

@Singleton
class DayNoteRepository @Inject constructor(
    private val dayNoteDao: DayNoteDao,
) {
    fun observeForDate(date: String) = dayNoteDao.observeForDate(date)

    suspend fun addNote(date: String, content: String) {
        val text = content.trim()
        if (text.isEmpty()) return
        val now = System.currentTimeMillis()
        dayNoteDao.insert(
            DayNoteEntity(
                id = UUID.randomUUID().toString(),
                date = date,
                content = text,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }
}
