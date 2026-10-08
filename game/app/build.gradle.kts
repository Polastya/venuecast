plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace="com.polastya.bachao500"
    compileSdk=35
    defaultConfig {
        applicationId="com.polastya.bachao500"
        minSdk=26
        targetSdk=35
        versionCode=1
        versionName="1.0"
    }
    buildTypes {
        debug { applicationIdSuffix=".debug" }
        release { isMinifyEnabled=false }
    }
    compileOptions {
        sourceCompatibility=JavaVersion.VERSION_17
        targetCompatibility=JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget="17" }
}
dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-ktx:1.10.1")
}
