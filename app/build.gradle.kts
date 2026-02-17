plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt.android)
}

val debugAbi = providers.gradleProperty("debugAbi").orNull

android {
    namespace = "io.morgan.idonthaveyourtime"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.morgan.idonthaveyourtime"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        if (!debugAbi.isNullOrBlank()) {
            ndk {
                abiFilters += debugAbi
            }
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("MYAPP_STORE_FILE") ?: project.property("MYAPP_STORE_FILE") as String)
            storePassword = System.getenv("MYAPP_STORE_PASSWORD") ?: project.property("MYAPP_STORE_PASSWORD") as String
            keyAlias = System.getenv("MYAPP_KEY_ALIAS") ?: project.property("MYAPP_KEY_ALIAS") as String
            keyPassword = System.getenv("MYAPP_KEY_PASSWORD") ?: project.property("MYAPP_KEY_PASSWORD") as String
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    androidResources {
        noCompress += setOf("bin", "gguf")
    }
}

dependencies {
    implementation(project(":feature:summarize:impl"))
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:audio"))
    implementation(project(":core:whisper"))
    implementation(project(":core:llm"))
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.activity.compose)

    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.viewmodel.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.timber)

    debugImplementation(libs.compose.ui.tooling)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(project(":core:model"))
    androidTestImplementation(project(":core:common"))
    androidTestImplementation(project(":core:domain"))
    androidTestImplementation(project(":core:audio"))
    androidTestImplementation(project(":core:data"))
    androidTestImplementation(project(":core:whisper"))
    androidTestImplementation(project(":core:llm"))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.truth)
}
