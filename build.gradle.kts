plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false

    idea
    alias(libs.plugins.idea.ext)
}

allprojects {
    apply(plugin = "idea")

    idea {
        module {
            isDownloadSources = true
            isDownloadJavadoc = true
        }
    }
}
