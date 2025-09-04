plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("org.jetbrains.kotlin.kapt")
}

android {
    namespace = "com.example.runnerapp"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.runnerapp"
        minSdk = 21
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

    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // Base
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.google.material)
    implementation(libs.androidx.drawerlayout)
    implementation("androidx.fragment:fragment-ktx:1.8.2")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Room
    implementation(libs.androidx.room.runtime)
    kapt(libs.androidx.room.compiler)
    implementation("androidx.room:room-ktx:2.6.1")

    // Lifecycle
    implementation(libs.androidx.lifecycle.vm)
    implementation(libs.androidx.lifecycle.ld)

    // Activity KTX
    implementation(libs.androidx.activity.ktx)

    // Coroutines
    implementation(libs.kotlinx.coroutines)

    // Mapa
    implementation("org.osmdroid:osmdroid-android:6.1.18")

    // JSON
    implementation("com.google.code.gson:gson:2.10.1")

    // Música
    implementation("com.google.android.exoplayer:exoplayer:2.19.1")
    implementation("androidx.media:media:1.6.0")
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")

    // Extra UI que usabas
    implementation("me.tankery.lib:circularSeekBar:1.3.2")

    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.material:material-icons-extended") // <- NECESARIO

    // Imágenes en Compose
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.material:material-icons-extended") // 👈 necesario para Icons.Default.*
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Tests
    testImplementation(libs.test.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.espresso)
}
