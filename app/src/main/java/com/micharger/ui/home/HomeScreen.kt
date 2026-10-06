package com.micharger.ui.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.micharger.app
import com.micharger.data.battery.BatteryInfo
import com.micharger.data.settings.AppSettings
import com.micharger.service.ChargingGuardService
import com.micharger.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun HomeScreen() {
    val scrollBehavior = MiuixScrollBehavior()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var battery by remember { mutableStateOf<BatteryInfo?>(null) }
    val settings by context.app.settingsRepository.settingsFlow.collectAsState(initial = AppSettings())
    val guardStatus by ChargingGuardService.statusFlow.collectAsState()

    LaunchedEffect(Unit) {
        val repo = context.app.batteryRepository
        while (true) {
            battery = withContext(Dispatchers.IO) { repo.snapshot() }
            delay(5000)
        }
    }

    // 一次性清理旧版本遗留的内核写入（限流/暂停）；守护开启时让守护全权接管，避免互相打架
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val app = context.app
            if (!app.settingsRepository.settingsOnce().guardEnabled) {
                val controller = app.chargingController
                controller.initialize()
                controller.clearCurrentLimit()
                controller.resume()
            }
        }
    }

    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            scope.launch { context.app.settingsRepository.setGuardEnabled(true) }
            ChargingGuardService.start(context)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = "充电", largeTitle = "充电管家", scrollBehavior = scrollBehavior) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            GuardActivationCard(
                active = settings.guardEnabled && guardStatus != null,
                status = guardStatus,
            )

            battery?.let { info ->
                BatteryCard(info = info)
            }

            GuardCard(
                enabled = settings.guardEnabled,
                status = guardStatus,
                targetSoc = settings.targetSoc,
                resumeSoc = settings.resumeSoc,
                onToggle = { checked ->
                    if (checked && Build.VERSION.SDK_INT >= 33) {
                        notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        scope.launch {
                            val app = context.app
                            app.settingsRepository.setGuardEnabled(checked)
                            if (checked) {
                                ChargingGuardService.start(context)
                            } else {
                                ChargingGuardService.stop(context)
                                withContext(Dispatchers.IO) {
                                    val controller = app.chargingController
                                    controller.initialize()
                                    controller.clearCurrentLimit()
                                    controller.resume()
                                }
                            }
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun GuardActivationCard(active: Boolean, status: String?) {
    val accent = if (active) androidx.compose.ui.graphics.Color(0xFF3F7D3A) else MiuixTheme.colorScheme.onSurfaceVariantSummary
    val surface = if (active) androidx.compose.ui.graphics.Color(0xFFEAF5E9) else MiuixTheme.colorScheme.surface
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(surface),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (active) "已激活" else "未激活",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
                Text(
                    text = status ?: "开启充电守护后，自动维持目标电量",
                    fontSize = 14.sp,
                    color = accent,
                )
            }
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(accent)
                    .padding(14.dp),
            ) {
                Text(text = if (active) "✓" else "–", fontSize = 28.sp, color = surface)
            }
        }
    }
}

@Composable
private fun BatteryCard(info: BatteryInfo) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row {
                Text(
                    text = "${info.levelPct}%",
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (info.charging) MiuixTheme.colorScheme.primary
                            else MiuixTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = when {
                            info.charging && info.chargeType != null -> "充电中 · ${info.chargeType}"
                            info.charging -> "充电中"
                            else -> "未充电"
                        },
                        fontSize = 16.sp,
                        fontWeight = if (info.chargeType != null) FontWeight.Medium else FontWeight.Normal,
                        color = if (info.charging) MiuixTheme.colorScheme.primary
                                else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    if (info.charging && info.powerW != null) {
                        Text(
                            text = Formatters.power(info.powerW),
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            InfoRow("充电协议", info.protocol ?: "--")
            InfoRow("电流", Formatters.current(info.currentMa))
            InfoRow("电压", Formatters.voltage(info.voltageMv))
            InfoRow("功率", Formatters.power(info.powerW))
            InfoRow("温度", Formatters.temp(info.tempTenths))
            InfoRow("剩余容量", Formatters.capacity(info.chargeCounterMah))
        }
    }
}

@Composable
private fun GuardCard(
    enabled: Boolean,
    status: String?,
    targetSoc: Int,
    resumeSoc: Int,
    onToggle: (Boolean) -> Unit,
) {
    SmallTitle(text = "充电守护")
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "充电守护",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (enabled) "已激活 · 目标 $targetSoc% / 恢复 $resumeSoc%"
                               else "未激活",
                        fontSize = 13.sp,
                        color = if (enabled) MiuixTheme.colorScheme.primary
                                else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontWeight = if (enabled) FontWeight.Medium else FontWeight.Normal,
                    )
                }
                Switch(checked = enabled, onCheckedChange = onToggle)
            }
            if (enabled) {
                Text(
                    text = status ?: "正在启动守护服务…",
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onSurface,
                )
            } else {
                Text(
                    text = "开启后自动维持在目标电量区间，减少电池满电损耗",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
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
