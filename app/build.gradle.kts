plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// 版本号随提交自动递增：每提交一次 +0.0.1，逢十进位（1.0.10 → 1.1.0，1.9.10 → 2.0.0）
val gitCommitCount: Int = runCatching {
    val process = ProcessBuilder("git", "rev-list", "--count", "HEAD")
        .directory(rootDir)
        .redirectErrorStream(true)
        .start()
    process.inputStream.bufferedReader().readText().trim().toInt()
}.getOrDefault(0)

fun versionNameFromCount(count: Int): String {
    val major = 1 + count / 100
    val minor = count % 100 / 10
    val patch = count % 10
    return "$major.$minor.$patch"
}

android {
    namespace = "com.micharger"
    compileSdk { version = release(37) } // Miuix 0.9.4 与 Compose 1.12 要求 compileSdk ≥ 37

    defaultConfig {
        applicationId = "com.micharger"
        minSdk = 28
        targetSdk = 36
        versionCode = if (gitCommitCount > 0) gitCommitCount else 1
        versionName = if (gitCommitCount > 0) versionNameFromCount(gitCommitCount) else "1.0.0"
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
