plugins {
    alias(libs.plugins.android.library)
}

val jdkVersion = libs.versions.jdk.get().toInt()
val minVersion = libs.versions.android.min.get().toInt()
val compileVersion = libs.versions.android.compile.get().split('.').map(String::toInt)

kotlin {
    jvmToolchain(jdkVersion)
    compilerOptions.optIn.addAll(
        "kotlin.contracts.ExperimentalContracts",
    )
}

android {
    namespace = "com.oasisfeng.island.shared"
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
    }

    compileOptions {
        val versionEnum = JavaVersion.toVersion(jdkVersion)
        sourceCompatibility = versionEnum
        targetCompatibility = versionEnum
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
