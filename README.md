# Trace

**Дневник состояния для Android** — спокойный трекер настроения, симптомов и повседневных факторов.  
Данные остаются на телефоне. Интерфейс на русском.

<p align="center">
  <img src="android/app/src/main/res/drawable/ic_brand_mark.png" alt="Trace mark" width="96" />
</p>

```text
  offline-first · без диагнозов · под вашим контролем
```

---

## Важно

| Trace делает | Trace не делает |
|---|---|
| Помогает вести дневник состояния | Не ставит диагнозы |
| Показывает ваши паттерны и статистику | Не заменяет врача |
| Даёт техники самопомощи и напоминания | Не советует менять лекарства |
| Хранит записи локально (Room) | Не публикует данные без вашего действия |

При ухудшении состояния или суицидальных мыслях — к специалисту или в экстренную службу.

---

## Что внутри

| Экран | Зачем |
|-------|--------|
| **Сегодня** | Шкалы, чек-ины, симптомы, триггеры, сон, препараты, заметки |
| **Календарь** | Обзор дней, краткая сводка, личный значок на выбранный день |
| **Отчёты** | Графики и экспорт (JSON / HTML / CSV / PDF) |
| **Прогноз** | Сводка по истории + «поделиться» |
| **Упражнения** | Практики самопомощи (можно скрыть в настройках) |
| **Настройки** | Каталоги, шкалы, импорт, напоминания, оформление |

**Стек:** Kotlin · Jetpack Compose · Material 3 · Room · Hilt · WorkManager · Health Connect  
**SDK:** min 30 · target 35 · `applicationId` `com.moodlife.app`

---

## Быстрый старт

### 1. Android Studio

1. Установите [Android Studio](https://developer.android.com/studio).
2. **Файл → Открыть** → папка **`android/`** (не корень репозитория).
3. Gradle Sync.
4. Если ошибка *Incompatible Gradle JVM / JDK 25* →  
   **Настройки → Сборка → Gradle → Gradle JDK → 17 или 21**.
5. ▶ Run на устройстве или эмуляторе.

### 2. Терминал

```powershell
cd android
.\gradlew.bat assembleDebug
.\gradlew.bat test
```

Подписанный APK для друзей: **[docs/BUILD_APK.md](docs/BUILD_APK.md)**.

---

## Структура

```text
Trace/
├── README.md                 ← вы здесь
├── LICENSE
├── .gitignore
├── HOW_TO_PUBLISH_RU.md      ← как выложить репозиторий
├── android/                  ← открывать в Android Studio
│   ├── app/src/main/         ← код и ресурсы
│   ├── gradlew.bat
│   └── keystore.properties.example
├── docs/                     ← архитектура и сборка APK
└── tools/                    ← вспомогательные скрипты
```

---

## Приватность

- Настроение, симптомы, журнал, сон и лекарства — чувствительные данные.
- Локальная база — источник истины; приложение работает без интернета.
- Экспорт и облачный бэкап — только по вашему явному действию.
- В репозиторий **не** входят: `local.properties`, keystore, пароли.

---

## Сборка release

Кратко:

```powershell
cd android
copy keystore.properties.example keystore.properties
# заполните пароли, создайте release.keystore (см. docs/BUILD_APK.md)
.\gradlew.bat assembleRelease --no-parallel --max-workers=1
```

APK: `android/app/build/outputs/apk/release/app-release.apk`

---

## Участие и этика

Исправления и идеи приветствуются через Issues / Pull Requests.  
Любой медицинский текст должен оставаться в рамках **самонаблюдения**, без диагностических формулировок.

---

## Лицензия

См. [LICENSE](LICENSE) — MIT.
