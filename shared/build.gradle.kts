plugins {
    alias(libs.plugins.android.library)
}

kotlin {
    jvmToolchain(11)
}

android {
    namespace = "com.oasisfeng.island.shared"
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
    api(projects.libs.deagle)
    api(libs.lyra)
    api(libs.spotbugs.annotations)

    implementation(libs.kotlinx.coroutines)
    implementation(libs.androidx)
    implementation(libs.condom)
    implementation(libs.okhttp)
    implementation(libs.okhttp.urlconnection)
    implementation("com.jaredrummler:apk-parser:1.0.2")

    testImplementation(libs.junit)
    testImplementation(libs.mockito)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
}
