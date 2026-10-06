package com.micharger.ui.about

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.micharger.R
import com.micharger.ui.icons.AppIcons
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val REPO_URL = "https://github.com/Mojito909/MiCharger"

private val TECH_STACK = listOf(
    "Kotlin 2.4.20" to "开发语言",
    "Jetpack Compose" to "声明式 UI 框架",
    "Miuix 0.9.4" to "小米风格 UI 组件库",
    "Room 2.8.5" to "充电历史本地数据库",
    "DataStore" to "配置持久化存储",
    "libsu 6.0.0" to "Root 充电控制",
)

private val DISCLAIMER = buildString {
    append("本应用通过 Root 权限直接读写系统充电节点，以实现充电控制。")
    append("该功能属于非官方的系统级操作，可能存在不可预知的风险。\n\n")
    append("使用本应用产生的一切后果（包括但不限于电池损耗、系统异常、设备损坏或数据丢失）由用户自行承担。")
    append("请仅在自有设备上使用，并理解您所执行的操作。项目按“现状”提供，作者不对任何直接或间接损失负责。")
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val scrollBehavior = MiuixScrollBehavior()
    val context = LocalContext.current
    val versionName = loadVersionName(context)

    Scaffold(
        topBar = {
            TopAppBar(
                title = "关于",
                largeTitle = "关于",
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = AppIcons.Back,
                            contentDescription = "返回",
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            AppHeader(versionName = versionName)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallTitle(text = "开源仓库")
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = "GitHub",
                        summary = REPO_URL,
                        onClick = { openUrl(context, REPO_URL) },
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallTitle(text = "技术栈")
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        TECH_STACK.forEachIndexed { index, (item, desc) ->
                            InfoRow(label = item, value = desc, emphasized = index == 0)
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallTitle(text = "免责声明")
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = DISCLAIMER,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun AppHeader(versionName: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 28.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher),
                contentDescription = "MiCharger 应用图标",
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(20.dp)),
                contentScale = ContentScale.Crop,
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "MiCharger 充电管家",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "版本 $versionName",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, emphasized: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = if (emphasized) FontWeight.Medium else FontWeight.Normal,
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = value,
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

private fun loadVersionName(context: Context): String =
    runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "--"
    }.getOrDefault("--")

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
