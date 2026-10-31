package com.forolderr.wxvideo

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.forolderr.wxvideo.ui.theme.WxVideoCallTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WxVideoCallTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AddShortcutScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun AddShortcutScreen(modifier: Modifier = Modifier) {
    var name by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("微信联系人名字（须与备注完全一致）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                if (name.isNotBlank()) {
                    ShortcutHelper.addShortcut(context, name.trim())
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("添加到桌面")
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                if (name.isBlank()) {
                    Toast.makeText(context, "先输入联系人名字", Toast.LENGTH_SHORT).show()
                } else {
                    val contact = name.trim()
                    WxWidgetProvider.setPendingContact(context, contact)
                    val manager = AppWidgetManager.getInstance(context)
                    val provider = ComponentName(context, WxWidgetProvider::class.java)
                    val supported = manager.isRequestPinAppWidgetSupported
                    Log.d("WxVideoCall", "pinWidget supported=$supported")
                    if (supported) {
                        val ok = manager.requestPinAppWidget(provider, null, null)
                        Log.d("WxVideoCall", "requestPinAppWidget=$ok")
                        Toast.makeText(context, "请确认添加到主屏幕", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(
                            context,
                            "桌面不支持自动添加，请长按桌面 → 小组件 → WxVideoCall",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("添加为大组件（可缩放）")
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                val power = context.getSystemService(PowerManager::class.java)
                if (power?.isIgnoringBatteryOptimizations(context.packageName) == true) {
                    Toast.makeText(context, "已关闭电池优化", Toast.LENGTH_SHORT).show()
                } else {
                    val ok = runCatching {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:${context.packageName}")
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }.isSuccess
                    if (!ok) {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("关闭电池优化（防后台被杀）")
        }
        Spacer(Modifier.height(28.dp))
        Text(
            text = "使用步骤\n\n" +
                "【一、首次设置 · 只做一次】\n" +
                "1. 设置 → 应用 → 应用管理 → 搜索 WxVideoCall → 权限管理 → 右上角三点 → 解除所有授权限制\n" +
                "2. 设置 → 应用 → 应用管理 → 搜索 WxVideoCall → 耗电管理 → 完全允许后台行为 → 允许\n" +
                "3. 设置 → 应用 → 自启动 → 搜索 WxVideoCall → 允许\n" +
                "4. 设置 → 辅助功能 → 无障碍 → 已下载的应用 →「微信视频呼叫助手」→ 允许\n\n" +
                "【二、添加联系人】\n" +
                "5. 输入微信好友的备注名，要和微信里显示的一模一样\n" +
                "6. 点「添加为大组件（可缩放）」\n" +
                "7. 弹出「添加到主屏幕」就点确定；没弹就长按桌面空白 → 小组件 → 找到 WxVideoCall → 拖到桌面\n\n" +
                "【三、日常使用】\n" +
                "8. 点桌面上的绿色方块，微信会自动发起视频通话\n" +
                "9. 换人：输入新名字，再拖一个组件到桌面\n" +
                "10. 删除：长按组件 → 移除\n\n" +
                "注意：手机重启后第 4 步会被系统关闭，需要重新打开。",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
