package com.micharger.ui.usage

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.micharger.app
import com.micharger.data.usage.AppUsage
import com.micharger.util.Formatters
import com.micharger.util.RootChecker
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
