# Сборка APK (Trace)

## Debug vs Release

| Тип | Когда |
|-----|--------|
| Debug | Себе для проверки |
| Release + свой keystore | Раздача другим |

Установка вне Play Store всегда просит разрешить источник — это нормально.

## PowerShell

```powershell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot"  # JDK 17 или 21
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path
$env:GRADLE_OPTS = "-Xmx4096m -XX:MaxMetaspaceSize=1024m"

cd android

# один раз: keystore
keytool -genkeypair -v -keystore release.keystore -alias moodlife -keyalg RSA -keysize 2048 -validity 10000
copy keystore.properties.example keystore.properties
# заполните пароли в keystore.properties (storeFile=release.keystore)

.\gradlew.bat assembleRelease --no-parallel --max-workers=1
```

APK: `app/build/outputs/apk/release/app-release.apk`

`local.properties` со `sdk.dir` создаёт Android Studio; в git не коммитьте.

## Android Studio (RU)

**Сборка → Generate App Bundles or APKs → APK → release** + keystore.
