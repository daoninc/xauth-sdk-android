import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(apps.plugins.android.application)
    alias(apps.plugins.kotlin.android)
    alias(apps.plugins.kotlin.kapt)
    alias(apps.plugins.hilt.android)
    alias(apps.plugins.compose.compiler)
    alias(apps.plugins.google.services)
}

android {
    namespace = "com.daon.fido.sdk.sample.kt"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.daon.fido.sdk.sample.kt"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        viewBinding = true
        dataBinding = true
    }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

dependencies {
    implementation(apps.daon.xauth)
    implementation(apps.daon.xauth.authenticator)
    implementation(apps.daon.xauth.face)
    implementation(apps.daon.xauth.voice)

    implementation(apps.daon.fido.crypto)
    implementation(apps.daon.fido.device)
    implementation(apps.daon.voice)
    implementation(apps.daon.voice.ogg)

    implementation(apps.daon.face)
    implementation(apps.daon.face.capture)
    implementation(apps.daon.face.quality)
    implementation(apps.daon.face.liveness)
    implementation(apps.daon.face.matcher)
    implementation(apps.daon.face.detector)

    // CameraX core library
    implementation(apps.camera.core)
    implementation(apps.camera.camera2)
    implementation(apps.camera.lifecycle)
    implementation(apps.camera.video)
    implementation(apps.camera.view)

    implementation(apps.androidx.core)
    implementation(apps.androidx.lifecyle.runtime)
    implementation(apps.androidx.activity.compose)
    implementation(apps.appcompat)
    implementation(apps.androidx.material3)
    // Compose Navigation
    implementation(apps.androidx.compose.navigation)
    implementation(apps.gson)
    implementation(apps.biometric)
    implementation(apps.androidx.fragment)
    implementation(apps.androidx.navigation.fragment)
    implementation(apps.androidx.preference.ktx)
    implementation(apps.androidx.lifecyle.viewmodel)

    // Firebase for push notifications
    implementation(apps.firebase.messaging)

    // Hilt
    implementation(apps.hilt)
    kapt(apps.hilt.compiler)
    implementation(apps.hilt.navigation.compose)
    // Jackson JSON converter
    implementation(apps.jackson)

    val composeBom = platform(apps.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation(apps.constraintlayout.compose)
    implementation(apps.constraintlayout)

    testImplementation(apps.junit)
    androidTestImplementation(apps.test.rule)
    androidTestImplementation(apps.espresso.core)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    implementation("androidx.compose.ui:ui-viewbinding")
}

kapt { correctErrorTypes = true }
