rootProject.name = "dhinsular"

pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement @Suppress("UnstableApiUsage") {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal()
        mavenCentral()
        google()
        maven("https://jitpack.io") { name = "Jitpack" }    // libsuperuser
    }
}

includeProject(":deagle") {
    projectDir = file("libs/deagle/library")
}

includeProject(":setup-wizard-lib") {
    projectDir = file("libs/setup-wizard/library")
    buildFileName = "standalone.gradle"
}

includeProject(":hidden") { projectDir = file("libs/hidden") }
include("shared", "engine", "watcher", "open")
include("app", "installer")

// <editor-fold defaultstate="collapsed" desc="fun includeProject(...)">
inline fun includeProject(projectPath: String, block: ProjectDescriptor.() -> Unit = {}) {
    include(projectPath)
    project(projectPath).apply(block)
}
// </editor-fold>
