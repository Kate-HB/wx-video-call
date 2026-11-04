package com.forolderr.wxvideo

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.view.WindowManager
import org.json.JSONArray
import org.json.JSONObject

enum class CallStep(val instruction: String, val debugName: String) {
    SEARCH("先回到微信首页，再轻点搜索按钮", "search"),
    INPUT("轻点空白搜索输入框；助手会执行长按", "longPress"),
    PASTE("等待粘贴菜单出现，轻点「粘贴」", "input"),
    RESULT("等待搜索完成，轻点第一条联系人结果（不要点搜索框）", "result"),
    MORE("核对聊天对象，轻点聊天栏的「+」", "more"),
    VIDEO("轻点展开面板里的「视频通话」", "video"),
    CONFIRM("轻点弹出菜单的「视频通话」；只保存位置，不会拨出", "confirm")
}

data class CalibrationTarget(val point: CalibratedPoint)

class CalibrationStore(private val context: Context) {
    // Device-level layout calibration: one run applies to every contact.
    private val file = java.io.File(context.noBackupFilesDir, "wechat-calibration-v1.json")

    @Suppress("DEPRECATION")
    fun signature(): String {
        val config = context.resources.configuration
        val wm = context.getSystemService(WindowManager::class.java)
        val metrics = android.util.DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(metrics)
        val wx = runCatching { context.packageManager.getPackageInfo(CallFlowActivity.WX_PACKAGE, 0) }.getOrNull()
        val version = if (Build.VERSION.SDK_INT >= 28) wx?.longVersionCode else wx?.versionCode?.toLong()
        val nav = Settings.Secure.getString(context.contentResolver, "navigation_mode")
        return listOf(Build.FINGERPRINT, metrics.widthPixels, metrics.heightPixels,
            metrics.densityDpi, config.fontScale, config.orientation, config.uiMode,
            config.locales.toLanguageTags(), version, nav).joinToString("|")
    }

    fun load(step: CallStep): CalibrationTarget? = runCatching {
        val root = JSONObject(file.readText())
        if (!calibrationMatches(root.getString("signature"), signature())) return null
        val steps = root.optJSONObject("steps") ?: legacySteps(root) ?: return null
        val obj = steps.optJSONObject(step.name) ?: return null
        val frame = obj.getJSONArray("frame")
        val point = CalibratedPoint.capture(obj.getDouble("x").toFloat(), obj.getDouble("y").toFloat(),
            ScreenFrame(frame.getInt(0), frame.getInt(1), frame.getInt(2), frame.getInt(3))) ?: return null
        CalibrationTarget(point)
    }.getOrNull()

    /** Older builds stored one calibration per contact; reuse the first complete one. */
    private fun legacySteps(root: JSONObject): JSONObject? {
        val contacts = root.optJSONObject("contacts") ?: return null
        return contacts.keys().asSequence().mapNotNull { contacts.optJSONObject(it) }
            .firstOrNull { steps -> CallStep.entries.all { steps.has(it.name) } }
    }

    fun save(signature: String, targets: Map<CallStep, CalibratedPoint>) {
        require(targets.keys.containsAll(CallStep.entries))
        require(calibrationMatches(signature, signature()))
        val steps = JSONObject()
        targets.forEach { (step, p) ->
            steps.put(step.name, JSONObject().put("x", p.x).put("y", p.y)
                .put("frame", JSONArray(listOf(p.frame.left, p.frame.top, p.frame.right, p.frame.bottom))))
        }
        val atomic = android.util.AtomicFile(file)
        val out = atomic.startWrite()
        try {
            out.write(JSONObject().put("signature", signature).put("steps", steps).toString().toByteArray())
            atomic.finishWrite(out)
        } catch (e: Exception) {
            atomic.failWrite(out)
            throw e
        }
    }

    fun clear() { android.util.AtomicFile(file).delete() }

    fun hasCalibration(): Boolean = CallStep.entries.all { load(it) != null }

    /** Small JSON (coordinates only), safe to move between devices of the same model/config. */
    fun exportJson(): String? = runCatching { file.readText() }.getOrNull()

    fun importJson(text: String): Boolean = runCatching {
        val root = JSONObject(text)
        if (!calibrationMatches(root.optString("signature"), signature())) return false
        val steps = root.optJSONObject("steps") ?: legacySteps(root) ?: return false
        val normalized = JSONObject().put("signature", signature()).put("steps", steps)
        val atomic = android.util.AtomicFile(file)
        val out = atomic.startWrite()
        try {
            out.write(normalized.toString().toByteArray())
            atomic.finishWrite(out)
        } catch (e: Exception) {
            atomic.failWrite(out)
            throw e
        }
        true
    }.getOrDefault(false)
}
