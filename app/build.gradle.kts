
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin") // Add this
}

android {
    namespace = "com.example.geminivoiceassistant"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.geminivoiceassistant"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
        buildConfig = true
    }
}

dependencies {

    /*implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
*/
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)

    // Gemini AI: Lets our app talk to the AI model.
    implementation(libs.generativeai)

    // ViewModel: A component that holds our app's data.
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    // Coroutine support for ViewModel: Helps with background tasks.
    implementation(libs.androidx.lifecycle.runtime.ktx.v282)

    // Activity KTX: Provides the "by viewModels()" delegate to easily create a ViewModel.
    implementation(libs.androidx.activity.ktx.v190)

    // Library for rendering Markdown text in TextViews
    implementation("io.noties.markwon:core:4.6.2")

    implementation("com.airbnb.android:lottie:5.2.0")
}