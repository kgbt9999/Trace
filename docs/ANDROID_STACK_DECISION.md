# Android Stack Decision — MoodLife

**Decision date:** 2026-08-31  
**Chosen stack:** Kotlin + Jetpack Compose + Room + Hilt + WorkManager + Health Connect SDK + Google Drive API

## Requirements recap

- Android 11+ (API 30+), all 4 ABIs (armeabi-v7a, arm64-v8a, x86, x86_64)
- Full feature parity with web MoodLife
- Health Connect, Google Fit, Flo integration
- Offline-first with optional cloud backup
- Clean Android Studio builds, testable architecture

## Recommended stack

| Layer | Technology | Rationale |
|-------|------------|-----------|
| Language | **Kotlin 2.x** | First-class Android, coroutines, null safety |
| UI | **Jetpack Compose + Material 3** | Declarative UI, matches modern Android; Russian strings in `strings.xml` |
| Local DB | **Room (SQLite)** | Offline SSOT; mirrors Prisma schema; Flow for reactive UI |
| DI | **Hilt** | Standard Google DI; testable repositories |
| Background | **WorkManager** | Health Connect sync, Drive backup, retry/backoff |
| Health data | **Health Connect SDK** | Official API for steps, sleep, weight, nutrition, menstrual flow |
| Fit legacy | **Health Connect** (preferred) or **Google Fit REST** via OAuth | Fit aggregates into HC on modern devices |
| Period app | **Flo JSON import** + HC menstrual records | Reuse web parser logic port |
| Backup | **Google Drive API** (appDataFolder) | Optional encrypted JSON export; user-owned |
| Architecture | **MVVM + Repository** | ViewModel → Repository → Room / Integrations |
| Tests | **JUnit 5, Robolectric, Compose UI Test, Room in-memory** | `./gradlew test` + `connectedAndroidTest` |

## Why NOT Capacitor-only

1. **Health Connect requires native Android** — no JavaScript bridge to read HC records in background; web app already uses file export as workaround.
2. **Foreground/background read permissions** — HC SDK needs Android manifest permissions and optional `READ_HEALTH_DATA_IN_BACKGROUND`; Capacitor plugins are immature for full HC record types.
3. **Offline Room performance** — 365+ days of mood/symptom/med logs with joins; native SQLite via Room is faster and simpler than IndexedDB in WebView.
4. **User asked for Android Studio project** — Capacitor wraps web; does not produce idiomatic Kotlin/Compose codebase.
5. **4-ABI native libs** — Some health SDKs ship native components; Gradle ABI filters are first-class in native project.

Capacitor could wrap a **future** web UI for rapid prototyping, but it cannot deliver Health Connect integration or the requested Android Studio structure.

## Why NOT full React Native rewrite

1. **User explicitly requested Android Studio files** — Kotlin/Gradle project, not Metro bundler.
2. **Health Connect** — React Native community modules exist but lag official SDK; Kotlin is reference implementation.
3. **Web codebase is Next.js/Prisma** — no shared React Native components to reuse; porting cost ≈ native rewrite without HC benefits.
4. **Testing** — `./gradlew assembleDebug` + Espresso is standard in enterprise Android; RN adds Node toolchain complexity on Windows.
5. **Long-term maintenance** — BP tracker needs reliable offline DB, clinical correctness, and Play Store compliance; Google's Jetpack stack is the stable path.

## Why NOT Flutter

Valid alternative, but team/web reference is TypeScript/Kotlin-adjacent patterns; Compose Material 3 aligns with Android design guidelines; Health Connect samples are Kotlin-first.

## Integration matrix (summary)

See `START_NEW_CHAT.md` § Integration matrix for full mapping.

## Gradle / SDK notes

- **minSdk 30**, **targetSdk 35**, **compileSdk 35**
- Gradle wrapper JAR not committed — generate via Android Studio or `gradle wrapper --gradle-version 8.10.2`
- JDK 17 required (Android Studio bundled)

## References

- [Health Connect sync data](https://developer.android.com/health-and-fitness/health-connect/sync-data)
- [Health Connect read records](https://developer.android.com/health-and-fitness/health-connect/read-data)
- Web reference: `web-reference/docs/android-sync.md`
