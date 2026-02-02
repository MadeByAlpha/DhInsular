plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val jdkVersion = libs.versions.jdk.get().toInt()
val minVersion = libs.versions.android.min.get().toInt()
val compileVersion = libs.versions.android.compile.get().split('.').map(String::toInt)

kotlin {
    jvmToolchain(jdkVersion)
    compilerOptions.optIn.addAll(
        "kotlin.contracts.ExperimentalContracts",
        "androidx.compose.material3.ExperimentalMaterial3Api",
        "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
    )
}

android {
    namespace = "com.oasisfeng.island"
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
        targetSdk {
            version = release(compileVersion.first())
        }
        versionName = rootProject.version.toString()
        versionCode = 51508

        applicationId = "com.oasisfeng.island"
        //resourceConfigurations += ["en"]
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        val versionEnum = JavaVersion.toVersion(jdkVersion)
        sourceCompatibility = versionEnum
        targetCompatibility = versionEnum
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
    runtimeOnly(projects.engine)

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
    implementation(libs.compose.navigation)
    implementation(libs.compose.lifecycle.viewmodel)

    implementation(libs.accompanist.drawable)

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
