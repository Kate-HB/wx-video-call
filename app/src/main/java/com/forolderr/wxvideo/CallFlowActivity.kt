package com.forolderr.wxvideo

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

class CallFlowActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val contact = intent?.getStringExtra(ShortcutHelper.EXTRA_CONTACT)
        if (contact.isNullOrBlank()) {
            finish()
            return
        }

        val service = WxAccessibilityService.instance
        if (service == null) {
            Toast.makeText(this, "请先在设置中开启无障碍服务", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val launch = packageManager.getLaunchIntentForPackage(WX_PACKAGE)
        if (launch == null) {
            Toast.makeText(this, "未安装微信", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        if (!service.startCall(contact, intent.getBooleanExtra("dryRun", false),
                intent.getStringExtra("stopAfter"))) {
            finish()
            return
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(launch)
        finish()
    }

    companion object {
        const val WX_PACKAGE = "com.tencent.mm"
    }
}
