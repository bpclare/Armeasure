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
// The commit this build was made from, shown in the app beside the build number (Bryan's rules: "Version and commit
// visible in the app").
val commit: String = runCatching {
    providers.exec { commandLine("git", "rev-parse", "--short=7", "HEAD"); isIgnoreExitValue = true }.standardOutput.asText.get().trim()
}.getOrDefault("").ifEmpty { "local" }
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
        buildConfigField("String", "COMMIT", "\"$commit\"")
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
        unitTests.isIncludeAndroidResources = true
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
    // The screens' Back tests run on the computer (Robolectric), no phone needed.
    testImplementation(platform("androidx.compose:compose-bom:2025.12.01"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
