import java.util.Properties

plugins {
    id("com.android.application")
    alias(libs.plugins.kotlin.compose)
}

/**
 * Release signing, read from keystore.properties in the project root (kept out of version
 * control) or from environment variables of the same names in upper case
 * (SMWC_STORE_FILE, SMWC_STORE_PASSWORD, SMWC_KEY_ALIAS, SMWC_KEY_PASSWORD):
 *   storeFile=/Users/me/.android/smwc-release.jks
 *   storePassword=…
 *   keyAlias=smwc
 *   keyPassword=…
 * Without them, the release build is left unsigned.
 */
val signingProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}

val releaseSigning: Map<String, String>? = run {
    val props = signingProps
    val keys = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
    val values = keys.associateWith { key ->
        props.getProperty(key) ?: System.getenv("SMWC_" + key.replace(Regex("([A-Z])"), "_$1").uppercase())
    }
    if (values.values.all { !it.isNullOrBlank() }) values.mapValues { it.value!! } else null
}

/**
 * The project's shared debug key, kept out of version control (e.g. in a private cloud folder)
 * so every machine signs debug builds with the same SHA-1, the one registered for Google
 * sign-in. Found at `debugStoreFile` in keystore.properties (or SMWC_DEBUG_STORE_FILE), else at
 * app/debug.keystore. Without it, the Android SDK's own debug key is used, and Google sign-in
 * only works if that key's SHA-1 is registered too.
 */
val sharedDebugKeystore: File? =
    (signingProps.getProperty("debugStoreFile") ?: System.getenv("SMWC_DEBUG_STORE_FILE"))
        ?.let { file(it) }
        ?.takeIf { it.exists() }
        ?: file("debug.keystore").takeIf { it.exists() }

/**
 * The app version, from the release's Git tag in CI (`-PappVersion=1.2.3`, see
 * .github/workflows/release.yml); local builds use the default. versionCode is derived from it
 * (1.2.3 → 1002003), so each release automatically has a higher one, as Android requires to
 * install it as an update.
 */
val appVersion: String = (findProperty("appVersion") as String?)?.removePrefix("v") ?: "0.1.0"
val appVersionCode: Int = run {
    val parts = Regex("""(\d+)\.(\d+)\.(\d+)""").matchEntire(appVersion)?.groupValues?.drop(1)?.map(String::toInt)
        ?: error("appVersion must look like 1.2.3, got \"$appVersion\"")
    val (major, minor, patch) = parts
    require(minor < 1000 && patch < 1000) { "minor and patch must be below 1000" }
    major * 1_000_000 + minor * 1_000 + patch
}

android {
    namespace = "com.capivaraec.smwc.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.capivaraec.smwc"
        minSdk = 33
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersion
    }

    // Debug builds use the shared debug key (see sharedDebugKeystore); release builds, your own key.
    signingConfigs {
        if (sharedDebugKeystore != null) {
            getByName("debug") {
                storeFile = sharedDebugKeystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
        if (releaseSigning != null) {
            create("release") {
                storeFile = file(releaseSigning.getValue("storeFile"))
                storePassword = releaseSigning.getValue("storePassword")
                keyAlias = releaseSigning.getValue("keyAlias")
                keyPassword = releaseSigning.getValue("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // A separate app next to the release one (different id and signing key), so both can
            // be installed. Its Google sign-in needs its own OAuth client (package ".debug").
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            // R8 shrinks and optimizes the code; see proguard-rules.pro for what it must keep.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.ktor.client.okhttp)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.play.services.auth)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.material.icons.extended) // unused icons are removed by R8 in release
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.navigation.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
}
