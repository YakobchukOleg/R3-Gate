plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.r3gate.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.r3gate.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("com.google.android.gms:play-services-auth:22.0.0")
    implementation("androidx.core:core:1.13.1")
    testImplementation("junit:junit:4.13.2")
}

