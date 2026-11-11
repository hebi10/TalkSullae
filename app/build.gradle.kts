plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.hebi.talksullae"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.hebi.talksullae"
        buildConfigField("boolean", "FIREBASE_CONFIGURED", file("google-services.json").exists().toString())
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.3.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        val keyFile = System.getenv("TALK_PLAY_KEYSTORE_FILE")?.takeIf { it.isNotBlank() }
        val storePass = System.getenv("TALK_PLAY_STORE_PASSWORD")?.takeIf { it.isNotBlank() }
        val keyAlias = System.getenv("TALK_PLAY_KEY_ALIAS")?.takeIf { it.isNotBlank() }
        val keyPass = System.getenv("TALK_PLAY_KEY_PASSWORD")?.takeIf { it.isNotBlank() }
        if (keyFile != null && storePass != null && keyAlias != null && keyPass != null) {
            create("talkPlayUpload") {
                storeFile = file(keyFile)
                storePassword = storePass
                this.keyAlias = keyAlias
                keyPassword = keyPass
            }
        }
    }

    buildTypes {
        getByName("release") {
            isDebuggable = false
            isMinifyEnabled = false
            if (signingConfigs.names.contains("talkPlayUpload")) {
                signingConfig = signingConfigs.getByName("talkPlayUpload")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.10.01"))
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-functions")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.10.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// Firebase is activated only after a project-specific config is supplied outside Git.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}
