plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.example.mushroom_grader"
    compileSdk = 35  // Updated to Android 16

    defaultConfig {
        applicationId = "com.example.mushroom_grader"
        minSdk = 29
        targetSdk = 35  // Updated to Android 16
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

    // ✅ Custom APK Naming
    applicationVariants.all {
        outputs.all {
            val outputImpl = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            val buildType = buildType.name
            // Result: MushroomGrader-debug.apk or MushroomGrader-release.apk
            outputImpl.outputFileName = "MushroomGrader-${buildType}.apk"
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
        viewBinding = true
        mlModelBinding = true
    }
}

dependencies {
    // ==================== Core Android ====================
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")

    // ==================== Navigation Component ====================
    val navVersion = "2.9.6"
    implementation("androidx.navigation:navigation-fragment-ktx:$navVersion")
    implementation("androidx.navigation:navigation-ui-ktx:$navVersion")

    // ==================== Image Processing ====================
    // ✅ REQUIRED: ExifInterface for proper image rotation handling
    implementation("androidx.exifinterface:exifinterface:1.4.2")

    // ==================== Fragment & Lifecycle ====================
    implementation("androidx.fragment:fragment-ktx:1.8.9")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.10.0")

    // ==================== Coroutines ====================
    // ✅ REQUIRED: For async image processing and classification
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    // ==================== Room Database ====================
    val roomVersion = "2.8.4"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // ==================== CameraX ====================
    // ✅ REQUIRED: For camera capture, quality optimization, and Camera2Interop distance measurement
    val cameraxVersion = "1.5.2"
    implementation("androidx.camera:camera-core:$cameraxVersion")  // ⭐ CRITICAL for Camera2Interop!
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // ==================== TensorFlow Lite ====================
    // ✅ REQUIRED: For mushroom classification model
    implementation("org.tensorflow:tensorflow-lite:2.17.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.5.0")
    implementation("org.tensorflow:tensorflow-lite-metadata:0.5.0")

    // ==================== UI Components ====================
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.viewpager2:viewpager2:1.1.0")


    // ==================== PDF Export ====================
    implementation("com.itextpdf:itext7-core:7.2.6")

    // ==================== Testing ====================
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}
