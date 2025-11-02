plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.visiboard.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.visiboard.app"
        minSdk = 24
        targetSdk = 34
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
}

dependencies {
    // Kotlin
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:1.8.20"))

    // AndroidX + material
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.9.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.cardview:cardview:1.0.0")

    // Lifecycle / ViewModel / LiveData
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.6.1")
    implementation("androidx.lifecycle:lifecycle-livedata:2.6.1")

    // Navigation
    implementation("androidx.navigation:navigation-fragment:2.6.0")
    implementation("androidx.navigation:navigation-ui:2.6.0")

    // Firebase
    implementation("com.google.firebase:firebase-auth:22.1.1")
    implementation("com.google.firebase:firebase-firestore:24.6.0")
    implementation("com.google.firebase:firebase-storage:20.2.0")
    implementation("com.google.firebase:firebase-messaging:23.2.0")

    // Room
    implementation("androidx.room:room-runtime:2.5.2")
    implementation(libs.swiperefreshlayout)
    annotationProcessor("androidx.room:room-compiler:2.5.2")

    // WorkManager
    implementation("androidx.work:work-runtime:2.8.1")

    // Maps
    implementation("com.google.android.gms:play-services-maps:18.1.0")

    // ML Kit Text Recognition
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:18.0.2")

    // Glide
    implementation("com.github.bumptech.glide:glide:4.15.1")
    annotationProcessor("com.github.bumptech.glide:compiler:4.15.1")

    // Networking (OkHttp)
    implementation ("com.squareup.okhttp3:okhttp:4.11.0")

    // Google Maps & Location
    implementation ("com.google.android.gms:play-services-maps:18.1.0")
    implementation ("com.google.android.gms:play-services-location:21.0.1")

    // Geoapify
    implementation ("org.maplibre.gl:android-sdk:11.0.0")
    implementation ("org.maplibre.gl:android-plugin-annotation-v9:3.0.0")

    // json
    implementation ("org.json:json:20231013")

    // Circle image
    implementation ("de.hdodenhof:circleimageview:3.1.0")


    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
