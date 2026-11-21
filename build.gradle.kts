plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.builtInKotlin) apply false

    // Still required by the modules which opt out of built-in Kotlin.
    // Also provides the Kotlin Gradle plugin on the classpath.
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.plugin.compose) apply false
}

// TODO: Remove this
val compileSdkVersion = libs.versions.android.sdk.compile.get().toInt()
val minimumSdkVersion = libs.versions.android.sdk.minimum.get().toInt()
allprojects {
    extra["compileSdkVersion"] = compileSdkVersion
    extra["minSdkVersion"] = minimumSdkVersion
}

// Read by the build script of the "libs/deagle" submodule, which is not covered by the version catalog.
project(":deagle").extra["kotlin_version"] = libs.versions.kotlin.get()

// TODO: External library with compileSdk 26, which requires the Java 8 source level that was the default before AGP 9.
project(":setup-wizard-lib") {
    plugins.withId("com.android.library") {
        extensions.configure<com.android.build.api.dsl.LibraryExtension> {
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_1_8
                targetCompatibility = JavaVersion.VERSION_1_8
            }
        }
    }
}
