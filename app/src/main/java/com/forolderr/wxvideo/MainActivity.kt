package com.forolderr.wxvideo

import android.appwidget.AppWidgetManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.forolderr.wxvideo.ui.theme.WxVideoCallTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WxVideoCallTheme {
                AddShortcutScreen()
            }
        }
    }
}

@Composable
fun AddShortcutScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var advanced by remember { mutableStateOf(false) }
    var calibrated by remember { mutableStateOf(CalibrationStore(context).hasCalibration()) }

    // Calibration state changes when the user comes back from the calibration flow.
    DisposableEffect(context) {
        val owner = context as? LifecycleOwner
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                calibrated = CalibrationStore(context).hasCalibration()
            }
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    fun requireName(): String? {
        if (name.isBlank()) { toast("先输入联系人名字"); return null }
        return name.trim()
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(24.dp))
            BrandHeader()
            Spacer(Modifier.height(24.dp))

            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("联系人", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = { Text("微信备注名，须完全一致") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { requireName()?.let { ShortcutHelper.addShortcut(context, it) } },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    ) { Text("添加到桌面", style = MaterialTheme.typography.labelLarge) }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = {
                                val contact = requireName() ?: return@OutlinedButton
                                WxWidgetProvider.setPendingContact(context, contact)
                                val manager = AppWidgetManager.getInstance(context)
                                val provider = ComponentName(context, WxWidgetProvider::class.java)
                                val supported = manager.isRequestPinAppWidgetSupported
                                Log.d("WxVideoCall", "pinWidget supported=$supported")
                                if (supported) {
                                    val ok = manager.requestPinAppWidget(provider, null, null)
                                    Log.d("WxVideoCall", "requestPinAppWidget=$ok")
                                    toast("请确认添加到主屏幕")
                                } else {
                                    Toast.makeText(context,
                                        "桌面不支持自动添加：长按桌面 → 小组件 → WxVideoCall",
                                        Toast.LENGTH_LONG).show()
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f).heightIn(min = 46.dp)
                        ) { Text("添加大组件") }
                        OutlinedButton(
                            onClick = {
                                val contact = requireName() ?: return@OutlinedButton
                                context.startActivity(Intent(context, CallFlowActivity::class.java)
                                    .putExtra(ShortcutHelper.EXTRA_CONTACT, contact)
                                    .putExtra("dryRun", true))
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f).heightIn(min = 46.dp)
                        ) { Text("试运行") }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            CalibrationCard(calibrated) {
                val contact = requireName() ?: return@CalibrationCard
                context.startActivity(Intent(context, CallFlowActivity::class.java)
                    .putExtra(ShortcutHelper.EXTRA_CONTACT, contact)
                    .putExtra("calibrate", true))
            }

            Spacer(Modifier.height(14.dp))
            AdvancedCard(expanded = advanced, onToggle = { advanced = !advanced }) {
                ExportCalibration(context, ::toast)
                ImportCalibration(context, ::toast)
                ClearCalibration(context, ::toast)
                OpenAccessibilitySettings(context, ::toast)
                OpenAppSettings(context, ::toast)
                BatteryOptimization(context, ::toast)
            }

            Spacer(Modifier.height(22.dp))
            StepsCard()

            Spacer(Modifier.height(26.dp))
            Text(
                "仅在本机运行 · 校准数据不上传",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun BrandHeader() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(listOf(
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.tertiary)))
            ,
            contentAlignment = Alignment.Center
        ) {
            Text("视", color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text("视频呼叫助手", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(2.dp))
            Text("一键发起微信视频通话", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CalibrationCard(calibrated: Boolean, onCalibrate: () -> Unit) {
    val container = if (calibrated) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh
    val titleColor = if (calibrated) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurface
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = container)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 18.dp, top = 16.dp,
                bottom = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(
                if (calibrated) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (calibrated) "本机已校准" else "尚未校准",
                    style = MaterialTheme.typography.titleMedium, color = titleColor)
                Spacer(Modifier.height(2.dp))
                Text(
                    if (calibrated) "7 个位置已记录，所有联系人通用"
                    else "首次使用校准一次，之后免校准",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (calibrated) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onCalibrate) {
                Text(if (calibrated) "重新校准" else "去校准",
                    style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun AdvancedCard(expanded: Boolean, onToggle: () -> Unit,
                         content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle)
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("高级设置", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(2.dp))
                    Text("导出校准 · 权限 · 后台运行",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(if (expanded) "收起" else "展开",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
            }
            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) { content() }
            }
        }
    }
}

@Composable
private fun AdvancedItem(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ExportCalibration(context: android.content.Context, toast: (String) -> Unit) {
    AdvancedItem("导出校准（复制到剪贴板）") {
        val json = CalibrationStore(context).exportJson()
        if (json == null) toast("还没有校准数据") else {
            context.getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText("calibration", json))
            toast("校准已复制，换机后点「导入校准」")
        }
    }
}

@Composable
private fun ImportCalibration(context: android.content.Context, toast: (String) -> Unit) {
    AdvancedItem("导入校准（从剪贴板）") {
        val text = runCatching {
            context.getSystemService(ClipboardManager::class.java)
                .primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
        }.getOrNull()
        val ok = text != null && CalibrationStore(context).importJson(text)
        toast(if (ok) "校准已导入"
        else "导入失败：数据格式不对，或设备/微信版本不匹配")
    }
}

@Composable
private fun ClearCalibration(context: android.content.Context, toast: (String) -> Unit) {
    AdvancedItem("清除校准数据") {
        CalibrationStore(context).clear()
        toast("已清除本机校准数据")
    }
}

@Composable
private fun OpenAccessibilitySettings(context: android.content.Context, toast: (String) -> Unit) {
    AdvancedItem("开启无障碍服务") {
        runCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            .onFailure { Toast.makeText(context, "请在系统设置中搜索无障碍", Toast.LENGTH_LONG).show() }
    }
}

@Composable
private fun OpenAppSettings(context: android.content.Context, toast: (String) -> Unit) {
    AdvancedItem("应用权限与后台设置") {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")))
        }.onFailure { Toast.makeText(context, "请在系统设置中打开本应用详情", Toast.LENGTH_LONG).show() }
    }
}

@Composable
private fun BatteryOptimization(context: android.content.Context, toast: (String) -> Unit) {
    AdvancedItem("关闭电池优化（防后台被杀）") {
        val power = context.getSystemService(PowerManager::class.java)
        if (power?.isIgnoringBatteryOptimizations(context.packageName) == true) {
            toast("已关闭电池优化")
            return@AdvancedItem
        }
        val ok = runCatching {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.isSuccess
        if (!ok) {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
}

@Composable
private fun StepsCard() {
    val steps = listOf(
        "开启无障碍服务，并允许后台运行、关闭电池优化",
        "输入与微信备注完全一致的名字，完成一次本机校准",
        "点「试运行」检查流程，确认无误后添加到桌面",
        "之后点桌面图标，即可自动找到联系人并拨出视频通话"
    )
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("使用步骤", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(14.dp))
            steps.forEachIndexed { index, item ->
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier.size(22.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${index + 1}", fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(item, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f))
                }
                if (index != steps.lastIndex) Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            Spacer(Modifier.height(12.dp))
            Text(
                deviceSetupHint() + "\n手机、微信版本、字体或导航方式变化后，需重新校准。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun deviceSetupHint(): String = when (Build.MANUFACTURER.lowercase()) {
    "oppo", "oneplus", "realme" -> "ColorOS 系：检查耗电管理中的允许后台活动和自启动。"
    "xiaomi", "redmi", "poco" -> "小米系统：检查自启动、电池策略和后台弹出界面权限。"
    "vivo", "iqoo" -> "vivo 系统：检查自启动和后台高耗电设置。"
    "huawei", "honor" -> "华为/荣耀：检查应用启动管理，允许自启动、关联启动和后台活动。"
    "samsung" -> "三星：检查后台使用限制，避免将本应用加入休眠应用。"
    else -> "在系统设置中搜索后台运行或自启动；菜单名称因系统版本而异。"
}
