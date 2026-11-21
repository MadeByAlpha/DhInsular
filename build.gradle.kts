// Top-level build file where you can add configuration options common to all sub-projects/modules.

plugins {
    id("com.android.application") version "9.5.2" apply false
    id("com.android.library") version "9.5.2" apply false
    id("com.android.built-in-kotlin") version "9.5.2" apply false
    // Still required by the modules which opt out of built-in Kotlin. Also provides the Kotlin Gradle plugin on the classpath.
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}

// Read by the Groovy build scripts of the sub-projects.
extra["kotlin_version"] = "2.4.21"
extra["kotlin_coroutine_version"] = "1.7.3"

if (file("local.gradle").exists()) apply(from = "local.gradle")

allprojects {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }      // libsuperuser
    }

    extra["compileSdkVersion"] = 35
    extra["minSdkVersion"] = 27
}

// External library with compileSdk 26, which requires the Java 8 source level that was the default before AGP 9.
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
