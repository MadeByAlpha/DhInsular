plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.parcelize)
}

kotlin {
    jvmToolchain(11)
}

android {
    namespace = "com.oasisfeng.island.engine"
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
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        buildConfig = true
    }

    /*
    lint {
        checkOnly 'NewApi'
        abortOnError true
        xmlReport false
        textReport true
        textOutput file('stdout')
    }
     */
}

dependencies {
    api(projects.shared)

    implementation(libs.kotlinx.coroutines)
    implementation(libs.androidx)   // ShortcutShuttle

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
}
