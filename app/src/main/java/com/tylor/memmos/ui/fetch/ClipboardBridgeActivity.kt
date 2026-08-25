package com.tylor.memmos.ui.fetch

import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.tylor.memmos.net.XhsFetcher

/**
 * 透明桥接页（不可见）：悬浮窗按钮在后台读不到剪贴板（Android 10+ 限制）时，
 * 前台（本 Activity 无 UI、透明主题）合法读取 → 启动 XhsCaptureService 后立即退出。
 * 用户全程无感知「跳转」，宿主 App 保持前台。
 */
class ClipboardBridgeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val clip = runCatching {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
        }.getOrDefault("")
        val url = XhsFetcher.extractUrl(clip)
        if (url != null) XhsCaptureService.start(this, clip)
        finish()
    }
}
