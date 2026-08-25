package com.tylor.memmos.ui.login

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.tylor.memmos.net.XhsFetcher
import com.tylor.memmos.ui.theme.Ink
import com.tylor.memmos.ui.theme.Success
import com.tylor.memmos.ui.theme.TextFaint
import com.tylor.memmos.ui.theme.TextHi

/**
 * 小红书登录（方案 A）：WebView 打开小红书，扫码/手机号登录一次，
 * Cookie 由 WebView 全局 CookieManager 持久化——之后抓取管线自动携带登录态。
 * 登录成功的判定：cookie 中出现 web_session。
 *
 * 模拟电脑（用户要求）：手机版网页没有网页登录入口，必须用桌面 UA 让服务器返回
 * PC 版页面——PC 版才有「扫码/手机号登录」浮层；配合宽视口+缩放，手机屏幕也能看清二维码。
 */
class XhsLoginActivity : ComponentActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var loggedIn by mutableStateOf(false)
    private var polling = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CookieManager.getInstance().setAcceptCookie(true)
        loggedIn = hasSession()
        setContent { LoginScreen() }
    }

    private fun hasSession(): Boolean {
        val cookie = CookieManager.getInstance().getCookie("https://www.xiaohongshu.com").orEmpty()
        return cookie.contains("web_session=")
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Composable
    private fun LoginScreen() {
        Column(
            Modifier
                .fillMaxSize()
                .background(Ink)
                .statusBarsPadding(),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "返回",
                    color = TextHi, fontSize = 14.sp,
                    modifier = Modifier.clickable { finish() },
                )
                Spacer(Modifier.weight(1f))
                Text("小红书登录", color = TextHi, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(
                    if (loggedIn) "已登录" else "未登录",
                    color = if (loggedIn) Success else TextFaint,
                    fontSize = 13.sp,
                )
            }

            if (loggedIn) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("登录成功，Cookie 已保存（约 30–90 天有效）", color = TextHi, fontSize = 13.sp)
                    Text("返回后即可正常抓取笔记与评论。", color = TextFaint, fontSize = 12.sp)
                }
            } else {
                Text(
                    "下方已是电脑版页面：点击「登录」，用手机小红书 App 扫码（或输手机号）。\n二维码太小就双指放大。",
                    color = TextFaint, fontSize = 12.sp, lineHeight = 18.sp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
                )
            }

            // WebView：登录页 + 登录后自动跳回笔记页
            AndroidView(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                factory = { context ->
                    WebView(context).apply {
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            // 模拟电脑（用户要求）：桌面 UA 才返回 PC 版登录页（扫码/手机号入口）
                            userAgentString = XhsFetcher.DESKTOP_UA
                            // 桌面页面按自然宽度排版并整体缩放到屏宽，再靠双指缩放看清二维码
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false
                        }
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = WebViewClient()
                        webChromeClient = WebChromeClient()
                        loadUrl("https://www.xiaohongshu.com/explore")
                        startPolling()
                    }
                },
            )

            Text(
                "本页模拟电脑打开小红书网页（手机版没有网页登录入口）。Cookie 仅保存在本机，用于抓取你可见的笔记内容。",
                fontSize = 10.sp, color = TextFaint,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }

    private fun startPolling() {
        if (polling) return
        polling = true
        handler.postDelayed(object : Runnable {
            override fun run() {
                loggedIn = hasSession()
                if (!loggedIn) handler.postDelayed(this, 1500)
            }
        }, 1500)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        // 持久化 Cookie 到磁盘
        CookieManager.getInstance().flush()
        super.onDestroy()
    }
}
