package com.micharger.ui.home

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
import com.micharger.data.battery.ChargingNodes
import com.micharger.util.Formatters
import com.micharger.util.RootChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun HomeScreen() {
    val scrollBehavior = MiuixScrollBehavior()
    val context = LocalContext.current
    var battery by remember { mutableStateOf<BatteryInfo?>(null) }
    var rootGranted by remember { mutableStateOf<Boolean?>(null) }
    var nodes by remember { mutableStateOf<ChargingNodes?>(null) }
    var suspended by remember { mutableStateOf<Boolean?>(null) }
    var limitMa by remember { mutableStateOf(1500f) }
    var limitBusy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val repo = context.app.batteryRepository
        while (true) {
            battery = withContext(Dispatchers.IO) { repo.snapshot() }
            delay(2000)
        }
    }

    LaunchedEffect(Unit) {
        val controller = context.app.chargingController
        rootGranted = RootChecker.check()
        if (rootGranted == true) {
            nodes = controller.initialize()
            suspended = controller.isSuspended()
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

            ChargingControlCard(
                rootGranted = rootGranted,
                nodes = nodes,
                suspended = suspended,
                limitMa = limitMa,
                limitBusy = limitBusy,
                onPauseResume = {
                    CoroutineScope(Dispatchers.IO).launch {
                        val controller = context.app.chargingController
                        val ok = if (suspended == true) controller.resume() else controller.pause()
                        if (ok) suspended = controller.isSuspended()
                    }
                },
                onApplyLimit = {
                    CoroutineScope(Dispatchers.IO).launch {
                        limitBusy = true
                        context.app.chargingController.setCurrentLimit(limitMa.toInt())
                        limitBusy = false
                    }
                },
                onClearLimit = {
                    CoroutineScope(Dispatchers.IO).launch {
                        limitBusy = true
                        context.app.chargingController.clearCurrentLimit()
                        limitBusy = false
                    }
                },
                onLimitChange = { limitMa = it },
            )
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
    onLimitChange: (Float) -> Unit,
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
                            onValueChange = onLimitChange,
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
