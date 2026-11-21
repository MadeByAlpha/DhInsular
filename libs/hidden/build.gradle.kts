plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.oasisfeng.island.hidden"
    compileSdk = libs.versions.android.sdk.compile.get().toInt()

    enableKotlin = false

    defaultConfig {
        minSdk = libs.versions.android.sdk.minimum.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.android.jdk.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.android.jdk.get())
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
}
