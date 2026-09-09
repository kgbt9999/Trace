# Gradle wrapper setup

Gradle Wrapper (`gradlew.bat` + `gradle/wrapper/`) уже в репозитории.

## Option A — Android Studio (recommended)

1. Install [Android Studio](https://developer.android.com/studio).
2. **File → Open** → select `android/` folder.
3. Sync Gradle, then **Build → Make Project** or Run.

## Option B — Command line

```powershell
cd android
.\gradlew.bat assembleDebug
```

## Requirements

- **JDK 17 or 21** (not JDK 25 — AGP 8.7.x)
- **Android SDK** with API 35 platform + build-tools
- Set `ANDROID_HOME` or let Android Studio manage SDK

## Verify build

```powershell
cd android
.\gradlew.bat test
.\gradlew.bat assembleDebug
```

Instrumented tests require a connected device/emulator:

```powershell
.\gradlew.bat connectedAndroidTest
```
