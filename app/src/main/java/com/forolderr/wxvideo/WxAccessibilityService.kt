package com.forolderr.wxvideo

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Path
import android.os.Build
import android.view.WindowManager
import android.view.WindowInsets
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class WxAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var busy = false
    private var dryRun = false
    private var stopAfter: String? = null

    fun startCall(contact: String, dryRun: Boolean = false, stopAfter: String? = null): Boolean {
        if (busy) return false
        busy = true
        getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("contact", contact))
        this.dryRun = dryRun
        this.stopAfter = stopAfter
        log("startCall: $contact")
        handler.postDelayed({ resetToMain(contact, 0) }, 1500)
        return true
    }

    private fun resetToMain(contact: String, attempts: Int) {
        if (!busy) return
        if (rootInActiveWindow?.packageName?.toString() == CallFlowActivity.WX_PACKAGE) {
            if (attempts >= 8) { abort("Cannot return to WeChat home"); return }
            if (!performGlobalAction(GLOBAL_ACTION_BACK)) { abort("Back rejected"); return }
            handler.postDelayed({ resetToMain(contact, attempts + 1) }, 500)
        } else {
            val launch = packageManager.getLaunchIntentForPackage(CallFlowActivity.WX_PACKAGE)
            if (launch == null) { abort("WeChat unavailable"); return }
            launch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launch)
            handler.postDelayed({ stepSearchEntry(contact, System.currentTimeMillis() + STEP_TIMEOUT) }, 1400)
        }
    }

    private fun stepSearchEntry(contact: String, deadline: Long) {
        if (expired(deadline)) return
        val node = find {
            it.text?.toString() == "搜索" ||
                it.contentDescription?.toString()?.contains("搜索") == true
        }
        if (node != null && click(node)) {
            log("clicked search entry")
            handler.postDelayed({
                stepInput(contact, System.currentTimeMillis() + STEP_TIMEOUT)
            }, 800)
        } else {
            gesture(1048f, 215f) {
                after("search", 1000) { stepInput(contact, System.currentTimeMillis() + STEP_TIMEOUT) }
            }
        }
    }

    private fun stepInput(contact: String, deadline: Long) {
        if (expired(deadline)) return
        val edit = find {
            it.isEditable || it.className?.toString()?.contains("EditText") == true
        }
        if (edit != null) {
            edit.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, contact)
            }
            edit.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            log("set text: $contact")
            handler.postDelayed({
                stepResult(contact, System.currentTimeMillis() + STEP_TIMEOUT)
            }, 1200)
        } else {
            gesture(340f, 246f, 800) {
                after("longPress", 700) {
                    gesture(150f, 445f) {
                        after("input", 1800) { stepResult(contact, System.currentTimeMillis() + STEP_TIMEOUT) }
                    }
                }
            }
        }
    }

    private fun stepResult(contact: String, deadline: Long) {
        if (expired(deadline)) return
        val node = find { it.text?.toString() == contact }
        if (node != null && click(node)) {
            log("clicked result: $contact")
            handler.postDelayed({
                stepMoreButton(contact, System.currentTimeMillis() + STEP_TIMEOUT)
            }, 1500)
        } else {
            gesture(360f, 570f) {
                after("result", 1400) { stepMoreButton(contact, System.currentTimeMillis() + STEP_TIMEOUT) }
            }
        }
    }

    private fun stepMoreButton(contact: String, deadline: Long) {
        if (expired(deadline)) return
        val node = find {
            it.contentDescription?.toString()?.contains("更多") == true
        }
        if (node != null && click(node)) {
            log("clicked more button")
            handler.postDelayed({
                stepVideoEntry(contact, System.currentTimeMillis() + STEP_TIMEOUT)
            }, 800)
        } else {
            gesture(1190f, 2695f) {
                after("more", 800) { stepVideoEntry(contact, System.currentTimeMillis() + STEP_TIMEOUT) }
            }
        }
    }

    private fun stepVideoEntry(contact: String, deadline: Long) {
        if (expired(deadline)) return
        val node = find { it.text?.toString() == "视频通话" }
        if (node != null && click(node)) {
            log("clicked video entry")
            handler.postDelayed({
                stepVideoConfirm(contact, System.currentTimeMillis() + STEP_TIMEOUT)
            }, 800)
        } else {
            gesture(775f, 1870f) {
                after("video", 800) { stepVideoConfirm(contact, System.currentTimeMillis() + STEP_TIMEOUT) }
            }
        }
    }

    private fun stepVideoConfirm(contact: String, deadline: Long) {
        if (expired(deadline)) return
        if (dryRun) {
            log("dryRun: stopped before final call")
            busy = false
            return
        }
        val node = find { it.text?.toString() == "视频通话" }
        if (node != null && click(node)) {
            log("clicked video confirm")
            busy = false
        } else {
            gesture(630f, 2310f) {
                log("video confirm dispatched")
                busy = false
            }
        }
    }

    // Coordinates calibrated on 1264 x 2780; scale against physical display bounds.
    private fun gesture(x: Float, y: Float, duration: Long = 80, next: () -> Unit) {
        if (!busy) return
        if (rootInActiveWindow?.packageName?.toString() != CallFlowActivity.WX_PACKAGE) {
            abort("WeChat is not foreground")
            return
        }
        val wm = getSystemService(WindowManager::class.java)
        val metrics = if (Build.VERSION.SDK_INT >= 30) wm.maximumWindowMetrics else null
        val bounds = if (Build.VERSION.SDK_INT >= 30) metrics!!.bounds else null
        val width = bounds?.width() ?: resources.displayMetrics.widthPixels
        val height = bounds?.height() ?: resources.displayMetrics.heightPixels
        val bottom = if (Build.VERSION.SDK_INT >= 30) {
            metrics!!.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars()).bottom
        } else {
            val id = resources.getIdentifier("navigation_bar_height", "dimen", "android")
            if (id != 0) resources.getDimensionPixelSize(id) else 0
        }
        val targetY = y / 2780f * height - if (y > 1000f) bottom else 0
        log("gesture: $x,$y -> ${x / 1264f * width},$targetY nav=$bottom")
        val path = Path().apply { moveTo(x / 1264f * width, targetY) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration)).build()
        val accepted = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) { if (busy) next() }
            override fun onCancelled(gestureDescription: GestureDescription?) { abort("Gesture cancelled") }
        }, handler)
        if (!accepted) abort("Gesture rejected")
    }

    private fun after(stage: String, delay: Long, next: () -> Unit) {
        log("gesture stage: $stage")
        if (stopAfter == stage) { busy = false; return }
        handler.postDelayed({ if (busy) next() }, delay)
    }

    private fun abort(reason: String) {
        log(reason)
        busy = false
        handler.removeCallbacksAndMessages(null)
    }

    private fun expired(deadline: Long): Boolean {
        if (System.currentTimeMillis() > deadline) {
            log("step timeout, abort")
            busy = false
            return true
        }
        return false
    }

    private fun find(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        if (root.packageName?.toString() != CallFlowActivity.WX_PACKAGE) return null
        return findRecursive(root, predicate)
    }

    private fun findRecursive(
        node: AccessibilityNodeInfo?,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        if (node == null) return null
        if (predicate(node)) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findRecursive(child, predicate)?.let { return it }
        }
        return null
    }

    private fun click(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable) {
                return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            current = current.parent
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun log(msg: String) {
        Log.d(TAG, msg)
    }

    private fun dumpDiag() {
        Log.d(TAG, "diag: rootInActiveWindow=$rootInActiveWindow")
        try {
            val wins = windows
            Log.d(TAG, "diag: windows=${wins?.size}")
            wins?.forEachIndexed { i, w ->
                Log.d(TAG, "diag: win[$i] type=${w.type} active=${w.isActive} focused=${w.isFocused} root=${w.root}")
                dumpTree(w.root)
            }
        } catch (e: Exception) {
            Log.d(TAG, "diag error: $e")
        }
    }

    private fun dumpTree(node: AccessibilityNodeInfo?, depth: Int = 0) {
        if (node == null || depth > 40) return
        val pad = "  ".repeat(depth)
        Log.d(
            TAG,
            "$pad${node.className} text=${node.text} desc=${node.contentDescription} " +
                "id=${node.viewIdResourceName} click=${node.isClickable} edit=${node.isEditable}"
        )
        for (i in 0 until node.childCount) {
            dumpTree(node.getChild(i), depth + 1)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() { abort("Service stopped") }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        log("service connected")
    }

    override fun onDestroy() {
        abort("Service stopped")
        instance = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "WxVideoCall"
        private const val STEP_TIMEOUT = 12_000L

        @Volatile
        var instance: WxAccessibilityService? = null
            private set
    }
}
