plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.parcelize)
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
    namespace = "com.oasisfeng.island.engine"
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
    api(projects.shared)

    implementation(libs.kotlinx.coroutines)
    implementation(libs.androidx)   // ShortcutShuttle
    implementation(libs.dhizuku)
    implementation(libs.dhizuku.server)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
}
