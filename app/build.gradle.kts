plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("app.cash.paparazzi")
}

// Versionsnummer an genau einer Stelle. Die Update-Prüfung vergleicht sie mit
// dem neuesten Release auf GitHub.
val appVersionName = "2.0.0"
val appVersionCode = 1

android {
    namespace = "de.gun.baustellen.reloaded"
    compileSdk = 35

    defaultConfig {
        // Eigene App-ID: läuft parallel zum alten Baustellen Pilot
        applicationId = "de.gun.baustellen.reloaded"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        // Fester Schlüssel im Projekt: jede neue APK lässt sich über die
        // vorhandene installieren, ohne Deinstallation und Datenverlust.
        create("fest") {
            storeFile = rootProject.file("keystore/baustellen.keystore")
            storeType = "pkcs12"
            storePassword = "baustellen2026"
            keyAlias = "baustellen"
            keyPassword = "baustellen2026"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("fest")
        }
        getByName("release") {
            signingConfig = signingConfigs.getByName("fest")
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    testImplementation("junit:junit:4.13.2")
}
