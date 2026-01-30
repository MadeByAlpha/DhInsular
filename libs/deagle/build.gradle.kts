plugins {
    alias(libs.plugins.android.library)
}

kotlin {
    jvmToolchain(11)
}

android {
    namespace = "com.oasisfeng.deagle"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk {
            version = release(27)
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
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
