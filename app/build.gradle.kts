plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.android.builtInKotlin)   // Per-module opt-in, see "android.builtInKotlin" in gradle.properties
    alias(libs.plugins.kotlin.plugin.compose)
}

android {
    namespace = "com.oasisfeng.island.mobile"   // TODO: change to "com.oasisfeng.island"

    compileSdk = libs.versions.android.sdk.compile.get().toInt()

    defaultConfig {
        applicationId = "com.oasisfeng.island"
        minSdk = libs.versions.android.sdk.minimum.get().toInt()
        // TODO: https://developer.android.com/about/versions/15/behavior-changes-15#window-insets
        targetSdk = 34 //libs.versions.android.sdk.compile.get().toInt()
        versionCode = rootProject.property("version.code").toString().toInt()
        versionName = rootProject.version.toString()
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
            keyAlias = "production-oss"
            keyPassword = providers.gradleProperty("signing.key.productionOSS").orNull
            enableV1Signing = false

            storeFile = providers.gradleProperty("signing.keyStore.path").orNull?.let(::file)
            storePassword = providers.gradleProperty("signing.keyStore.password").orNull
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.android.jdk.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.android.jdk.get())
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

    implementation(libs.kotlin.stdlib.jdk8)
    implementation(libs.kotlinx.coroutines.jdk8)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.lifecycle.common.java8)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.localbroadcastmanager)
    implementation(libs.androidx.recyclerview)
    implementation(libs.google.material)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui.tooling.preview)    // @Preview
    implementation(libs.androidx.compose.runtime.livedata)      // LiveData.observeAsState()
    implementation(libs.androidx.compose.material)

    implementation(libs.libsuperuser)
    implementation(libs.materialTapTargetPrompt)

    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    debugImplementation(libs.androidx.compose.ui.tooling)   // Impl for @Preview (only needed by Android Studio)

    androidTestImplementation(libs.test.mockito.android)
    androidTestImplementation(libs.test.androidx.test.runner.legacy)
    androidTestImplementation(libs.test.androidx.test)
}
