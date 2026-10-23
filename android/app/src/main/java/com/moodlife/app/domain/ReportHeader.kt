package com.moodlife.app.domain

import com.moodlife.app.data.repository.SettingsRepository

/**
 * The «шапка» of exported reports: who the report is about and for whom. Filled by the user;
 * the app never guesses a diagnosis. Empty fields are simply left out of the report.
 */
data class ReportHeader(
    val title: String = "",
    val patient: String = "",
    val birthDate: String = "",
    val diagnosis: String = "",
    val doctor: String = "",
    val clinic: String = "",
    val nextVisit: String = "",
    val comment: String = "",
) {
    /** Title shown at the top of the report. */
    val effectiveTitle: String get() = title.trim().ifBlank { DEFAULT_TITLE }

    companion object {
        const val DEFAULT_TITLE = "Отчёт о самочувствии для врача"

        private val KEYS = listOf(
            SettingsRepository.KEY_REPORT_TITLE,
            SettingsRepository.KEY_REPORT_PATIENT,
            SettingsRepository.KEY_REPORT_DOB,
            SettingsRepository.KEY_REPORT_DIAGNOSIS,
            SettingsRepository.KEY_REPORT_DOCTOR,
            SettingsRepository.KEY_REPORT_CLINIC,
            SettingsRepository.KEY_REPORT_NEXT_VISIT,
            SettingsRepository.KEY_REPORT_COMMENT,
        )

        /** The doctor falls back to the one in the crisis plan, so it is not typed twice. */
        suspend fun load(settings: SettingsRepository): ReportHeader {
            val v = KEYS.map { settings.get(it)?.trim().orEmpty() }
            return ReportHeader(
                title = v[0],
                patient = v[1],
                birthDate = v[2],
                diagnosis = v[3],
                doctor = v[4].ifBlank { settings.get(SettingsRepository.KEY_CRISIS_DOCTOR)?.trim().orEmpty() },
                clinic = v[5],
                nextVisit = v[6],
                comment = v[7],
            )
        }

        suspend fun save(settings: SettingsRepository, h: ReportHeader) {
            listOf(h.title, h.patient, h.birthDate, h.diagnosis, h.doctor, h.clinic, h.nextVisit, h.comment)
                .zip(KEYS)
                .forEach { (value, key) -> settings.set(key, value.trim()) }
        }
    }
}
