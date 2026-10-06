package com.micharger.ui.info

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.micharger.data.battery.SysFsReader
import com.micharger.util.Formatters
import com.topjohnwu.superuser.Shell
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar

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
        "系统版本" to (listOfNotNull(miui?.takeIf { it.isNotEmpty() && !it.contains("error") },
            hyper?.takeIf { it.isNotEmpty() && !it.contains("error") }).firstOrNull() ?: "无"),
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

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(text = label, fontSize = 14.sp)
        Spacer(modifier = Modifier.weight(1f))
        Text(text = value, fontSize = 14.sp)
    }
}
