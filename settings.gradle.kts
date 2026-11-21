pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement @Suppress("UnstableApiUsage") {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal()
        mavenCentral()
        maven("https://jitpack.io") { name = "Jitpack" }    // libsuperuser
        google()
    }
}

include("shared", "engine", "app", "installer", "watcher", "open")

includeProject(":deagle") {
    projectDir = file("libs/deagle/library")
}

includeProject(":setup-wizard-lib") {
    projectDir = file("libs/setup-wizard/library")
    buildFileName = "standalone.gradle"
}

// <editor-fold defaultstate="collapsed" desc="fun includeProject(...)">
inline fun includeProject(projectPath: String, block: ProjectDescriptor.() -> Unit = {}) {
    include(projectPath)
    project(projectPath).apply(block)
}
// </editor-fold>
