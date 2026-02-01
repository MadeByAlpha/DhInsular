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
    namespace = "com.oasisfeng.island.hidden_api"
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
    }

    compileOptions {
        val versionEnum = JavaVersion.toVersion(jdkVersion)
        sourceCompatibility = versionEnum
        targetCompatibility = versionEnum
    }

    lint {
        abortOnError = false
    }
}

dependencies {
}
