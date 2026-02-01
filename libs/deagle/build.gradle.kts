plugins {
    alias(libs.plugins.android.library)
}

val jdkVersion = libs.versions.jdk.get().toInt()
val minVersion = libs.versions.android.min.get().toInt()
val compileVersion = libs.versions.android.compile.get().split('.').map(String::toInt)

kotlin {
    jvmToolchain(jdkVersion)
}

android {
    namespace = "com.oasisfeng.deagle"
    compileSdk {
        version = release(compileVersion.first()) {
            minorApiLevel = compileVersion.getOrNull(1)
            sdkExtension = compileVersion.getOrNull(2)
        }
    }

    defaultConfig {
        minSdk {
            version = release(minVersion)
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        val versionEnum = JavaVersion.toVersion(jdkVersion)
        sourceCompatibility = versionEnum
        targetCompatibility = versionEnum
    }

    buildFeatures {
        buildConfig = true
        dataBinding = true
    }
}

dependencies {
    implementation(libs.spotbugs.annotations)
    implementation("net.sourceforge.streamsupport:android-retrostreams:1.7.4")  // Deprecated

    implementation(libs.material)
    implementation(libs.androidx.browser)   // TODO: runtimeOnly
    runtimeOnly(libs.androidx.compat)
    runtimeOnly(libs.androidx.lifecycle.viewmodel)
    runtimeOnly(libs.androidx.lifecycle.livedata)
    runtimeOnly(libs.androidx.recyclerview)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
