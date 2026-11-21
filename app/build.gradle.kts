import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.android.builtInKotlin)   // Per-module opt-in, see "android.builtInKotlin" in gradle.properties
    alias(libs.plugins.kotlin.plugin.compose)
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

android {
    namespace = "com.oasisfeng.island.mobile"   // TODO: change to "com.oasisfeng.island"

    compileSdk = compileSdkVersion

    defaultConfig {
        applicationId = "com.oasisfeng.island"
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

    implementation(libs.kotlin.stdlib.jdk8)
    implementation(libs.kotlinx.coroutines.jdk8)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.common.java8)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.localbroadcastmanager)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.biometric)
    implementation(libs.google.material)
    implementation(libs.androidx.cardview)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui.tooling.preview)         // @Preview
    implementation(libs.androidx.compose.runtime.livedata)      // LiveData.observeAsState()
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.activity.compose)       // Integration with activities, not managed by the Compose BOM
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.libsuperuser)
    implementation(libs.materialTapTargetPrompt)

    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    debugImplementation(libs.androidx.compose.ui.tooling)           // Implementation for @Preview (only needed by Android Studio)

    androidTestImplementation(libs.test.mockito.android)
    androidTestImplementation(libs.test.androidx.test.runner.legacy)
    androidTestImplementation(libs.test.androidx.test)
}
