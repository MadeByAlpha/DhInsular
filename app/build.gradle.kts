plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    jvmToolchain(11)
    compilerOptions {
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
        )
    }
}

android {
    namespace = rootProject.group.toString()
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk {
            version = release(27)
        }
        targetSdk {
            version = release(36)
        }
        versionName = "5.2.0-SNAPSHOT"
        versionCode = 51508

        applicationId = "kr.alpha93.dhizuku"
        //resourceConfigurations += ["en"]
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        buildConfig = true
        dataBinding = true
        compose = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isJniDebuggable = false
            isPseudoLocalesEnabled = false

            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    /*
    lint {
        checkOnly 'NewApi'
        abortOnError true
        checkReleaseBuilds false
        htmlReport false
        xmlReport false
        textReport true
        textOutput file('stdout')
    }
     */
}

dependencies {
    implementation(projects.shared)

    implementation(libs.kotlinx.coroutines.android)


    implementation(libs.androidx)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.lifecycle)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.biometric)
    implementation("androidx.cardview:cardview:1.0.0")  // TODO: Replace this
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0") // TODO: Migrato to livedata

    implementation(platform(libs.compose))
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.runtime.livedata)
    implementation(libs.compose.material3)
    implementation(libs.compose.activity)
    implementation(libs.compose.lifecycle.viewmodel)

    implementation(libs.material)
    implementation("eu.chainfire:libsuperuser:1.1.1")   // TODO: Migrate into com.github.topjohnwu.libsu
    implementation("uk.co.samuelwall:material-tap-target-prompt:3.3.2") // TODO: Migrate into com.canopas.intro-showcase-view:introshowcaseview

    implementation(libs.shizuku)
    implementation(libs.shizuku.provider)

    debugImplementation(libs.compose.ui.tooling)

    androidTestImplementation(libs.mockito.android)
    androidTestImplementation(libs.androidx.test)
    androidTestImplementation(libs.androidx.test.runner)
}
