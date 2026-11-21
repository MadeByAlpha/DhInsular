pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

include("shared", "engine", "app", "installer", "watcher", "open")
include(":deagle")
project(":deagle").projectDir = file("libs/deagle/library")
include(":setup-wizard-lib")
project(":setup-wizard-lib").projectDir = file("libs/setup-wizard/library")
project(":setup-wizard-lib").buildFileName = "standalone.gradle"
