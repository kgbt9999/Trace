plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

import java.util.Properties

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

android {
    namespace = "com.moodlife.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.moodlife.app"
        minSdk = 30
        targetSdk = 36
        versionCode = 10
        versionName = "0.4.1"

        testInstrumentationRunner = "com.moodlife.app.HiltTestRunner"
        vectorDrawables { useSupportLibrary = true }

        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }

    }

    lint {
        checkReleaseBuilds = true
        abortOnError = false
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        debug {
            // Emulator (x86_64) + phones — avoids INSTALL_FAILED_NO_MATCHING_ABIS
            ndk {
                abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86_64")
            }
            // Same key as releases: once a phone has a key-rotated release, only that key can
            // update it, so Android Studio builds must use it too to install over without wiping data.
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        release {
            // Real phones only — fewer ABIs = less RAM during release merge
            ndk {
                abiFilters += listOf("armeabi-v7a", "arm64-v8a")
            }
            signingConfig = if (keystorePropertiesFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        // Declared after release: initWith copies its settings at this point.
        // Optimized like release (R8, not debuggable) but signed with the debug key, so it
        // installs *over* the debug build and keeps the diary. Debug builds of Compose apps are
        // noticeably janky; use this one on a real phone: ./gradlew installStaging
        create("staging") {
            // Release optimizations and the release key (from initWith), plus x86_64 for the emulator.
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            // Also runs on the x86_64 emulator, so release optimizations can be checked there.
            ndk { abiFilters += listOf("x86_64") }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }



    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.9.8")

    // Room
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.57.2")
    ksp("com.google.dagger:hilt-compiler:2.57.2")
    implementation("androidx.hilt:hilt-navigation-compose:1.3.0")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    implementation("androidx.hilt:hilt-work:1.3.0")
    ksp("androidx.hilt:hilt-compiler:1.3.0")
    implementation("androidx.documentfile:documentfile:1.1.0")

    // Health Connect (stub — enable when implementing Phase 4)
    implementation("androidx.health.connect:connect-client:1.1.0")

    // Network — Open-Meteo weather
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")
    implementation("com.squareup.okhttp3:okhttp:5.4.0")

    // EncryptedSharedPreferences for API keys (Keystore-backed)
    implementation("androidx.security:security-crypto:1.1.0")

    // SQLCipher — encrypted Room database at rest
    implementation("net.zetetic:android-database-sqlcipher:4.5.4")
    implementation("androidx.sqlite:sqlite:2.7.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.3.21")
    testImplementation("org.json:json:20260814")
    androidTestImplementation("com.google.dagger:hilt-android-testing:2.57.2")
    kspAndroidTest("com.google.dagger:hilt-compiler:2.57.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

// Releases are signed with the release key plus a rotation lineage from the old debug key
// (signing/rotation.lineage). One APK then installs on a clean phone and over every earlier
// build — the debug-signed 0.1–0.3 releases and Android Studio installs included — keeping data.
val signReleaseWithLineage by tasks.registering {
    dependsOn("packageRelease")
    doLast {
        val lineage = rootProject.file("signing/rotation.lineage")
        val debugKs = File(System.getProperty("user.home"), ".android/debug.keystore")
        if (!keystorePropertiesFile.exists() || !lineage.exists() || !debugKs.exists()) {
            logger.warn("Lineage signing skipped: keystore.properties, signing/rotation.lineage or ~/.android/debug.keystore is missing")
            return@doLast
        }
        val apk = layout.buildDirectory.file("outputs/apk/release/app-release.apk").get().asFile
        val windows = System.getProperty("os.name").lowercase().contains("windows")
        val apksigner = android.sdkDirectory.resolve("build-tools/${android.buildToolsVersion}/apksigner" + if (windows) ".bat" else "")
        fun run(vararg args: String) {
            val pb = ProcessBuilder(apksigner.absolutePath, *args).redirectErrorStream(true)
            // Passwords go through the environment, not the command line.
            pb.environment()["TRACE_RELEASE_STORE_PASS"] = keystoreProperties["storePassword"] as String
            pb.environment()["TRACE_RELEASE_KEY_PASS"] = keystoreProperties["keyPassword"] as String
            val p = pb.start()
            val out = p.inputStream.bufferedReader().readText()
            check(p.waitFor() == 0) { "apksigner ${args.first()} failed:\n$out" }
        }
        run(
            "sign",
            "--ks", debugKs.absolutePath, "--ks-key-alias", "androiddebugkey",
            "--ks-pass", "pass:android", "--key-pass", "pass:android",
            "--next-signer",
            "--ks", rootProject.file(keystoreProperties["storeFile"] as String).absolutePath,
            "--ks-key-alias", keystoreProperties["keyAlias"] as String,
            "--ks-pass", "env:TRACE_RELEASE_STORE_PASS", "--key-pass", "env:TRACE_RELEASE_KEY_PASS",
            "--lineage", lineage.absolutePath,
            // Rotate on every supported Android (minSdk 30), not only 13+ as apksigner does by default.
            "--rotation-min-sdk-version", "28",
            apk.absolutePath,
        )
        run("verify", apk.absolutePath)
        apk.copyTo(apk.resolveSibling("Trace-${android.defaultConfig.versionName}.apk"), overwrite = true)
        logger.lifecycle("Signed with key rotation: ${apk.resolveSibling("Trace-${android.defaultConfig.versionName}.apk")}")
    }
}
tasks.matching { it.name == "assembleRelease" }.configureEach { finalizedBy(signReleaseWithLineage) }
