import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// CI passes -PversionCode (the run number + 1) and -PupdateRepo (owner/name); the app checks that repo's latest
// release for a newer versionCode. The release key's password comes from KEYSTORE_PASSWORD (a GitHub secret in CI).
val buildCode = (findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
val updateRepo = (findProperty("updateRepo") as String?) ?: "bpclare/Armeasure"
val keystorePassword: String? = System.getenv("KEYSTORE_PASSWORD") ?: (findProperty("keystorePassword") as String?)

android {
    namespace = "com.bpclare.armeasure"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.bpclare.armeasure"
        minSdk = 29
        targetSdk = 36
        versionCode = buildCode
        versionName = "build $buildCode"
        buildConfigField("String", "UPDATE_REPO", "\"$updateRepo\"")
    }

    signingConfigs {
        create("release") {
            storeFile = file("release.keystore")
            storePassword = keystorePassword
            keyAlias = "armeasure"
            keyPassword = keystorePassword
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (keystorePassword != null) signingConfigs.getByName("release") else null
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("com.google.ar:core:1.57.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
