package com.micharger.ui.settings

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
fun SettingsScreen(onOpenAbout: () -> Unit) {
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
            scope.launch { context.app.settingsRepository.setGuardEnabled(true) }
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
                .padding(horizontal = 16.dp)
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
                        scope.launch {
                            val app = context.app
                            app.settingsRepository.setGuardEnabled(checked)
                            if (checked) {
                                ChargingGuardService.start(context)
                            } else {
                                ChargingGuardService.stop(context)
                                launch(kotlinx.coroutines.Dispatchers.IO) {
                                    val controller = app.chargingController
                                    controller.initialize()
                                    controller.clearCurrentLimit()
                                    controller.resume()
                                }
                            }
                        }
                    }
                },
                title = "充电守护服务",
                summary = "达到目标电量自动暂停充电，回落到恢复阈值继续充电",
            )
            SwitchPreference(
                checked = settings.showNotification,
                onCheckedChange = { checked ->
                    scope.launch { context.app.settingsRepository.setShowNotification(checked) }
                },
                title = "状态栏显示",
                summary = "关闭后守护转入静默运行，不再在状态栏显示图标（需守护服务开启）",
            )
            SwitchPreference(
                checked = settings.bootStart,
                onCheckedChange = { checked ->
                    scope.launch { context.app.settingsRepository.setBootStart(checked) }
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

            SmallTitle(text = "关于")
            top.yukonga.miuix.kmp.basic.Card(modifier = Modifier.fillMaxWidth()) {
                top.yukonga.miuix.kmp.preference.ArrowPreference(
                    title = "关于 MiCharger",
                    onClick = onOpenAbout,
                )
            }
        }
    }
}
