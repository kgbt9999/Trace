# Сборка и раздача APK (Trace)

## Debug vs Release

| Тип | Когда | Комментарий |
|-----|--------|-------------|
| **Debug** | Только себе | Больше предупреждений при установке |
| **Release + своя подпись** | Раздача другим | Один keystore = можно обновлять поверх |

Установка **вне Google Play** всегда требует разрешения «из этого источника». Полностью убрать это можно только через Play Store / Internal testing.

---

## Android Studio (русский интерфейс)

1. Открыть папку **`android/`**.
2. **Сборка → Generate App Bundles or APKs** (или «Создать подписанный набор…»).
3. Выбрать **APK** → создать / выбрать **keystore** → вариант **release**.
4. Файл: `android/app/build/outputs/apk/release/app-release.apk`.

**Gradle JDK:** если Sync падает с *Incompatible Gradle JVM / 25.x* →  
**Файл → Настройки → Сборка → Gradle → Gradle JDK → 17 или 21** (не 25).

---

## Терминал (PowerShell)

### Память (если был OutOfMemory)

В `android/gradle.properties` уже задано примерно:

```properties
org.gradle.jvmargs=-Xmx4096m -XX:MaxMetaspaceSize=1024m
```

Перед сборкой:

```powershell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot"  # свой JDK 17/21
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path
$env:GRADLE_OPTS = "-Xmx4096m -XX:MaxMetaspaceSize=1024m"
```

Нужен `local.properties` с `sdk.dir=...` (Android Studio создаёт сама; в git не коммитить).

### Подпись (один раз)

```powershell
cd android
keytool -genkeypair -v -keystore release.keystore -alias moodlife -keyalg RSA -keysize 2048 -validity 10000
copy keystore.properties.example keystore.properties
# отредактируйте пароли в keystore.properties
```

`storeFile=release.keystore` — путь относительно папки `android/`.

### Сборка

```powershell
cd android
.\gradlew.bat assembleRelease --no-parallel --max-workers=1
```

APK: `app/build/outputs/apk/release/app-release.apk`

---

## Получателю APK

1. Открыть файл на телефоне.
2. Разрешить установку из источника.
3. Если Play Защита → **Подробнее → Всё равно установить**.
4. «Приложение не установлено» чаще всего = другая подпись; удалите старую версию или используйте тот же keystore.

---

## Не публиковать в Git

- `release.keystore` / `*.jks`
- `keystore.properties`
- `local.properties`
- готовые `*.apk`
