# MiCharger 充电管家 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为小米9（Android 13）构建一个 Miuix 风格的充电控制 App：主页显示实时电池/充电状态并支持 Root 充电控制（暂停/恢复/限流），含耗电排行、手机信息、充电历史曲线和设置页；通过 GitHub Actions 自动打包 APK。

**Architecture:** 单 Activity + Compose 状态切换四个 Tab（充电/耗电/信息/设置），无 ViewModel 库（状态用 `remember` + 轮询/Flow，Manifest 声明 `configChanges` 避免旋转重建）。数据层为单例仓库挂在 Application 上：BatteryRepository（BatteryManager + sysfs）、ChargingController（libsu 写 sysfs 节点）、AppUsageRepository（root dumpsys / UsageStatsManager 双模式）、SettingsRepository（DataStore）、HistoryRepository（Room）。充电守护为前台服务（specialUse）+ 开机自启 Receiver。

**Tech Stack:** Kotlin 2.4.20 / AGP 9.4.1（内置 Kotlin，无 kotlin-android 插件）/ Gradle 9.8.0 / Miuix 0.9.4 / CMP 1.12.1（Miuix 传递依赖）/ libsu 6.0.0 / Room 2.5.2 + KSP 2.3.12 / DataStore 1.1.7 / activity-compose 1.13.0 / minSdk 28 / compileSdk 36

**关键约束（来自 Miuix 0.9.4 源码级调研）：**
- `ThemeController` 属性只读，切换主题必须 `remember(mode) { ThemeController(mode) }` 重建
- 不使用 miuix-blur（要求 minSdk 33，本项目 minSdk 28）
- Overlay 系列组件（OverlaySpinnerPreference 等）必须位于 Scaffold 内
- `NavigationBarItem(selected, onClick, icon: ImageVector, label: String)` —— icon 是 ImageVector，需自建图标
- AGP 9 插件只写 `com.android.application` + `org.jetbrains.kotlin.plugin.compose`

---

## 文件结构总览

```
.github/workflows/build.yml        # CI 自动打包
.gitignore
README.md
LICENSE
settings.gradle.kts
build.gradle.kts
gradle.properties
gradle/libs.versions.toml
app/
  build.gradle.kts
  proguard-rules.pro               # 空占位（minify 关闭）
  src/main/
    AndroidManifest.xml
    res/values/strings.xml
    res/values/themes.xml
    res/drawable/ic_launcher.xml
    java/com/micharger/
      MiChargerApp.kt              # Application，持有全部仓库单例
      MainActivity.kt
      ui/theme/Theme.kt            # MiuixTheme 包装 + ColorSchemeMode 解析
      ui/icons/AppIcons.kt         # 4 个自建 ImageVector（Home/Bolt/Chart/Tune）
      ui/AppRoot.kt                # Tab 状态 + 底部导航 + 页面切换
      ui/home/HomeScreen.kt        # 电池卡 + 充电控制卡 + 历史曲线卡
      ui/usage/UsageScreen.kt      # 耗电排行
      ui/usage/UsageParser.kt      # dumpsys 解析（有单元测试）
      ui/info/InfoScreen.kt        # 手机/电池信息
      ui/settings/SettingsScreen.kt# 设置页
      data/battery/BatteryRepository.kt
      data/battery/SysFsReader.kt
      data/battery/ChargingNodeDetector.kt
      data/battery/ChargingController.kt
      data/usage/AppUsageRepository.kt
      data/history/BatterySample.kt
      data/history/SampleDao.kt
      data/history/HistoryDatabase.kt
      data/history/HistoryRepository.kt
      data/settings/SettingsRepository.kt
      service/ChargingGuardService.kt
      service/BootReceiver.kt
      util/RootChecker.kt
      util/Formatters.kt
  src/test/java/com/micharger/usage/UsageParserTest.kt
```

---

## Task 1: 环境检查 + Git 仓库 + Gradle 脚手架（空壳可构建）

**Files:**
- Create: `settings.gradle.kts`、`build.gradle.kts`、`gradle.properties`、`gradle/libs.versions.toml`、`.gitignore`
- Create: `app/build.gradle.kts`、`app/proguard-rules.pro`
- Create: `app/src/main/AndroidManifest.xml`、`res/values/strings.xml`、`res/values/themes.xml`、`res/drawable/ic_launcher.xml`
- Create: `app/src/main/java/com/micharger/MiChargerApp.kt`、`MainActivity.kt`

- [ ] **Step 1.1: 环境检查**

Run: `java -version 2>&1 | head -1`
Expected: 版本 ≥ 17（如 `openjdk version "21.x"`）。低于 17 则 `brew install openjdk@21` 并按 brew 提示设置 JAVA_HOME。

Run: `gradle -v 2>/dev/null | grep Gradle | head -1 || echo NO_GRADLE`
Expected: `Gradle 9.x`。若输出 `NO_GRADLE` 则 `brew install gradle`。

Run: `ls "$HOME/Library/Android/sdk/platform-tools/adb" 2>/dev/null || echo NO_SDK`
Expected: 路径存在则本地可构建；`NO_SDK` 则本地跳过构建、仅靠 CI（不影响后续任务提交代码）。

- [ ] **Step 1.2: 初始化 Git 仓库**

```bash
cd "/Users/admin/Documents/Github/xiaomi9 充电控制"
git init -b main
```
Expected: `Initialized empty Git repository`

- [ ] **Step 1.3: 写根构建文件**

创建 `settings.gradle.kts`：

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "MiCharger"
include(":app")
```

创建根 `build.gradle.kts`：

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
```

创建 `gradle.properties`：

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
```

创建 `gradle/libs.versions.toml`：

```toml
[versions]
agp = "9.4.1"
kotlin = "2.4.20"
miuix = "0.9.4"
libsu = "6.0.0"
activityCompose = "1.13.0"
room = "2.5.2"
ksp = "2.3.12"
datastore = "1.1.7"
coroutines = "1.10.2"
junit = "4.13.2"

[libraries]
miuix-ui = { module = "top.yukonga.miuix.kmp:miuix-ui", version.ref = "miuix" }
miuix-preference = { module = "top.yukonga.miuix.kmp:miuix-preference", version.ref = "miuix" }
libsu-core = { module = "com.github.topjohnwu.libsu:core", version.ref = "libsu" }
activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }
coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
junit = { module = "junit:junit", version.ref = "junit" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

创建 `.gitignore`：

```
.gradle/
build/
**/build/
local.properties
.idea/
*.iml
.DS_Store
.kotlin/
captures/
```

- [ ] **Step 1.4: 写 app 模块构建文件**

创建 `app/build.gradle.kts`：

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.micharger"
    compileSdk { version = release(36) }

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
    implementation(libs.coroutines.android)
    ksp(libs.room.compiler)
    testImplementation(libs.junit)
}
```

创建空文件 `app/proguard-rules.pro`（内容为空）。

- [ ] **Step 1.5: 写 Manifest 与资源**

创建 `app/src/main/AndroidManifest.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission
        android:name="android.permission.PACKAGE_USAGE_STATS"
        tools:ignore="ProtectedPermissions" />

    <application
        android:name=".MiChargerApp"
        android:icon="@drawable/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.MiCharger">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:configChanges="orientation|screenSize|keyboardHidden|uiMode|screenLayout|smallestScreenSize"
            android:enableOnBackInvokedCallback="true"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".service.ChargingGuardService"
            android:exported="false"
            android:foregroundServiceType="specialUse">
            <property
                android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
                android:value="charging_control_guard" />
        </service>

        <receiver
            android:name=".service.BootReceiver"
            android:exported="false">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
            </intent-filter>
        </receiver>
    </application>
</manifest>
```

注意：Manifest 引用了 `.service.ChargingGuardService` 和 `.service.BootReceiver`，它们在 Task 6 才创建，因此本任务构建用 `assembleDebug` 时会失败——**本任务先临时注释掉 Manifest 中 `<service>` 与 `<receiver>` 两段**（保留注释 `<!-- Task 6 启用 -->`），Task 6 再取消注释。

创建 `app/src/main/res/values/strings.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">充电管家</string>
</resources>
```

创建 `app/src/main/res/values/themes.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.MiCharger" parent="android:Theme.Material.Light.NoActionBar" />
</resources>
```

创建 `app/src/main/res/drawable/ic_launcher.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#FF6C2B"
        android:pathData="M0,0h108v108h-108z" />
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M60,22 L38,62 L52,62 L48,86 L70,46 L56,46 Z" />
</vector>
```

- [ ] **Step 1.6: 写 Application 与最小 MainActivity**

创建 `app/src/main/java/com/micharger/MiChargerApp.kt`：

```kotlin
package com.micharger

import android.app.Application

class MiChargerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: MiChargerApp
            private set
    }
}

// 各处快捷访问：context.app.xxx
val android.content.Context.app: MiChargerApp
    get() = applicationContext as MiChargerApp
```

创建 `app/src/main/java/com/micharger/MainActivity.kt`（最小版，Task 2 扩展）：

```kotlin
package com.micharger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MiuixTheme {
                Box(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
                    Text(text = "MiCharger")
                }
            }
        }
    }
}
```

- [ ] **Step 1.7: 生成 Gradle Wrapper**

```bash
cd "/Users/admin/Documents/Github/xiaomi9 充电控制"
gradle wrapper --gradle-version 9.8.0 --distribution-type bin
```
Expected: `BUILD SUCCESSFUL`，生成 `gradlew`、`gradle/wrapper/gradle-wrapper.properties`。

- [ ] **Step 1.8: 首次构建验证**

```bash
./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`。若 `miuix-ui` 等坐标解析失败，检查网络代理后重试（坐标为 Maven Central 的 `top.yukonga.miuix.kmp:miuix-ui:0.9.4`，Gradle 会通过 variant 元数据自动选择 `-android` 变体）。

- [ ] **Step 1.9: Commit**

```bash
git add -A
git commit -m "chore: 初始化 MiCharger Gradle 脚手架（AGP 9 + Miuix 0.9.4）"
```

---

## Task 2: Miuix 主题 + 底部导航 + 四个页面骨架

**Files:**
- Create: `ui/theme/Theme.kt`、`ui/icons/AppIcons.kt`、`ui/AppRoot.kt`
- Create: `ui/home/HomeScreen.kt`、`ui/usage/UsageScreen.kt`、`ui/info/InfoScreen.kt`、`ui/settings/SettingsScreen.kt`（均为骨架）
- Modify: `MainActivity.kt`

- [ ] **Step 2.1: 主题**

创建 `app/src/main/java/com/micharger/ui/theme/Theme.kt`：

```kotlin
package com.micharger.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.ThemeController

@Composable
fun MiChargerTheme(themeMode: ColorSchemeMode, content: @Composable () -> Unit) {
    // ThemeController 属性只读，模式变化时重建实例
    val controller = remember(themeMode) { ThemeController(colorSchemeMode = themeMode) }
    MiuixTheme(controller = controller, content = content)
}

fun parseThemeMode(raw: String?): ColorSchemeMode =
    runCatching { ColorSchemeMode.valueOf(raw ?: "System") }.getOrDefault(ColorSchemeMode.System)
```

- [ ] **Step 2.2: 自建图标（NavigationBarItem 需要 ImageVector）**

创建 `app/src/main/java/com/micharger/ui/icons/AppIcons.kt`：

```kotlin
package com.micharger.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

object AppIcons {

    val Home: ImageVector by lazy {
        ImageVector.Builder(
            name = "Home", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(10f, 20f)
                verticalLineToRelative(-6f)
                horizontalLineToRelative(4f)
                verticalLineToRelative(6f)
                horizontalLineToRelative(5f)
                verticalLineToRelative(-8f)
                horizontalLineToRelative(3f)
                lineTo(12f, 3f)
                lineTo(2f, 12f)
                horizontalLineToRelative(3f)
                verticalLineToRelative(8f)
                close()
            }
        }.build()
    }

    val Bolt: ImageVector by lazy {
        ImageVector.Builder(
            name = "Bolt", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(13f, 2f)
                lineTo(4.5f, 13.5f)
                lineTo(10.5f, 13.5f)
                lineTo(9f, 22f)
                lineTo(19.5f, 9.5f)
                lineTo(13.2f, 9.5f)
                lineTo(15f, 2f)
                close()
            }
        }.build()
    }

    val Chart: ImageVector by lazy {
        ImageVector.Builder(
            name = "Chart", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(9f, 17f)
                horizontalLineTo(7f)
                verticalLineToRelative(-7f)
                horizontalLineToRelative(2f)
                close()
                moveTo(13f, 17f)
                horizontalLineTo(11f)
                verticalLineTo(7f)
                horizontalLineToRelative(2f)
                close()
                moveTo(17f, 17f)
                horizontalLineTo(15f)
                verticalLineTo(13f)
                horizontalLineToRelative(2f)
                close()
            }
        }.build()
    }

    val Tune: ImageVector by lazy {
        ImageVector.Builder(
            name = "Tune", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(3f, 17f); verticalLineToRelative(2f); horizontalLineToRelative(6f)
                verticalLineToRelative(-2f); horizontalLineTo(3f); close()
                moveTo(3f, 5f); verticalLineToRelative(2f); horizontalLineToRelative(10f)
                verticalLineTo(5f); horizontalLineTo(3f); close()
                moveTo(13f, 21f); verticalLineToRelative(-2f); horizontalLineToRelative(8f)
                verticalLineToRelative(-2f); horizontalLineToRelative(-8f); verticalLineToRelative(-2f)
                horizontalLineToRelative(-2f); verticalLineToRelative(6f); horizontalLineToRelative(2f); close()
                moveTo(7f, 9f); verticalLineToRelative(2f); horizontalLineTo(3f); verticalLineToRelative(2f)
                horizontalLineToRelative(4f); verticalLineToRelative(2f); horizontalLineToRelative(2f)
                verticalLineTo(9f); horizontalLineTo(7f); close()
                moveTo(21f, 13f); verticalLineToRelative(-2f); horizontalLineTo(11f); verticalLineToRelative(2f)
                horizontalLineToRelative(10f); close()
                moveTo(15f, 9f); horizontalLineToRelative(2f); verticalLineTo(7f); horizontalLineToRelative(4f)
                verticalLineTo(5f); horizontalLineToRelative(-4f); verticalLineTo(3f); horizontalLineToRelative(-2f)
                verticalLineToRelative(6f); close()
            }
        }.build()
    }
}
```

- [ ] **Step 2.3: 四个页面骨架**

创建 `app/src/main/java/com/micharger/ui/home/HomeScreen.kt`：

```kotlin
package com.micharger.ui.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar

@Composable
fun HomeScreen() {
    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        topBar = { TopAppBar(title = "充电", largeTitle = "充电管家", scrollBehavior = scrollBehavior) },
    ) { padding ->
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(text = "主页骨架")
        }
    }
}
```

创建 `app/src/main/java/com/micharger/ui/usage/UsageScreen.kt`：

```kotlin
package com.micharger.ui.usage

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar

@Composable
fun UsageScreen() {
    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        topBar = { TopAppBar(title = "耗电排行", scrollBehavior = scrollBehavior) },
    ) { padding ->
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(text = "耗电排行骨架")
        }
    }
}
```

创建 `app/src/main/java/com/micharger/ui/info/InfoScreen.kt`：

```kotlin
package com.micharger.ui.info

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar

@Composable
fun InfoScreen() {
    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        topBar = { TopAppBar(title = "信息", scrollBehavior = scrollBehavior) },
    ) { padding ->
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(text = "信息骨架")
        }
    }
}
```

创建 `app/src/main/java/com/micharger/ui/settings/SettingsScreen.kt`（空骨架，Task 5 填充）：

```kotlin
package com.micharger.ui.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar

@Composable
fun SettingsScreen() {
    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        topBar = { TopAppBar(title = "设置", scrollBehavior = scrollBehavior) },
    ) { padding ->
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(text = "设置骨架")
        }
    }
}
```

- [ ] **Step 2.4: AppRoot + 底部导航**

创建 `app/src/main/java/com/micharger/ui/AppRoot.kt`：

```kotlin
package com.micharger.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import com.micharger.ui.home.HomeScreen
import com.micharger.ui.icons.AppIcons
import com.micharger.ui.info.InfoScreen
import com.micharger.ui.settings.SettingsScreen
import com.micharger.ui.usage.UsageScreen

private data class Tab(val label: String, val icon: ImageVector)

@Composable
fun AppRoot() {
    var tab by rememberSaveable { mutableStateOf(0) }
    val tabs = listOf(
        Tab("充电", AppIcons.Home),
        Tab("耗电", AppIcons.Bolt),
        Tab("信息", AppIcons.Chart),
        Tab("设置", AppIcons.Tune),
    )
    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = item.icon,
                        label = item.label,
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
            when (tab) {
                0 -> HomeScreen()
                1 -> UsageScreen()
                2 -> InfoScreen()
                else -> SettingsScreen()
            }
        }
    }
}
```

- [ ] **Step 2.5: 改造 MainActivity 接入 AppRoot**

将 `MainActivity.kt` 的 `setContent` 块替换为：

```kotlin
setContent {
    MiChargerTheme(themeMode = ColorSchemeMode.System) {
        AppRoot()
    }
}
```

并把 import 区块改为：

```kotlin
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.micharger.ui.AppRoot
import com.micharger.ui.theme.MiChargerTheme
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
```

（删除原有的 Box/Text/MiuixTheme import。）

- [ ] **Step 2.6: 构建验证**

```bash
./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`。若 `nestedScrollConnection` 报红，检查该属性在 `top.yukonga.miuix.kmp.basic.ScrollBehavior` 上的实际名称（Miuix 的 ExitUntilCollapsedScrollBehavior 与 m3 同名），必要时改为不传 `scrollBehavior` 的静态 TopAppBar。

- [ ] **Step 2.7: Commit**

```bash
git add -A
git commit -m "feat: Miuix 主题 + 底部四 Tab 导航骨架"
```

---

## Task 3: 电池数据仓库 + 主页电池状态卡

**Files:**
- Create: `data/battery/SysFsReader.kt`、`data/battery/BatteryRepository.kt`、`util/Formatters.kt`
- Modify: `MiChargerApp.kt`、`ui/home/HomeScreen.kt`

- [ ] **Step 3.1: SysFsReader**

创建 `app/src/main/java/com/micharger/data/battery/SysFsReader.kt`：

```kotlin
package com.micharger.data.battery

import com.topjohnwu.superuser.Shell
import java.io.File

class SysFsReader {

    /** 读节点：先试直接读，失败走 shell cat（部分节点需 root 才可见内容） */
    fun read(path: String): String? {
        val file = File(path)
        if (file.canRead()) {
            runCatching { file.readText().trim() }
                .getOrNull()
                ?.takeIf { it.isNotEmpty() }
                ?.let { return it }
        }
        val result = Shell.cmd("cat '$path'").exec()
        val line = result.out.firstOrNull()?.trim()
        return line?.takeIf { it.isNotEmpty() && !it.startsWith("cat:") && !it.contains("Permission denied") }
    }

    /** 写节点（需 root），返回是否成功 */
    fun write(path: String, value: String): Boolean =
        Shell.cmd("echo '$value' > '$path'").exec().isSuccess
}
```

- [ ] **Step 3.2: BatteryRepository**

创建 `app/src/main/java/com/micharger/data/battery/BatteryRepository.kt`：

```kotlin
package com.micharger.data.battery

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlin.math.abs

data class BatteryInfo(
    val levelPct: Int,
    val charging: Boolean,
    val voltageMv: Int?,
    val currentMa: Double?,
    val powerW: Double?,
    val tempTenths: Int?,
    val chargeCounterMah: Int?,
    val timestamp: Long = System.currentTimeMillis(),
)

class BatteryRepository(private val context: Context) {

    private val batteryManager by lazy {
        context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    }

    fun snapshot(): BatteryInfo {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val voltageMv = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)?.takeIf { it > 0 }
        val temp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            ?.takeIf { it != Int.MIN_VALUE }

        val currentUa = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        var currentMa = if (currentUa != Int.MIN_VALUE && currentUa != 0) currentUa / 1000.0 else null
        if (currentMa == null) {
            // sysfs 兜底（单位 µA）
            val sysCurrent = SysFsReader().read("/sys/class/power_supply/battery/current_now")
            currentMa = sysCurrent?.toDoubleOrNull()?.div(1000.0)
        }

        val counterUah = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val counterMah = if (counterUah != Int.MIN_VALUE) counterUah / 1000 else null

        val powerW = if (voltageMv != null && currentMa != null) {
            abs(voltageMv / 1000.0 * currentMa) / 1000.0
        } else null

        val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else 0
        return BatteryInfo(
            levelPct = pct,
            charging = charging,
            voltageMv = voltageMv,
            currentMa = currentMa,
            powerW = powerW,
            tempTenths = temp,
            chargeCounterMah = counterMah,
        )
    }
}
```

- [ ] **Step 3.3: Formatters**

创建 `app/src/main/java/com/micharger/util/Formatters.kt`：

```kotlin
package com.micharger.util

import java.util.Locale

object Formatters {

    fun temp(tenths: Int?): String =
        tenths?.let { String.format(Locale.US, "%.1f ℃", it / 10.0) } ?: "--"

    fun current(ma: Double?): String =
        ma?.let { String.format(Locale.US, "%.0f mA", it) } ?: "--"

    fun voltage(mv: Int?): String =
        mv?.let { String.format(Locale.US, "%.3f V", it / 1000.0) } ?: "--"

    fun power(w: Double?): String =
        w?.let { String.format(Locale.US, "%.2f W", it) } ?: "--"

    fun capacity(mah: Int?): String =
        mah?.let { String.format(Locale.US, "%d mAh", it) } ?: "--"

    fun duration(ms: Long): String {
        val totalMin = ms / 60000
        val h = totalMin / 60
        val m = totalMin % 60
        return when {
            h > 0 -> "${h}小时${m}分"
            else -> "${m}分钟"
        }
    }

    fun hhmm(ts: Long): String =
        String.format(
            Locale.US, "%02d:%02d",
            android.text.format.DateFormat.Format("HH", java.util.Date(ts)).toIntOrNull() ?: 0,
        )
}
```

（`hhmm` 若编译报错，直接改用 `SimpleDateFormat("HH:mm", Locale.US).format(java.util.Date(ts))`。）

- [ ] **Step 3.4: 挂到 Application**

修改 `MiChargerApp.kt` 为：

```kotlin
package com.micharger

import android.app.Application
import com.micharger.data.battery.BatteryRepository

class MiChargerApp : Application() {

    lateinit var batteryRepository: BatteryRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        batteryRepository = BatteryRepository(this)
    }

    companion object {
        lateinit var instance: MiChargerApp
            private set
    }
}

val android.content.Context.app: MiChargerApp
    get() = applicationContext as MiChargerApp
```

- [ ] **Step 3.5: 主页电池卡**

在 `HomeScreen.kt` 的 `HomeScreen()` 中，把 `Text(text = "主页骨架")` 一行替换为电池卡内容，并在文件头部补充以下 import 与状态逻辑：

新增 import：

```kotlin
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.micharger.app
import com.micharger.data.battery.BatteryInfo
import com.micharger.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.theme.MiuixTheme
```

`HomeScreen()` 函数体替换为：

```kotlin
@Composable
fun HomeScreen() {
    val scrollBehavior = MiuixScrollBehavior()
    var battery by remember { mutableStateOf<BatteryInfo?>(null) }

    LaunchedEffect(Unit) {
        val repo = com.micharger.app.batteryRepository
        while (true) {
            battery = withContext(Dispatchers.IO) { repo.snapshot() }
            delay(2000)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = "充电", largeTitle = "充电管家", scrollBehavior = scrollBehavior) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState()),
        ) {
            battery?.let { info ->
                BatteryCard(info = info)
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun BatteryCard(info: BatteryInfo) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row {
                Text(
                    text = "${info.levelPct}%",
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (info.charging) MiuixTheme.colorScheme.primary
                            else MiuixTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = if (info.charging) "充电中" else "未充电",
                    fontSize = 16.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            InfoRow("电流", Formatters.current(info.currentMa))
            InfoRow("电压", Formatters.voltage(info.voltageMv))
            InfoRow("功率", Formatters.power(info.powerW))
            InfoRow("温度", Formatters.temp(info.tempTenths))
            InfoRow("剩余容量", Formatters.capacity(info.chargeCounterMah))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(text = label, fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Spacer(modifier = Modifier.weight(1f))
        Text(text = value, fontSize = 14.sp)
    }
}
```

- [ ] **Step 3.6: 构建验证**

```bash
./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3.7: Commit**

```bash
git add -A
git commit -m "feat: 电池实时状态卡（电流/电压/功率/温度）"
```

---

## Task 4: Root 检测 + sysfs 充电控制 + 主页控制卡

**Files:**
- Create: `util/RootChecker.kt`、`data/battery/ChargingNodeDetector.kt`、`data/battery/ChargingController.kt`
- Modify: `MiChargerApp.kt`、`ui/home/HomeScreen.kt`

- [ ] **Step 4.1: RootChecker**

创建 `app/src/main/java/com/micharger/util/RootChecker.kt`：

```kotlin
package com.micharger.util

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object RootChecker {
    /** 无 root 设备不会弹授权，直接返回 false；有 root 首次调用会触发 Magisk 授权弹窗 */
    suspend fun check(): Boolean = withContext(Dispatchers.IO) {
        Shell.getShell()
        Shell.isAppGrantedRoot() == true
    }
}
```

- [ ] **Step 4.2: 节点探测器**

创建 `app/src/main/java/com/micharger/data/battery/ChargingNodeDetector.kt`：

```kotlin
package com.micharger.data.battery

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ChargingNodes(
    val inputSuspendNode: String?,   // 写 1 暂停充电 / 0 恢复（优先）
    val enableNode: String?,         // 写 1 开启 / 0 关闭（降级方案）
    val currentMaxNode: String?,     // 限流 mA
)

class ChargingNodeDetector(private val reader: SysFsReader) {

    suspend fun detect(): ChargingNodes = withContext(Dispatchers.IO) {
        val base = "/sys/class/power_supply/battery"
        ChargingNodes(
            inputSuspendNode = detectNode("$base/input_suspend"),
            enableNode = detectNode("$base/battery_charging_enabled")
                ?: detectNode("$base/charging_enabled"),
            currentMaxNode = detectNode("$base/constant_charge_current_max"),
        )
    }

    /** 用「读出当前值并原样写回」验证节点可写（原样写回无副作用） */
    private fun detectNode(path: String): String? {
        val current = reader.read(path)?.toIntOrNull() ?: return null
        return if (reader.write(path, current.toString())) path else null
    }
}
```

- [ ] **Step 4.3: 充电控制器**

创建 `app/src/main/java/com/micharger/data/battery/ChargingController.kt`：

```kotlin
package com.micharger.data.battery

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChargingController(
    private val reader: SysFsReader,
    private val detector: ChargingNodeDetector,
) {

    var nodes: ChargingNodes? = null
        private set
    private var originalCurrent: Int? = null

    suspend fun initialize(): ChargingNodes? = withContext(Dispatchers.IO) {
        nodes = detector.detect()
        nodes?.currentMaxNode
            ?.let { reader.read(it)?.toIntOrNull() }
            ?.let { originalCurrent = it }
        nodes
    }

    /** 是否处于暂停充电状态；null 表示无法判断 */
    suspend fun isSuspended(): Boolean? = withContext(Dispatchers.IO) {
        val n = nodes ?: return@withContext null
        when {
            n.inputSuspendNode != null -> reader.read(n.inputSuspendNode)?.toIntOrNull() == 1
            n.enableNode != null -> reader.read(n.enableNode)?.toIntOrNull() == 0
            else -> null
        }
    }

    suspend fun pause(): Boolean = withContext(Dispatchers.IO) {
        val n = nodes ?: return@withContext false
        when {
            n.inputSuspendNode != null -> reader.write(n.inputSuspendNode, "1")
            n.enableNode != null -> reader.write(n.enableNode, "0")
            else -> false
        }
    }

    suspend fun resume(): Boolean = withContext(Dispatchers.IO) {
        val n = nodes ?: return@withContext false
        when {
            n.inputSuspendNode != null -> reader.write(n.inputSuspendNode, "0")
            n.enableNode != null -> reader.write(n.enableNode, "1")
            else -> false
        }
    }

    suspend fun setCurrentLimit(ma: Int): Boolean = withContext(Dispatchers.IO) {
        val path = nodes?.currentMaxNode ?: return@withContext false
        reader.write(path, ma.toString())
    }

    suspend fun clearCurrentLimit(): Boolean = withContext(Dispatchers.IO) {
        val path = nodes?.currentMaxNode ?: return@withContext false
        val original = originalCurrent ?: return@withContext false
        reader.write(path, original.toString())
    }
}
```

- [ ] **Step 4.4: 挂到 Application**

`MiChargerApp.onCreate()` 中 `batteryRepository` 赋值之后新增：

```kotlin
chargingController = ChargingController(SysFsReader(), ChargingNodeDetector(SysFsReader()))
```

类体新增字段：

```kotlin
lateinit var chargingController: ChargingController
    private set
```

并补充 import：

```kotlin
import com.micharger.data.battery.ChargingController
import com.micharger.data.battery.ChargingNodeDetector
import com.micharger.data.battery.SysFsReader
```

- [ ] **Step 4.5: 主页充电控制卡**

在 `HomeScreen.kt` 中新增 import：

```kotlin
import com.micharger.data.battery.ChargingNodes
import com.micharger.util.RootChecker
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextButton
```

在 `HomeScreen()` 的 `battery?.let { ... }` 块之后、`Column` 收尾前新增状态与控制卡。先在 `var battery ...` 下方加状态：

```kotlin
var rootGranted by remember { mutableStateOf<Boolean?>(null) }
var nodes by remember { mutableStateOf<ChargingNodes?>(null) }
var suspended by remember { mutableStateOf<Boolean?>(null) }
var limitMa by remember { mutableStateOf(1500f) }
var limitBusy by remember { mutableStateOf(false) }
```

在 `LaunchedEffect(Unit)` 电池轮询之外再加一个：

```kotlin
LaunchedEffect(Unit) {
    val controller = com.micharger.app.chargingController
    rootGranted = RootChecker.check()
    if (rootGranted == true) {
        nodes = controller.initialize()
        suspended = controller.isSuspended()
    }
}
```

`battery?.let { ... }` 块后新增：

```kotlin
ChargingControlCard(
    rootGranted = rootGranted,
    nodes = nodes,
    suspended = suspended,
    limitMa = limitMa,
    limitBusy = limitBusy,
    onPauseResume = {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val controller = com.micharger.app.chargingController
            val ok = if (suspended == true) controller.resume() else controller.pause()
            if (ok) suspended = controller.isSuspended()
        }
    },
    onApplyLimit = {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            limitBusy = true
            com.micharger.app.chargingController.setCurrentLimit(limitMa.toInt())
            limitBusy = false
        }
    },
    onClearLimit = {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            limitBusy = true
            com.micharger.app.chargingController.clearCurrentLimit()
            limitBusy = false
        }
    },
)
```

文件底部追加两个组件（import 补 `kotlinx.coroutines.launch`）：

```kotlin
@Composable
private fun ChargingControlCard(
    rootGranted: Boolean?,
    nodes: ChargingNodes?,
    suspended: Boolean?,
    limitMa: Float,
    limitBusy: Boolean,
    onPauseResume: () -> Unit,
    onApplyLimit: () -> Unit,
    onClearLimit: () -> Unit,
) {
    SmallTitle(text = "充电控制")
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            when {
                rootGranted == null -> Text("正在检测 Root…", fontSize = 14.sp)
                rootGranted == false -> Text(
                    "未获取 Root 权限，无法控制充电。\n请用 Magisk 授权后重试。",
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                nodes != null -> {
                    Text(
                        text = when (suspended) {
                            true -> "已暂停充电（保住当前电量）"
                            false -> "充电进行中"
                            null -> "充电状态未知"
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = onPauseResume, modifier = Modifier.fillMaxWidth()) {
                        Text(if (suspended == true) "恢复充电" else "立即暂停")
                    }
                    if (nodes.currentMaxNode != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("充电限流：${limitMa.toInt()} mA", fontSize = 14.sp)
                        Slider(
                            value = limitMa,
                            onValueChange = { limitMa = it },
                            valueRange = 300f..3300f,
                        )
                        Row {
                            TextButton(text = "应用限流", onClick = onApplyLimit, enabled = !limitBusy)
                            Spacer(modifier = Modifier.weight(1f))
                            TextButton(text = "恢复默认", onClick = onClearLimit, enabled = !limitBusy)
                        }
                    }
                }
                else -> Text("未找到可用的充电控制节点", fontSize = 14.sp)
            }
        }
    }
}
```

注意：`Slider` 的 `onValueChange` 直接改 `limitMa`，Slider 来自 `top.yukonga.miuix.kmp.basic`。上面 lambda 里的 `CoroutineScope(...).launch` 需要 import `kotlinx.coroutines.launch`；更推荐改用 `rememberCoroutineScope()`，但为保持步骤独立先用显式 scope（编译无警告问题）。

- [ ] **Step 4.6: 构建验证**

```bash
./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4.7: Commit**

```bash
git add -A
git commit -m "feat: Root 充电控制（暂停/恢复/限流，多节点探测降级）"
```

---

## Task 5: DataStore 设置仓库 + 设置页

**Files:**
- Create: `data/settings/SettingsRepository.kt`
- Modify: `MiChargerApp.kt`、`MainActivity.kt`、`ui/settings/SettingsScreen.kt`

- [ ] **Step 5.1: SettingsRepository**

创建 `app/src/main/java/com/micharger/data/settings/SettingsRepository.kt`：

```kotlin
package com.micharger.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    val targetSoc: Int = 80,
    val resumeSoc: Int = 75,
    val themeMode: String = "System",
    val sampleMinutes: Int = 5,
    val guardEnabled: Boolean = false,
    val bootStart: Boolean = false,
)

class SettingsRepository(private val context: Context) {

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            targetSoc = p[KEY_TARGET] ?: 80,
            resumeSoc = p[KEY_RESUME] ?: 75,
            themeMode = p[KEY_THEME] ?: "System",
            sampleMinutes = p[KEY_SAMPLE] ?: 5,
            guardEnabled = p[KEY_GUARD] ?: false,
            bootStart = p[KEY_BOOT] ?: false,
        )
    }

    suspend fun settingsOnce(): AppSettings = settingsFlow.first()

    suspend fun setTargetSoc(v: Int) = context.dataStore.edit { it[KEY_TARGET] = v }
    suspend fun setResumeSoc(v: Int) = context.dataStore.edit { it[KEY_RESUME] = v }
    suspend fun setThemeMode(v: String) = context.dataStore.edit { it[KEY_THEME] = v }
    suspend fun setSampleMinutes(v: Int) = context.dataStore.edit { it[KEY_SAMPLE] = v }
    suspend fun setGuardEnabled(v: Boolean) = context.dataStore.edit { it[KEY_GUARD] = v }
    suspend fun setBootStart(v: Boolean) = context.dataStore.edit { it[KEY_BOOT] = v }

    private companion object {
        val KEY_TARGET = intPreferencesKey("target_soc")
        val KEY_RESUME = intPreferencesKey("resume_soc")
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_SAMPLE = intPreferencesKey("sample_minutes")
        val KEY_GUARD = booleanPreferencesKey("guard_enabled")
        val KEY_BOOT = booleanPreferencesKey("boot_start")
    }
}
```

- [ ] **Step 5.2: 挂到 Application**

`MiChargerApp` 类体新增：

```kotlin
lateinit var settingsRepository: SettingsRepository
    private set
```

`onCreate()` 中 `instance = this` 后新增：

```kotlin
settingsRepository = SettingsRepository(this)
```

import 补 `com.micharger.data.settings.SettingsRepository`。

- [ ] **Step 5.3: MainActivity 接入主题设置**

`setContent` 改为（import 补 `androidx.compose.runtime.collectAsState`、`androidx.compose.runtime.getValue`、`com.micharger.data.settings.AppSettings`、`com.micharger.ui.theme.parseThemeMode`）：

```kotlin
setContent {
    val settings by app.settingsRepository.settingsFlow.collectAsState(initial = AppSettings())
    MiChargerTheme(themeMode = parseThemeMode(settings.themeMode)) {
        AppRoot()
    }
}
```

并在类体外/内加私有属性 `private val app: MiChargerApp get() = application as MiChargerApp`，或直接在 setContent 中用 `(application as MiChargerApp).settingsRepository`。

- [ ] **Step 5.4: 设置页 UI**

将 `SettingsScreen.kt` 整体替换为：

```kotlin
package com.micharger.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import com.micharger.app
import com.micharger.data.settings.AppSettings
import com.micharger.service.ChargingGuardService
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

private val THEME_ITEMS = listOf("跟随系统" to "System", "浅色" to "Light", "深色" to "Dark")
private val SAMPLE_ITEMS = listOf(1, 5, 15, 30)

@Composable
fun SettingsScreen() {
    val scrollBehavior = MiuixScrollBehavior()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by context.app.settingsRepository.settingsFlow.collectAsState(initial = AppSettings())

    // 本地拖动状态：松手才写 DataStore
    var targetSoc by remember(settings.targetSoc) { mutableStateOf(settings.targetSoc.toFloat()) }
    var resumeSoc by remember(settings.resumeSoc) { mutableStateOf(settings.resumeSoc.toFloat()) }

    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            context.app.settingsRepository.setGuardEnabled(true)
            ChargingGuardService.start(context)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = "设置", scrollBehavior = scrollBehavior) },
    ) { padding ->
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState()),
        ) {
            SmallTitle(text = "充电守护")
            SwitchPreference(
                checked = settings.guardEnabled,
                onCheckedChange = { checked ->
                    if (checked && Build.VERSION.SDK_INT >= 33) {
                        notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        context.app.settingsRepository.setGuardEnabled(checked)
                        if (checked) ChargingGuardService.start(context) else ChargingGuardService.stop(context)
                    }
                },
                title = "充电守护服务",
                summary = "达到目标电量自动暂停充电，回落到恢复阈值继续充电",
            )
            SwitchPreference(
                checked = settings.bootStart,
                onCheckedChange = { checked ->
                    context.app.settingsRepository.setBootStart(checked)
                },
                title = "开机自启",
                summary = "开机后自动启动充电守护（需守护服务开启）",
            )
            SliderPreference(
                value = targetSoc,
                onValueChange = { targetSoc = it },
                onValueChangeFinished = {
                    scope.launch { context.app.settingsRepository.setTargetSoc(targetSoc.toInt()) }
                },
                title = "目标电量",
                valueText = "${targetSoc.toInt()} %",
                valueRange = 50f..95f,
            )
            SliderPreference(
                value = resumeSoc,
                onValueChange = { resumeSoc = it },
                onValueChangeFinished = {
                    scope.launch { context.app.settingsRepository.setResumeSoc(resumeSoc.toInt()) }
                },
                title = "恢复阈值",
                valueText = "${resumeSoc.toInt()} %",
                valueRange = 30f..(targetSoc - 5f),
                summary = "低于此电量时恢复充电",
            )

            SmallTitle(text = "历史记录")
            OverlaySpinnerPreference(
                items = SAMPLE_ITEMS.map { DropdownItem(text = "$it 分钟") },
                selectedIndex = SAMPLE_ITEMS.indexOf(settings.sampleMinutes).coerceAtLeast(0),
                title = "采样间隔",
                summary = "充电守护运行时按此间隔记录电量",
                onSelectedIndexChange = { index ->
                    scope.launch { context.app.settingsRepository.setSampleMinutes(SAMPLE_ITEMS[index]) }
                },
            )

            SmallTitle(text = "外观")
            OverlaySpinnerPreference(
                items = THEME_ITEMS.map { DropdownItem(text = it.first) },
                selectedIndex = THEME_ITEMS.map { it.second }.indexOf(settings.themeMode).coerceAtLeast(0),
                title = "主题模式",
                onSelectedIndexChange = { index ->
                    scope.launch { context.app.settingsRepository.setThemeMode(THEME_ITEMS[index].second) }
                },
            )

            SmallTitle(text = "其它")
            top.yukonga.miuix.kmp.preference.ArrowPreference? // 占位将在下一步移除
        }
    }
}
```

**注意：上面最后一行 `ArrowPreference?` 是非法占位，必须删除。** 最终 `SmallTitle(text = "其它")` 分支替换为版本信息卡：

```kotlin
            SmallTitle(text = "关于")
            top.yukonga.miuix.kmp.basic.Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                androidx.compose.foundation.layout.Column(modifier = Modifier.padding(16.dp)) {
                    AboutRow("应用", "MiCharger 充电管家")
                    AboutRow("版本", versionName(context))
                }
            }
        }
    }
}

private fun versionName(context: Context): String =
    runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "--"
    }.getOrDefault("--")

@Composable
private fun AboutRow(label: String, value: String) {
    androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        top.yukonga.miuix.kmp.basic.Text(label, fontSize = 14.sp)
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
        top.yukonga.miuix.kmp.basic.Text(value, fontSize = 14.sp)
    }
}
```

并把以上两段合并成一个文件（import 需补 `androidx.compose.ui.unit.sp`；去掉对 ArrowPreference 的引用）。同时删除未用的 import（如 `Settings`、`Intent` 若未使用）。

- [ ] **Step 5.5: 构建验证**

```bash
./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`。若 `DropdownItem(text = ...)` 构造器报错，用 `DropdownItem(text = "...", selected = false, onClick = null)` 的完整参数形式核对实际签名。

- [ ] **Step 5.6: Commit**

```bash
git add -A
git commit -m "feat: 设置页（守护/阈值/采样/主题，DataStore 持久化）"
```

---

## Task 6: 充电守护前台服务 + 开机自启

**Files:**
- Create: `service/ChargingGuardService.kt`、`service/BootReceiver.kt`
- Modify: `AndroidManifest.xml`（取消注释 service/receiver）

- [ ] **Step 6.1: 前台服务**

创建 `app/src/main/java/com/micharger/service/ChargingGuardService.kt`：

```kotlin
package com.micharger.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.micharger.R
import com.micharger.app
import com.micharger.data.battery.BatterySample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ChargingGuardService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForegroundCompat(buildNotification("充电守护运行中"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch { guardLoop() }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun guardLoop() {
        val app = app
        val history = app.historyRepository
        while (isActive) {
            val settings = app.settingsRepository.settingsOnce()
            if (!settings.guardEnabled) break

            val info = app.batteryRepository.snapshot()
            val controller = app.chargingController

            // 首次循环若未初始化节点则尝试初始化
            if (controller.nodes == null) controller.initialize()

            val isSuspended = controller.isSuspended()
            when {
                isSuspended == false && info.levelPct >= settings.targetSoc ->
                    controller.pause()
                isSuspended == true && info.levelPct <= settings.resumeSoc ->
                    controller.resume()
            }

            // 历史采样
            runCatching {
                history.insert(
                    BatterySample(
                        timestamp = info.timestamp,
                        level = info.levelPct,
                        charging = info.charging,
                    ),
                )
                history.trim()
            }

            updateNotification("电量 ${info.levelPct}% · " + if (info.charging) "充电中" else "未充电")
            delay(settings.sampleMinutes * 60_000L)
        }
        stopSelf()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "充电守护",
            NotificationManager.IMPORTANCE_LOW,
        )
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("充电管家")
            .setContentText(text)
            .setOngoing(true)
            .build()

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    companion object {
        private const val CHANNEL_ID = "charging_guard"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            context.startForegroundService(Intent(context, ChargingGuardService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ChargingGuardService::class.java))
        }
    }
}
```

注意：上面引用了 `app.historyRepository` 和 `BatterySample`，它们在 Task 7 创建。**本任务先让 `guardLoop()` 中"历史采样"的 `runCatching { history.insert... }` 两行保持注释状态**（`// Task 7 启用`），Task 7 再取消。`val history = app.historyRepository` 一行同样先注释。

- [ ] **Step 6.2: BootReceiver**

创建 `app/src/main/java/com/micharger/service/BootReceiver.kt`：

```kotlin
package com.micharger.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.micharger.app
import kotlinx.coroutines.runBlocking

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val settings = runBlocking { context.app.settingsRepository.settingsOnce() }
        if (settings.bootStart && settings.guardEnabled) {
            ChargingGuardService.start(context)
        }
    }
}
```

- [ ] **Step 6.3: 启用 Manifest 声明**

取消 Task 1 中 `AndroidManifest.xml` 内 `<service>` 与 `<receiver>` 两段的注释。

- [ ] **Step 6.4: 构建验证**

```bash
./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6.5: Commit**

```bash
git add -A
git commit -m "feat: 充电守护前台服务 + 开机自启"
```

---

## Task 7: Room 充电历史 + 曲线图

**Files:**
- Create: `data/history/BatterySample.kt`、`SampleDao.kt`、`HistoryDatabase.kt`、`HistoryRepository.kt`
- Modify: `MiChargerApp.kt`、`service/ChargingGuardService.kt`（启用采样）、`ui/home/HomeScreen.kt`（历史卡）

- [ ] **Step 7.1: Room 三件套**

创建 `app/src/main/java/com/micharger/data/history/BatterySample.kt`：

```kotlin
package com.micharger.data.history

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "battery_samples")
data class BatterySample(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val level: Int,      // 0-100
    val charging: Boolean,
)
```

创建 `app/src/main/java/com/micharger/data/history/SampleDao.kt`：

```kotlin
package com.micharger.data.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SampleDao {
    @Insert
    suspend fun insert(sample: BatterySample)

    @Query("SELECT * FROM battery_samples WHERE timestamp >= :since ORDER BY timestamp ASC")
    suspend fun since(since: Long): List<BatterySample>

    @Query("DELETE FROM battery_samples WHERE timestamp < :before")
    suspend fun trim(before: Long)
}
```

创建 `app/src/main/java/com/micharger/data/history/HistoryDatabase.kt`：

```kotlin
package com.micharger.data.history

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [BatterySample::class], version = 1, exportSchema = false)
abstract class HistoryDatabase : RoomDatabase() {
    abstract fun sampleDao(): SampleDao
}
```

创建 `app/src/main/java/com/micharger/data/history/HistoryRepository.kt`：

```kotlin
package com.micharger.data.history

import android.content.Context
import androidx.room.Room

class HistoryRepository(context: Context) {
    private val dao = Room.databaseBuilder(
        context.applicationContext,
        HistoryDatabase::class.java,
        "micharger_history.db",
    ).build().sampleDao()

    suspend fun insert(sample: BatterySample) = dao.insert(sample)

    suspend fun last24h(): List<BatterySample> =
        dao.since(System.currentTimeMillis() - 24L * 3600 * 1000)

    suspend fun trim() =
        dao.trim(System.currentTimeMillis() - 7L * 24 * 3600 * 1000)
}
```

- [ ] **Step 7.2: 挂到 Application 并启用服务采样**

`MiChargerApp` 类体新增：

```kotlin
lateinit var historyRepository: HistoryRepository
    private set
```

`onCreate()` 中新增：

```kotlin
historyRepository = HistoryRepository(this)
```

import 补 `com.micharger.data.history.HistoryRepository`。

取消 Task 6 中 `ChargingGuardService.guardLoop()` 的三行注释（`val history = app.historyRepository` 与 `runCatching { history.insert(...) ... }` 块）。

- [ ] **Step 7.3: 主页历史曲线卡**

`HomeScreen.kt` 新增 import：

```kotlin
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import com.micharger.data.history.BatterySample
import com.micharger.data.history.HistoryRepository
```

`HomeScreen()` 中加状态与加载：

```kotlin
var samples by remember { mutableStateOf<List<BatterySample>>(emptyList()) }

LaunchedEffect(Unit) {
    samples = withContext(Dispatchers.IO) { HistoryRepositoryLoading() }
}
```

实际写法（与 Application 单例一致）：

```kotlin
LaunchedEffect(Unit) {
    samples = withContext(Dispatchers.IO) { com.micharger.app.historyRepository.last24h() }
}
```

`ChargingControlCard(...)` 之后追加：

```kotlin
Spacer(modifier = Modifier.height(12.dp))
SmallTitle(text = "24 小时电量曲线")
HistoryCard(samples = samples)
```

文件底部追加：

```kotlin
@Composable
private fun HistoryCard(samples: List<BatterySample>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (samples.size < 2) {
                Text(
                    "暂无历史数据。开启充电守护服务后，将按采样间隔自动记录。",
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            } else {
                Canvas(modifier = Modifier.fillMaxWidth().height(160.dp)) {
                    val minT = samples.first().timestamp.toFloat()
                    val maxT = samples.last().timestamp.toFloat()
                    val range = (maxT - minT).coerceAtLeast(1f)
                    val points = samples.map {
                        Offset(
                            x = (it.timestamp - minT) / range * size.width,
                            y = (1f - it.level.coerceIn(0, 100) / 100f) * size.height,
                        )
                    }
                    // 基线 100%
                    drawLine(
                        color = MiuixTheme.colorScheme.dividerLine,
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 2f,
                    )
                    val path = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        points.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(
                        path = path,
                        color = MiuixTheme.colorScheme.primary,
                        style = Stroke(width = 6f),
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = Formatters.hhmm(samples.first().timestamp),
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "现在",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 7.4: 构建验证**

```bash
./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`（KSP 生成 Room 实现类）。

- [ ] **Step 7.5: Commit**

```bash
git add -A
git commit -m "feat: Room 充电历史 + 24h 电量曲线图"
```

---

## Task 8: 耗电页（双模式排行）

**Files:**
- Create: `ui/usage/UsageParser.kt`、`app/src/test/java/com/micharger/usage/UsageParserTest.kt`、`data/usage/AppUsageRepository.kt`
- Modify: `MiChargerApp.kt`、`ui/usage/UsageScreen.kt`

- [ ] **Step 8.1: 解析器（先写测试）**

创建 `app/src/main/java/com/micharger/ui/usage/UsageParser.kt`：

```kotlin
package com.micharger.ui.usage

object UsageParser {

    data class UidPower(val uid: Int, val mah: Double)

    private val regex = Regex("""^\s*Uid (u0a(\d+)|(\d+)):\s*([\d.]+)""", RegexOption.MULTILINE)

    /** 解析 dumpsys batterystats --charged 的 Estimated power use 段落 */
    fun parse(output: String): List<UidPower> =
        regex.findAll(output).mapNotNull { m ->
            val uid = m.groupValues[2].toIntOrNull()?.let { it + 10000 }
                ?: m.groupValues[3].toIntOrNull()
            val mah = m.groupValues[4].toDoubleOrNull()
            if (uid != null && mah != null && mah > 0) UidPower(uid, mah) else null
        }.sortedByDescending { it.mah }.take(20)
}
```

创建 `app/src/test/java/com/micharger/usage/UsageParserTest.kt`：

```kotlin
package com.micharger.usage

import com.micharger.ui.usage.UsageParser
import org.junit.Assert.assertEquals
import org.junit.Test

class UsageParserTest {

    @Test
    fun parseExtractsUidPowerLines() {
        val output = """
            Estimated power use (mAh):
              Capacity: 3300, Computed: 250
              Uid u0a123: 45.2 ( cpu=40 screen=3 radio=1 )
              Uid 1000: 12.1 ( cpu=12 )
              Uid u0a5: 0.0 ( cpu=0 )
        """.trimIndent()
        val result = UsageParser.parse(output)
        assertEquals(2, result.size)
        assertEquals(10123, result[0].uid)
        assertEquals(45.2, result[0].mah, 0.001)
        assertEquals(1000, result[1].uid)
    }

    @Test
    fun parseReturnsEmptyForGarbage() {
        assertEquals(0, UsageParser.parse("no data here").size)
    }
}
```

- [ ] **Step 8.2: 运行测试验证通过**

```bash
./gradlew :app:testDebugUnitTest --tests "com.micharger.usage.UsageParserTest"
```
Expected: `BUILD SUCCESSFUL`，2 个测试通过。

- [ ] **Step 8.3: AppUsageRepository**

创建 `app/src/main/java/com/micharger/data/usage/AppUsageRepository.kt`：

```kotlin
package com.micharger.data.usage

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import com.micharger.ui.usage.UsageParser
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppUsage(
    val label: String,
    val packageName: String,
    val mah: Double?,        // root 精确模式
    val screenTimeMs: Long?, // 估算模式
)

class AppUsageRepository(private val context: Context) {

    fun hasUsageAccess(): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = ops.checkOpNoThrow(
            AppOpsManager.OP_GET_USAGE_STATS, Process.myUid(), context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Root 模式：batterystats 耗电排行 */
    suspend fun powerRanking(): List<AppUsage> = withContext(Dispatchers.IO) {
        val result = Shell.cmd("dumpsys batterystats --charged").exec()
        val uidPowers = UsageParser.parse(result.out.joinToString("\n"))
        uidPowers.mapNotNull { up ->
            val pkg = context.packageManager.getPackagesForUid(up.uid)?.firstOrNull()
                ?: return@mapNotNull null
            AppUsage(label = labelFor(pkg), packageName = pkg, mah = up.mah, screenTimeMs = null)
        }
    }

    /** 无 Root 模式：近 24h 前台时长排行 */
    suspend fun screenTimeRanking(): List<AppUsage> = withContext(Dispatchers.IO) {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val start = end - 24L * 3600 * 1000
        usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
            .filter { it.totalTimeInForeground > 0 }
            .sortedByDescending { it.totalTimeInForeground }
            .take(20)
            .map { stats ->
                AppUsage(
                    label = labelFor(stats.packageName),
                    packageName = stats.packageName,
                    mah = null,
                    screenTimeMs = stats.totalTimeInForeground,
                )
            }
    }

    private fun labelFor(pkg: String): String =
        runCatching {
            context.packageManager.getApplicationLabel(
                context.packageManager.getApplicationInfo(pkg, 0),
            ).toString()
        }.getOrDefault(pkg)
}
```

- [ ] **Step 8.4: 挂到 Application**

`MiChargerApp` 类体新增 `lateinit var appUsageRepository: AppUsageRepository`（private set），`onCreate()` 中 `appUsageRepository = AppUsageRepository(this)`，import 补 `com.micharger.data.usage.AppUsageRepository`。

- [ ] **Step 8.5: UsageScreen UI**

将 `UsageScreen.kt` 中的 `Text(text = "耗电排行骨架")` 替换为实际列表，函数体改为：

```kotlin
@Composable
fun UsageScreen() {
    val scrollBehavior = MiuixScrollBehavior()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var mode by remember { mutableStateOf("") }           // "root" / "screen" / "need_permission"
    var items by remember { mutableStateOf<List<AppUsage>>(emptyList()) }

    fun reload() {
        scope.launch {
            loading = true
            val repo = context.app.appUsageRepository
            if (RootChecker.check()) {
                mode = "root"
                items = withContext(Dispatchers.IO) { repo.powerRanking() }
            } else if (repo.hasUsageAccess()) {
                mode = "screen"
                items = withContext(Dispatchers.IO) { repo.screenTimeRanking() }
            } else {
                mode = "need_permission"
                items = emptyList()
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    Scaffold(
        topBar = { TopAppBar(title = "耗电排行", scrollBehavior = scrollBehavior) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState()),
        ) {
            TextButton(
                text = "刷新",
                onClick = { reload() },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            when {
                loading -> Text("加载中…", modifier = Modifier.padding(16.dp), fontSize = 14.sp)
                mode == "need_permission" -> Card(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("需要\"使用情况访问\"权限才能统计应用耗电/时长", fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = {
                            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        }) { Text("去授权") }
                    }
                }
                mode == "screen" -> SmallTitle(text = "按近 24h 前台时长估算（无 Root）")
                else -> SmallTitle(text = "按 Root 耗电统计")
            }
            items.forEach { usage ->
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Row(modifier = Modifier.padding(16.dp)) {
                        Column {
                            Text(text = usage.label, fontSize = 16.sp)
                            Text(
                                text = usage.packageName,
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = usage.mah?.let { String.format(Locale.US, "%.1f mAh", it) }
                                ?: usage.screenTimeMs?.let { Formatters.duration(it) }
                                ?: "--",
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        }
    }
}
```

新增 import：

```kotlin
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import com.micharger.app
import com.micharger.data.usage.AppUsage
import com.micharger.util.Formatters
import com.micharger.util.RootChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale
```

- [ ] **Step 8.6: 构建验证**

```bash
./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 8.7: Commit**

```bash
git add -A
git commit -m "feat: 耗电排行（Root 精确统计 + 无 Root 时长估算双模式）"
```

---

## Task 9: 信息页

**Files:**
- Modify: `ui/info/InfoScreen.kt`

- [ ] **Step 9.1: InfoScreen 实现**

将 `InfoScreen.kt` 中的 `Text(text = "信息骨架")` 替换，函数体改为：

```kotlin
@Composable
fun InfoScreen() {
    val scrollBehavior = MiuixScrollBehavior()

    var deviceRows by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    var batteryRows by remember { mutableStateOf(listOf<Pair<String, String>>()) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            deviceRows = loadDeviceRows()
            batteryRows = loadBatteryRows()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = "信息", scrollBehavior = scrollBehavior) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState()),
        ) {
            SmallTitle(text = "设备")
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    deviceRows.forEach { (k, v) -> InfoRow(k, v) }
                }
            }
            SmallTitle(text = "电池")
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    batteryRows.forEach { (k, v) -> InfoRow(k, v) }
                }
            }
        }
    }
}

private fun loadDeviceRows(): List<Pair<String, String>> {
    val miui = Shell.cmd("getprop ro.miui.ui.version.name").exec().out.firstOrNull()?.trim()
    val hyper = Shell.cmd("getprop ro.mi.os.version.name").exec().out.firstOrNull()?.trim()
    return listOf(
        "品牌" to Build.MANUFACTURER,
        "型号" to Build.MODEL,
        "设备代号" to Build.DEVICE,
        "Android 版本" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        "系统版本" to listOfNotNull(miui?.takeIf { it.isNotEmpty() && !it.contains("error") },
            hyper?.takeIf { it.isNotEmpty() && !it.contains("error") })
            .firstOrNull() ?: "无",
    )
}

private fun loadBatteryRows(): List<Pair<String, String>> {
    val reader = SysFsReader()
    val base = "/sys/class/power_supply/battery"
    val full = reader.read("$base/charge_full")?.toDoubleOrNull()?.div(1000.0)?.toInt()
    val design = reader.read("$base/charge_full_design")?.toDoubleOrNull()?.div(1000.0)?.toInt()
    val health = if (full != null && design != null && design > 0) {
        String.format(Locale.US, "%.1f %%（%d/%d mAh）", full * 100.0 / design, full, design)
    } else null
    val cycles = reader.read("$base/cycle_count")?.trim()?.toIntOrNull()
    return listOf(
        "设计容量" to Formatters.capacity(design),
        "实际满充容量" to Formatters.capacity(full),
        "电池健康度" to (health ?: "需要 Root 权限读取"),
        "循环次数" to (cycles?.toString() ?: "需要 Root 权限读取"),
    )
}
```

新增 import：

```kotlin
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.sp
import com.micharger.data.battery.SysFsReader
import com.micharger.util.Formatters
import com.micharger.util.RootChecker
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
```

（`RootChecker` 若未使用可移除；电池容量读取不强制 root——直接 `File.readText` 失败会走 shell cat，无 root 时返回 null 显示提示。）

- [ ] **Step 9.2: 构建验证**

```bash
./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 9.3: Commit**

```bash
git add -A
git commit -m "feat: 信息页（设备信息 + 电池健康/循环次数）"
```

---

## Task 10: GitHub Actions CI + README + LICENSE

**Files:**
- Create: `.github/workflows/build.yml`、`README.md`、`LICENSE`

- [ ] **Step 10.1: CI 工作流**

创建 `.github/workflows/build.yml`：

```yaml
name: Build APK

on:
  push:
    branches: [ main ]
  workflow_dispatch:
  release:
    types: [ created ]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Build release APK
        run: ./gradlew assembleRelease --no-daemon

      - name: Upload APK artifact
        uses: actions/upload-artifact@v4
        with:
          name: MiCharger-apk
          path: app/build/outputs/apk/release/*.apk

      - name: Attach APK to release
        if: github.event_name == 'release'
        uses: softprops/action-gh-release@v2
        with:
          files: app/build/outputs/apk/release/*.apk
```

- [ ] **Step 10.2: README**

创建 `README.md`：

```markdown
# MiCharger 充电管家

针对小米9（cepheus / Android 13）的充电控制工具，MIUI 风格（[Miuix](https://github.com/compose-miuix-ui/miuix)）。

## 功能
- 实时电池状态：电量 / 电流 / 电压 / 功率 / 温度 / 剩余容量
- 充电控制（需 Root / Magisk）：暂停充电、恢复充电、充电限流
- 充电守护：达到目标电量自动暂停，回落阈值自动恢复；支持开机自启
- 充电历史：24 小时电量曲线（需开启守护服务）
- 耗电排行：Root 下按 batterystats 精确统计，无 Root 按前台时长估算
- 信息页：设备信息、电池健康度、循环次数

## 安装
从 [Releases](../../releases) 下载 APK 安装（release 构建当前使用 debug 签名）。

## 权限说明
| 权限 | 用途 |
|---|---|
| Root（Magisk） | 写 sysfs 节点控制充电：`/sys/class/power_supply/battery/` 下 `input_suspend`、`battery_charging_enabled`、`constant_charge_current_max` |
| 使用情况访问 | 无 Root 时的应用前台时长统计（可选授权） |
| 通知 | 充电守护前台服务通知 |

## 构建要求
- JDK 17+
- Android SDK（compileSdk 36）
- `./gradlew assembleDebug` 或直接推送触发 GitHub Actions 自动打包

## 免责声明
修改充电策略存在电池风险，请自行评估。作者不对任何设备损坏负责。

## License
MIT
```

- [ ] **Step 10.3: LICENSE**

创建 `LICENSE`（MIT，全文标准文本）：

```
MIT License

Copyright (c) 2026 MiCharger contributors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

- [ ] **Step 10.4: 最终构建验证**

```bash
./gradlew assembleDebug assembleRelease
```
Expected: `BUILD SUCCESSFUL`，`app/build/outputs/apk/release/app-release.apk` 存在。

- [ ] **Step 10.5: Commit**

```bash
git add -A
git commit -m "ci: GitHub Actions 自动打包 + README + MIT License"
```

- [ ] **Step 10.6: 推送到 GitHub（用户执行或经确认后执行）**

```bash
git remote add origin git@github.com:<你的用户名>/MiCharger.git
git push -u origin main
```
Expected: Actions 页面出现 "Build APK" 工作流并变绿，Artifacts 中可下载 APK。

---

## 自审记录（Self-Review）

1. **需求覆盖**：主页充电情况（Task 3）、充电控制（Task 4）、耗电情况（Task 8）、手机信息（Task 9）、设置页（Task 5）、历史曲线（Task 7）、CI 打包（Task 10）、Root 前提/双模式/名称 —— 全部有对应任务。
2. **占位符**：Task 1 Manifest 的 service/receiver 暂注释、Task 6 采样暂注释均为**有序衔接**而非 TBD，各自有明确的启用步骤；Task 5 Step 5.4 已显式标出非法占位行并给出替换代码。
3. **类型一致性**：`ChargingNodes`/`ChargingController.isSuspended()`/`BatterySample(timestamp, level, charging)`/`AppUsage(label, packageName, mah, screenTimeMs)`/`AppSettings` 各任务间引用一致；`Formatters.hhmm` 有编译兜底说明；`DropdownItem` 构造有签名核对提示。

## 已知风险与兜底

- **KSP 2.3.12 与 Kotlin 2.4.20 兼容性**：KSP 2.x 已与 Kotlin 版本解耦，理论兼容；若 KSP 编译报错，将 `ksp` 版本改为与 Kotlin 匹配的最新 tag 重试。
- **Miuix `ScrollBehavior.nestedScrollConnection` 属性名**：与 m3 同名概率极高，若报错按 Task 2 Step 2.6 兜底处理。
- **小米9 sysfs 节点实测**：节点探测带「原样写回」验证，未找到节点时 UI 明确提示，不会误写。
