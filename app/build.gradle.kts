plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.arlys.moviflexx"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.arlys.moviflexx"
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

    // ✅ Resuelve conflicto de archivos duplicados entre android-mail y android-activation
    packaging {
        resources {
            excludes += setOf(
                "META-INF/NOTICE.md",
                "META-INF/LICENSE.md",
                "META-INF/NOTICE",
                "META-INF/LICENSE",
                "META-INF/DEPENDENCIES"
            )
        }
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.uiautomator)

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    // 🔥 Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-database")
    implementation("com.google.firebase:firebase-messaging")

    // 🔥 Google Login
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    implementation("com.facebook.android:facebook-login:17.0.2")

    implementation("com.android.volley:volley:1.2.1")

    implementation("com.google.android.gms:play-services-location:21.0.1")

    implementation("org.osmdroid:osmdroid-android:6.1.18")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // 📷 CameraX
    val cameraxVersion = "1.3.0"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
    implementation("com.google.mlkit:face-detection:16.1.5")

    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // 🎙️ Offline wake word + STT (Vosk)
    implementation("com.alphacephei:vosk-android:0.3.75")


    // 🔥 Socket.io para rastreo en tiempo real
    implementation("io.socket:socket.io-client:2.1.0")


    // Pruebas Unitarias
    implementation ("androidx.appcompat:appcompat:1.6.1")
    implementation ("com.google.android.material:material:1.11.0")

    // ── Retrofit (para la prueba de API) ──────────────────────────────────
    implementation ("com.squareup.retrofit2:retrofit:2.9.0")
    implementation ("com.squareup.retrofit2:converter-gson:2.9.0")

    // ── PRUEBAS UNITARIAS (carpeta test) ──────────────────────────────────
    testImplementation ("junit:junit:4.13.2")
    testImplementation ("org.mockito:mockito-core:5.4.0")
    testImplementation ("com.squareup.okhttp3:mockwebserver:4.11.0")

    // ── PRUEBAS DE UI Espresso (carpeta androidTest) ──────────────────────
    androidTestImplementation ("androidx.test.ext:junit:1.1.5")
    androidTestImplementation ("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation ("androidx.test.espresso:espresso-contrib:3.5.1")
    androidTestImplementation ("androidx.test.espresso:espresso-intents:3.5.1")
    androidTestImplementation ("androidx.test:rules:1.5.0")
    androidTestImplementation ("androidx.test.uiautomator:uiautomator:2.2.0")
    androidTestImplementation ("org.hamcrest:hamcrest:2.2")

    // 🖼️ Glide — carga de imágenes
    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")

    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
}


