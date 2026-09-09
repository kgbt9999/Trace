# MoodLife — Feature Parity Checklist (Web → Android)

Use this checklist when porting from `web-reference/`. Mark `[x]` when implemented and tested on device.

## Global / Shell

- [ ] Russian UI (`strings.xml` complete)
- [ ] Disclaimer in app shell (not medical advice)
- [ ] 6 bottom tabs: Сегодня, Календарь, Отчёты, Прогноз, Настройки, Источники
- [ ] Dark theme + appearance_style (minimal / neutral / colorful)
- [x] Crisis plan chip on worsening (all tabs, above nav)
- [ ] Offline-first — all core data in Room, no network required
- [x] Timezone-safe dates (`DateUtils.todayIso()`)

## Сегодня (Today)

### Mood entry (daily)
- [ ] Date navigation (today / pick date)
- [x] 4 core axes 0–5: Подавленность, Подъём, Тревога, Раздражение
- [x] Qualitative axes: Силы, Внимание, Аппетит, Общение (0–3)
- [x] Clinical axes: Сон ночью (quality 0–3); часы сна, легли/встали — ещё нет
- [x] Функционирование 0–10, Безопасность 0–3, Режим 0–10
- [x] Алкоголь 0–5, ПАВ 0–5 (Russian anchors)
- [x] Auto episode phase classification
- [x] Save entry (delete — ещё нет)
- [x] Last saved timestamp
- [x] Anchor labels (наречия); подъём ≠ хорошо hint
- [ ] Source citations on scales (→ Источники)

### Medications
- [ ] List active meds with dosage
- [ ] Intake time slots (утро/день/вечер/ночь/HH:MM / по схеме)
- [ ] Per-slot checkboxes for today
- [ ] Add new medication inline
- [ ] isRegular, doseVaries, doseNote

### Symptoms & factors
- [ ] Custom symptoms with scale types (0-5, 0-10, yesno, qual4-i, qual4-lmh)
- [ ] Symptom logs for today
- [x] Trigger/factor logs
- [x] Early warning signs (prodromes) with intensity
- [ ] Basic symptom seed button (merge, no overwrite)

### Check-ins (eMoods-style)
- [ ] Morning / afternoon / evening mini check-in
- [ ] Timeline in day detail sheet

### Day notes
- [ ] Multiple timestamped notes per day
- [ ] Add note immediately (no separate save button)

### Weather
- [ ] Auto-fetch weather (Open-Meteo → Yandex fallback)
- [ ] Weather card with metrics and deviation highlights
- [ ] Carry yesterday → today, today → tomorrow

### External health cards
- [ ] Sleep / activity / nutrition cards from ExternalHealthDay
- [ ] Merge sleep into MoodEntry if empty (policy match web)

### UX
- [x] Onboarding card «С чего начать» (dismissible)
- [ ] No menstruation block on Today (calendar only)

## Календарь (Calendar)

- [ ] Month grid with mood color/intensity
- [ ] Cycle phase overlay (fertile, ovulation, PMS, menstruation)
- [ ] Weather icons on days
- [ ] Moon phase indicator (8 steps, no clinical claims)
- [ ] Flo symptom markers on days
- [ ] External health dots (sleep/activity/cycle)
- [ ] Tap day → bottom sheet summary + «Открыть в Сегодня»
- [ ] Cycle settings panel (length, period length, irregular, lastPeriodStart)
- [ ] Auto-open cycle panel if no lastPeriodStart

## Отчёты (Reports)

- [ ] Month selector
- [ ] Line charts for 0–5 axes
- [ ] Radar chart profile (Силы, Внимание, Общение, …)
- [ ] Weather correlation section
- [ ] Alcohol / substance columns with text labels
- [ ] External data section
- [ ] Insights panel «Ваши паттерны» (≥14 days, exploratory disclaimer)
- [ ] Export JSON
- [ ] Export for doctor (HTML print layout)

## Прогноз (Forecast)

- [ ] 7-day state estimate 0–10
- [ ] Cycle phase per day with emoji + description
- [ ] Weather risk flags (pressure, UV, precipitation, temp, wind)
- [ ] P(подъём/спад/смешанное) from prodromes + cycle
- [ ] Recommendations by phase (clinical text unchanged from web)
- [ ] «Мало данных» when history < 8 days
- [ ] Loading / error states
- [ ] Do NOT change forecast formula without user request

## Настройки (Settings)

### Sections
- [ ] Симптомы (CRUD, scale type, sort, hide/restore)
- [x] Триггеры / факторы
- [x] Продромы (early warning signs)
- [ ] Погода (location, Yandex API key)
- [x] Flo sync (JSON import)
- [ ] Google Drive backup
- [ ] Данные (export/import JSON, seed, clear)
- [ ] Кризисный план (doctor, support, notes)
- [x] crisis_plan_on_worsening toggle
- [ ] Оформление (minimal/neutral/colorful)
- [ ] Интеграции / Android sync hub equivalent

### Integrations (native advantage)
- [ ] Health Connect live read (replace file export)
- [x] Flo JSON import
- [ ] Optional sync to web via Personal Sync API
- [ ] Google Fit via Health Connect aggregation

## Источники (Sources)

- [ ] Citation library tab
- [ ] SourceCitation popovers linked from scales
- [ ] Clinical disclaimers

## Data layer (Room)

- [x] MoodEntry
- [x] MoodCheckIn
- [x] DayNote
- [x] Medication + MedicationLog
- [x] Symptom + SymptomLog
- [x] Factor + FactorLog
- [x] EarlyWarningSign + WarningTrigger
- [x] PeriodLog + PeriodSetting
- [x] FloLog
- [x] WeatherDay
- [x] ExternalHealthDay
- [x] Setting (key-value)

## Background & backup

- [ ] WorkManager Health Connect sync
- [ ] Google Drive appDataFolder backup/restore
- [ ] Export/import JSON file (SAF picker)

## Testing

- [x] Unit tests: DateUtils, repositories, parsers, forecast
- [ ] Room in-memory DAO tests
- [ ] Compose UI: Today save flow, tab navigation
- [x] `./gradlew assembleDebug` passes

## Explicit non-goals (web honesty preserved)

- [ ] Do not claim live OAuth without user credentials
- [ ] Do not diagnose or replace clinician
- [ ] Do not put menstruation on Today tab
