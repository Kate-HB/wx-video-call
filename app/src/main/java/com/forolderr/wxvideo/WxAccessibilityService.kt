package com.forolderr.wxvideo

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast

class WxAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val store by lazy { CalibrationStore(this) }
    private val overlay by lazy { CalibrationOverlay(this) }
    private var busy = false
    private var generation = 0
    private var phase = 0
    private var phaseStart = 0L
    private var contact = ""
    private var dryRun = false
    private var stopAfter: String? = null
    private var calibrationSignature = ""
    private var observedWechatWindow = -1
    private var wechatClass: String? = null
    private val captured = mutableMapOf<CallStep, CalibratedPoint>()

    fun startCall(contact: String, dryRun: Boolean = false, stopAfter: String? = null,
                  calibrate: Boolean = false): Boolean {
        if (busy || contact.isBlank()) return false
        busy = true
        generation++
        this.contact = contact.trim()
        this.dryRun = dryRun
        this.stopAfter = stopAfter
        captured.clear()
        wechatClass = null
        calibrationSignature = store.signature()
        getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("contact", this.contact))
        later(900) {
            if (calibrate) prepareCalibration() else resetToMain()
        }
        // A stuck gesture or vendor callback must not leave the service permanently busy.
        later(5 * 60_000) { abort("操作超时，请重试") }
        return true
    }

    private fun prepareCalibration() {
        prompt("请回到微信首页，清空之前的搜索。随后按提示逐步点选。最后一步只保存，不拨号。",
            "开始校准") { calibrate(CallStep.SEARCH) }
    }

    private fun calibrate(step: CallStep) {
        if (store.signature() != calibrationSignature) { abort("屏幕设置已变化，请重新校准"); return }
        val back: (() -> Unit)? = if (step == CallStep.SEARCH) null else ({
            if (busy) {
                performGlobalAction(GLOBAL_ACTION_BACK)
                later(700) { calibrate(CallStep.entries[step.ordinal - 1]) }
            }
        })
        pick("校准 ${step.ordinal + 1}/7：${step.instruction}\n联系人：$contact", back) { x, y ->
            val frame = wechatFrame() ?: run { abort("请在微信窗口内校准"); return@pick }
            val point = CalibratedPoint.capture(x, y, frame)
                ?: run { abort("位置不在微信窗口内，请重新校准"); return@pick }
            captured[step] = point
            if (step == CallStep.CONFIRM) {
                val saved = runCatching { store.save(calibrationSignature, captured) }.isSuccess
                if (saved) {
                    runCatching {
                        overlay.show("校准已保存\n7 个位置已全部记录，所有联系人通用。",
                            { finish(null) }, "完成", { finish(null) })
                    }.onFailure { finish("校准已保存；请返回助手试运行（不会拨号）") }
                } else {
                    finish("保存失败，请重新校准")
                }
            } else {
                later(200) {
                    dispatch(point, clickDuration(step)) {
                        later(700) { calibrate(CallStep.entries[step.ordinal + 1]) }
                    }
                }
            }
        }
    }

    private fun resetToMain(attempt: Int = 0) {
        if (!busy) return
        if (wechatFrame() != null) {
            // CallFlowActivity launches WeChat with CLEAR_TOP, so the main page is showing.
            enter(CallStep.SEARCH)
            return
        }
        if (attempt == 0) {
            // Some ROMs block service-initiated launches; the activity's launch usually works.
            val launch = packageManager.getLaunchIntentForPackage(CallFlowActivity.WX_PACKAGE)
            if (launch == null) { abort("未安装微信"); return }
            runCatching {
                startActivity(launch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                    android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP))
            }
        }
        if (attempt >= 10) {
            prompt("请手动打开微信并回到首页，再继续。", "已打开") { if (busy) resetToMain(0) }
            return
        }
        later(600) { resetToMain(attempt + 1) }
    }

    private fun enter(step: CallStep) {
        phase++
        phaseStart = SystemClock.elapsedRealtime()
        poll(step, phaseStart + STEP_TIMEOUT, phase)
    }

    private fun poll(step: CallStep, deadline: Long, expectedPhase: Int) {
        if (!busy || phase != expectedPhase) return
        if (overlay.showing) return
        val now = SystemClock.elapsedRealtime()
        val frame = wechatFrame()
        if (frame == null) {
            if (now >= deadline) abort("微信不在前台或窗口已变化，请重新开始")
            else later(500) { poll(step, deadline, expectedPhase) }
            return
        }
        // Let each page transition settle; tuned per step to keep the flow quick.
        if (now - phaseStart < settleMs(step)) { later(150) { poll(step, deadline, expectedPhase) }; return }

        val target = store.load(step)
        if (target == null) {
            if (now >= deadline) manual(step, "未找到本机校准位置，请核对后操作：${step.instruction}\n联系人：$contact")
            else later(700) { poll(step, deadline, expectedPhase) }
            return
        }
        if (target.point.resolve(frame) == null) {
            abort("微信窗口布局已变化，请重新校准当前联系人")
            return
        }
        if (step == CallStep.CONFIRM && dryRun) {
            finish("试运行完成，未拨号", "dryRun: stopped before final call")
            return
        }
        dispatch(target.point, clickDuration(step)) { advanced(step) }
    }

    private fun advanced(step: CallStep) {
        Log.d(TAG, "stage: ${step.debugName}")
        if (stopAfter == step.debugName) { finish("已停止在 ${step.debugName}"); return }
        if (step == CallStep.CONFIRM) { verifyDialStarted(); return }
        val next = CallStep.entries[step.ordinal + 1]
        val expected = when (step) {
            CallStep.SEARCH -> "fts"
            CallStep.RESULT -> "chatting"
            else -> null
        }
        if (expected == null) { later(0) { enter(next) }; return }
        awaitClass(expected, 0) { ok ->
            if (!ok) { abort("页面没有按预期切换，已停止"); return@awaitClass }
            later(0) { enter(next) }
        }
    }

    /** Activity names are still delivered even when WeChat hides its node tree. */
    private fun awaitClass(token: String, attempt: Int, done: (Boolean) -> Unit) {
        val cls = wechatClass
        if (cls != null && cls.contains(token, ignoreCase = true)) { done(true); return }
        if (attempt >= 15) { done(cls == null); return } // no activity events at all: do not block
        later(100) { awaitClass(token, attempt + 1, done) }
    }

    private fun verifyDialStarted() {
        later(3_000) {
            val cls = wechatClass.orEmpty()
            val dialed = cls.contains("voip", ignoreCase = true) || cls.contains("VideoActivity", true)
            finish(if (dialed) null else "未检测到通话界面，可能未拨出", "confirm class=$cls dialed=$dialed")
        }
    }

    private fun manual(step: CallStep, instruction: String) {
        if (step == CallStep.CONFIRM && dryRun) { abort("试运行禁止拨号"); return }
        pick(instruction) { x, y ->
            val frame = wechatFrame() ?: run { abort("微信不在前台"); return@pick }
            val point = CalibratedPoint.capture(x, y, frame)
                ?: run { abort("点击位置不在微信窗口内"); return@pick }
            later(150) { dispatch(point, clickDuration(step)) { advanced(step) } }
        }
    }

    private fun dispatch(point: CalibratedPoint, duration: Long, next: () -> Unit) {
        val frame = wechatFrame()
        if (store.signature() != calibrationSignature) { abort("显示或微信配置已变化，请重新开始并校准"); return }
        if (!busy || frame == null || point.resolve(frame) == null) { abort("窗口已变化，已停止点击"); return }
        val gesture = GestureDescription.Builder().addStroke(
            GestureDescription.StrokeDescription(Path().apply { moveTo(point.x, point.y) }, 0, duration)).build()
        val token = generation
        val accepted = runCatching { dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                if (busy && generation == token) next()
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                if (busy && generation == token) abort("点击被取消")
            }
        }, handler) }.getOrDefault(false)
        if (!accepted) abort("系统拒绝执行点击")
    }

    private fun prompt(message: String, label: String, action: () -> Unit) {
        runCatching { overlay.show(message, { abort("已取消") }, label, {
            if (busy) action()
        }) }.onFailure { abort("无法显示操作提示") }
    }

    private fun pick(message: String, back: (() -> Unit)? = null, action: (Float, Float) -> Unit) {
        runCatching { overlay.show(message, { abort("已取消") }, pick = { x, y ->
            // Let WindowManager remove the picker before inspecting the underlying window.
            if (busy) later(150) { action(x, y) }
        }, back = back) }.onFailure { abort("无法显示校准提示") }
    }

    private fun later(delay: Long, action: () -> Unit) {
        val token = generation
        handler.postDelayed({ if (busy && generation == token) action() }, delay)
    }

    private fun finish(message: String?, logMessage: String? = null) {
        busy = false
        generation++
        handler.removeCallbacksAndMessages(null)
        overlay.close()
        if (logMessage != null) Log.d(TAG, logMessage)
        if (message != null) Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun abort(reason: String) { finish(reason, "abort: $reason") }
    private fun longPressDuration() = (ViewConfiguration.getLongPressTimeout() + 300L).coerceAtLeast(800L)
    private fun clickDuration(step: CallStep) = if (step == CallStep.INPUT) longPressDuration() else 80L

    private fun settleMs(step: CallStep) = when (step) {
        CallStep.SEARCH -> 300L
        CallStep.INPUT -> 600L
        CallStep.PASTE -> 500L
        CallStep.RESULT -> 800L
        CallStep.MORE -> 700L
        CallStep.VIDEO -> 500L
        CallStep.CONFIRM -> 400L
    }

    private fun wechatWindow(): AccessibilityWindowInfo? {
        if (getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked) return null
        val current = windows
        fun belongsToWechat(window: AccessibilityWindowInfo): Boolean {
            val owner = window.root?.packageName?.toString()
            return owner == CallFlowActivity.WX_PACKAGE ||
                (owner == null && window.type == AccessibilityWindowInfo.TYPE_APPLICATION &&
                    window.id == observedWechatWindow)
        }
        // A notification shade or permission dialog can cover WeChat without being TYPE_APPLICATION.
        val blocked = current.any {
            (it.isActive || it.isFocused) && it.type != AccessibilityWindowInfo.TYPE_INPUT_METHOD &&
                !(it.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY &&
                    it.root?.packageName?.toString() == packageName) &&
                !belongsToWechat(it)
        }
        if (blocked) {
            if (overlay.showing) logWindows(current, "blocked")
            return null
        }
        // Vendor paste toolbars (Huawei HwFloatingToolbar) are extra WeChat windows that take focus.
        // Pick the largest WeChat application window so they cannot replace the main frame.
        return current.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && belongsToWechat(it) }
            .maxByOrNull { windowArea(it) }
            ?: current.firstOrNull {
                it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.id == observedWechatWindow
            }?.also { logWindows(current, "fallback") }
            ?: run { if (overlay.showing) logWindows(current, "none"); null }
    }

    private fun windowArea(window: AccessibilityWindowInfo): Long = Rect().also {
        window.getBoundsInScreen(it)
    }.let { it.width().toLong() * it.height() }

    private fun logWindows(list: List<AccessibilityWindowInfo>, reason: String) {
        Log.d(TAG, "windows=$reason")
        list.forEach {
            val r = Rect().also { rect -> it.getBoundsInScreen(rect) }
            Log.d(TAG, "  id=${it.id} type=${it.type} active=${it.isActive} focused=${it.isFocused} " +
                "layer=${it.layer} owner=${it.root?.packageName} bounds=$r")
        }
    }

    private fun wechatFrame(): ScreenFrame? {
        val rect = Rect()
        val window = wechatWindow() ?: return null
        window.getBoundsInScreen(rect)
        return ScreenFrame(rect.left, rect.top, rect.right, rect.bottom).takeIf { it.width > 0 && it.height > 0 }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Window identity and activity names remain available even when the node tree is null.
        if (event?.packageName?.toString() == CallFlowActivity.WX_PACKAGE) {
            if (event.windowId >= 0) observedWechatWindow = event.windowId
            event.className?.toString()?.takeIf { it.startsWith("com.tencent.mm") }?.let { wechatClass = it }
        }
    }
    override fun onInterrupt() { abort("无障碍服务已中断") }
    override fun onServiceConnected() { super.onServiceConnected(); instance = this }
    override fun onDestroy() { finish(null); instance = null; super.onDestroy() }

    companion object {
        private const val TAG = "WxVideoCall"
        private const val STEP_TIMEOUT = 15_000L
        @Volatile var instance: WxAccessibilityService? = null
            private set
    }
}
