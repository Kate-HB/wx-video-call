package com.forolderr.wxvideo

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast

class CallFlowActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private var contact: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val name = intent?.getStringExtra(ShortcutHelper.EXTRA_CONTACT)
        if (name.isNullOrBlank()) {
            finish()
            return
        }
        contact = name

        if (!isServiceEnabled()) {
            if (tryEnableAccessibility()) {
                waitForService(0)
                return
            }
            Toast.makeText(this, "请先在设置中开启无障碍服务", Toast.LENGTH_LONG).show()
            runCatching {
                startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            finish()
            return
        }

        waitForService(0)
    }

    /** 无障碍开关是否真的启用（不代表服务进程还活着） */
    private fun isServiceEnabled(): Boolean {
        val manager = getSystemService(AccessibilityManager::class.java) ?: return false
        return manager
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo?.serviceInfo?.packageName == packageName }
    }

    /**
     * 重启后系统会关掉无障碍。若已通过 adb 授予 WRITE_SECURE_SETTINGS，
     * 直接把本服务写回启用的服务列表。
     */
    private fun tryEnableAccessibility(): Boolean = runCatching {
        val mine = "$packageName/${WxAccessibilityService::class.java.name}"
        val current = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        val updated = (current.split(':').filter { it.isNotBlank() } + mine)
            .distinct()
            .joinToString(":")
        Settings.Secure.putString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            updated
        )
        Settings.Secure.putInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        true
    }.getOrDefault(false)

    /** 开关已开但服务可能被系统清理，最多等 3 秒重连 */
    private fun waitForService(attempt: Int) {
        val service = WxAccessibilityService.instance
        if (service == null) {
            if (attempt >= 20) {
                Toast.makeText(
                    this,
                    "无障碍服务未就绪，请在设置里关闭再打开一次",
                    Toast.LENGTH_LONG
                ).show()
                finish()
                return
            }
            handler.postDelayed({ waitForService(attempt + 1) }, 150)
            return
        }

        val name = contact ?: run { finish(); return }
        val launch = packageManager.getLaunchIntentForPackage(WX_PACKAGE)
        if (launch == null) {
            Toast.makeText(this, "未安装微信", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        if (!service.startCall(
                name,
                intent.getBooleanExtra("dryRun", false),
                intent.getStringExtra("stopAfter")
            )
        ) {
            finish()
            return
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(launch)
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        const val WX_PACKAGE = "com.tencent.mm"
    }
}
