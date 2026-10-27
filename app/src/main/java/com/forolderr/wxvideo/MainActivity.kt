package com.forolderr.wxvideo

import android.appwidget.AppWidgetManager
import android.content.ComponentName
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
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center
    ) {
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
        Spacer(Modifier.height(28.dp))
        Text(
            text = "使用步骤：\n1. 先在系统设置里开启无障碍服务\n2. 输入联系人备注，点「添加到桌面」\n3. 桌面出现大图标，点击即自动拨打微信视频",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
