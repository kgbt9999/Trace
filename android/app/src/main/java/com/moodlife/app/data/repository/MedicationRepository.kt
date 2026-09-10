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
            medicationLogDao.upsert(
                MedicationLogEntity(
                    id = UUID.randomUUID().toString(),
                    medicationId = medicationId,
                    moodEntryId = null,
                    date = date,
                    taken = false,
                    slotsTaken = "{}",
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
    }

    suspend fun ensureAllRegularWeekAhead() {
        medicationDao.observeActive().first().filter { it.isRegular }.forEach { ensureWeekAhead(it.id) }
    }

    suspend fun updateMedication(med: MedicationEntity) {
        val previous = medicationDao.getById(med.id)
        medicationDao.upsert(med.copy(updatedAt = System.currentTimeMillis()))
        // Changing-scheme: do not wipe past logs. Continuous: refill forward slots.
        if (med.isActive && med.isRegular) {
            ensureWeekAhead(med.id)
        }
        // If switched off regular, leave existing logs as historical marks.
        previous // keep for clarity / future hooks
    }

    suspend fun deactivate(med: MedicationEntity) {
        medicationDao.upsert(med.copy(isActive = false, updatedAt = System.currentTimeMillis()))
    }

    suspend fun toggleSlot(
        medicationId: String,
        date: String,
        slotKey: String,
        moodEntryId: String?,
    ) {
        val med = medicationDao.getById(medicationId) ?: return
        val scheduled = MedsUtils.parseIntakeTimes(med.intakeTimes)
        val existing = medicationLogDao.getByMedAndDate(medicationId, date)
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
            dosageOverride = existing?.dosageOverride,
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
        val existing = medicationLogDao.getByMedAndDate(medicationId, date)
        val now = System.currentTimeMillis()
        val override = dosage?.trim()?.takeIf { it.isNotEmpty() }
        medicationLogDao.upsert(
            MedicationLogEntity(
                id = existing?.id ?: UUID.randomUUID().toString(),
                medicationId = medicationId,
                moodEntryId = existing?.moodEntryId,
                date = date,
                taken = existing?.taken == true,
                slotsTaken = existing?.slotsTaken ?: "{}",
                dosageOverride = override,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            ),
        )
    }

    /** Effective dosage shown for a day: log override, else catalog. */
    fun effectiveDosage(med: MedicationEntity, log: MedicationLogEntity?): String? =
        log?.dosageOverride?.takeIf { it.isNotBlank() } ?: med.dosage
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
