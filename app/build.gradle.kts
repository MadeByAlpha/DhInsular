import java.util.Properties

plugins {
    id("com.android.application")
    id("com.android.built-in-kotlin")   // Per-module opt-in, see "android.builtInKotlin" in gradle.properties
    id("org.jetbrains.kotlin.plugin.compose")
}

// Try reading secrets from file, otherwise from environment variables (e.g. CI).
val secrets = Properties().apply {
    val file = rootProject.file("secrets.properties")
    if (file.exists()) file.inputStream().use(::load)
    else {
        setProperty("signing_keystore_password", System.getenv("KEYSTORE_PASSWORD") ?: "")
        setProperty("signing_key_password", System.getenv("KEY_PASSWORD") ?: "")
        setProperty("signing_key_alias", System.getenv("KEY_ALIAS") ?: "")
    }
}

val compileSdkVersion = rootProject.extra["compileSdkVersion"] as Int
val minSdkVersion = rootProject.extra["minSdkVersion"] as Int
val kotlinVersion = rootProject.extra["kotlin_version"] as String
val kotlinCoroutineVersion = rootProject.extra["kotlin_coroutine_version"] as String

android {
    namespace = "com.oasisfeng.island.mobile"

    compileSdk = compileSdkVersion

    defaultConfig {
        applicationId = "com.oasisfeng.island.fdroid"
        minSdk = minSdkVersion
        targetSdk = 34
        versionCode = 64200
        versionName = "6.4.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        missingDimensionStrategy("compat", "gingerbreadCompat", "platformDeprecated")
    }

    androidResources {
        localeFilters += listOf("en", "zh", "zh-rTW", "pt-rBR")
    }

    buildFeatures {
        buildConfig = true
        dataBinding = true
        compose = true
    }

    signingConfigs {
        create("release") {
            // You need to specify either an absolute path or include the keystore file in the same directory as this file.
            storeFile = file("../android-signing-keystore.jks")
            storePassword = secrets.getProperty("signing_keystore_password")
            keyAlias = secrets.getProperty("signing_key_alias")
            keyPassword = secrets.getProperty("signing_key_password")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    lint {
        checkOnly += "NewApi"
        abortOnError = true
        checkReleaseBuilds = false
    }
}

dependencies {
    debugImplementation(project(path = ":setup-wizard-lib", configuration = "gingerbreadCompatDebugRuntimeElements"))
    releaseImplementation(project(path = ":setup-wizard-lib", configuration = "gingerbreadCompatReleaseRuntimeElements"))

    implementation(project(":shared"))
    implementation(project(":deagle"))
    implementation(project(":engine"))
    implementation(project(":installer"))
    implementation(project(":watcher"))
    implementation(project(":open"))

    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:$kotlinCoroutineVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:$kotlinCoroutineVersion")
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.fragment:fragment-ktx:1.6.0")
    implementation("androidx.lifecycle:lifecycle-common-java8:2.6.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.4.0")
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")
    implementation("androidx.recyclerview:recyclerview:1.3.0")
    implementation("androidx.biometric:biometric:1.0.1")
    implementation("com.google.android.material:material:1.4.0")
    implementation("androidx.cardview:cardview:1.0.0")

    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.ui:ui-tooling-preview")         // @Preview
    implementation("androidx.compose.runtime:runtime-livedata")      // LiveData.observeAsState()
    implementation("androidx.compose.material:material")
    implementation("androidx.activity:activity-compose:1.8.0")       // Integration with activities, not managed by the Compose BOM
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose")

    implementation("eu.chainfire:libsuperuser:1.1.1")
    implementation("uk.co.samuelwall:material-tap-target-prompt:2.0.1")

    implementation("dev.rikka.shizuku:api:12.1.0")
    implementation("dev.rikka.shizuku:provider:12.1.0")

    debugImplementation("androidx.compose.ui:ui-tooling")           // Implementation for @Preview (only needed by Android Studio)

    androidTestImplementation("org.mockito:mockito-android:2.8.9")
    androidTestImplementation("androidx.test:runner:1.3.0")
    androidTestImplementation("androidx.test:core:1.5.0")
}
