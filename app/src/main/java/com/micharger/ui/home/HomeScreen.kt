package com.micharger.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
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
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun HomeScreen() {
    val scrollBehavior = MiuixScrollBehavior()
    val context = LocalContext.current
    var battery by remember { mutableStateOf<BatteryInfo?>(null) }

    LaunchedEffect(Unit) {
        val repo = context.app.batteryRepository
        while (true) {
            battery = withContext(Dispatchers.IO) { repo.snapshot() }
            delay(2000)
        }
    }

    // 一次性清理旧版本遗留的内核写入（限流/暂停），恢复系统默认充电行为
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val controller = context.app.chargingController
            controller.initialize()
            controller.clearCurrentLimit()
            controller.resume()
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
            battery?.let { info ->
                BatteryCard(info = info)
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
                Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
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
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(text = label, fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Spacer(modifier = Modifier.weight(1f))
        Text(text = value, fontSize = 14.sp)
    }
}
