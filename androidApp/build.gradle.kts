import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

dependencies {
    implementation(projects.shared)
    
    // UI & Material Design
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    
    // Retrofit & Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    
    // Lifecycle & Coroutines
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.1")

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

android {
    namespace = "com.example.tes"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.tes"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        viewBinding = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
