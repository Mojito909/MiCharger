plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.micharger"
    compileSdk { version = release(37) } // Miuix 0.9.4 与 Compose 1.12 要求 compileSdk ≥ 37

    defaultConfig {
        applicationId = "com.micharger"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // 开源项目先用 debug 签名保证可安装，正式发布时可换 CI 签名
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.miuix.ui)
    implementation(libs.miuix.preference)
    implementation(libs.activity.compose)
    implementation(libs.libsu.core)
    implementation(libs.datastore.preferences)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.coroutines.android)
    ksp(libs.room.compiler)
    testImplementation(libs.junit)
}
