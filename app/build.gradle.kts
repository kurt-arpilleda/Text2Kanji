plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.text2kanji"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.text2kanji"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))

    // Jetpack Compose UI
    implementation("androidx.compose.ui:ui:1.6.0-alpha05") // Ensure this is compatible with the BOM version
    implementation("androidx.compose.ui:ui-tooling-preview:1.6.0-alpha05") // Ensure this is compatible with the BOM version
    implementation("androidx.compose.material3:material3:1.3.0") // Material 3 for BottomNavigation

    // Additional Compose Libraries
    implementation("androidx.compose.material:material-icons-core:1.6.0-alpha05") // Material Icons
    implementation("androidx.compose.material:material-icons-extended:1.6.0-alpha05") // Extended Material Icons

    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.1")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("io.coil-kt:coil-compose:2.0.0")
    implementation(libs.androidx.espresso.core)

    // Testing dependencies
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    // Debugging dependencies
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    // Duplicate entries removed for clarity
    implementation ("androidx.activity:activity-compose:1.9.3")
    implementation ("com.google.accompanist:accompanist-pager:0.28.0") // Use the latest version
    implementation ("androidx.activity:activity-ktx:1.9.2")
    implementation ("androidx.compose.foundation:foundation:1.7.4")
    implementation ("androidx.compose.foundation:foundation-layout:1.7.3")
    implementation ("io.coil-kt:coil-compose:2.3.0")
    implementation ("androidx.media3:media3-exoplayer:1.4.1") // Latest stable version
    implementation ("androidx.media3:media3-ui:1.4.1")
    implementation ("com.google.android.exoplayer:exoplayer:2.19.1")
    implementation ("com.google.android.material:material:1.9.0")
    implementation ("androidx.compose.ui:ui:1.3.0")
    implementation ("androidx.compose.material3:material3:1.3.0")
    implementation ("io.coil-kt:coil-compose:2.3.0")
    implementation ("androidx.work:work-runtime-ktx:2.9.1")
    implementation ("androidx.lifecycle:lifecycle-service:2.8.6")
    implementation ("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.6") // For ViewModel
    implementation ("androidx.lifecycle:lifecycle-livedata-ktx:2.6.2")
    implementation ("com.google.accompanist:accompanist-permissions:0.32.0")
    implementation ("androidx.navigation:navigation-compose:2.5.3")
    implementation ("com.google.code.gson:gson:2.10.1")
    implementation ("com.google.mlkit:translate:17.0.3")
    implementation ("com.google.mlkit:language-id:17.0.6")
}