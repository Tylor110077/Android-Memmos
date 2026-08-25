package com.tylor.memmos.ui.fetch

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.tylor.memmos.data.ClipComment
import com.tylor.memmos.data.ClipNote
import com.tylor.memmos.data.ClipStore
import com.tylor.memmos.net.XhsFetcher
import com.tylor.memmos.ui.theme.Ink
import com.tylor.memmos.ui.theme.TextHi
import com.tylor.memmos.ui.theme.TextMid
import com.tylor.memmos.util.AppPrefs
import com.tylor.memmos.util.VideoSaverService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/**
 * 抓取页（方案 A · WebView 渲染管线）：
 * 透明 Activity + 隐藏 WebView 以登录态渲染笔记页（评论由页面 JS 异步加载），
 * 轮询就绪后注入 JS 从 DOM 提取标题/正文/图集/评论，失败回退 HTTP 解析（XhsFetcher）。
 * 登录态来自 XhsLoginActivity 的 Cookie（WebView 全局 CookieManager 持久化）。
 */
class ClipFetchActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private val handler = Handler(Looper.getMainLooper())
    private var handled = false
    private var attempts = 0
    private var pageLoaded = false
    private var noteUrl = ""
    /** 网络拦截收集的视频流地址（页面用 blob 播放时，真地址只出现在网络请求里） */
    private val videoCandidates = java.util.concurrent.CopyOnWriteArrayList<String>()
    /** 网络拦截收集的封面图地址（sns-webpic；登录态页面 JSON 可能解析失败时的兜底） */
    private val coverCandidates = java.util.concurrent.CopyOnWriteArrayList<String>()

    private val uiState = mutableStateOf("准备中…")
    /** 重复剪藏待确认（同 id 已存在时弹出对话框，用户确认后才覆盖更新） */
    private val pendingDup = mutableStateOf<ClipNote?>(null)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val shared = intent.getStringExtra(EXTRA_TEXT) ?: intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val url = XhsFetcher.extractUrl(shared)
        if (url == null) {
            finish()
            return
        }
        noteUrl = url

        CookieManager.getInstance().setAcceptCookie(true)

        // UI（Compose）+ 隐藏 WebView 同层：WebView 全屏透明垫底，Compose 状态卡浮于其上
        val root = android.widget.FrameLayout(this)
        webView = WebView(this)
        setupWebView(webView)
        root.addView(
            webView,
            android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        val compose = androidx.compose.ui.platform.ComposeView(this)
        compose.setContent {
            FetchScreen(
                state = uiState,
                dup = pendingDup,
                onConfirm = { applyConfirmed(it) },
                onCancel = { finish() },
            )
        }
        root.addView(
            compose,
            android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        setContentView(root)

        uiState.value = "加载笔记页…"
        webView.loadUrl(url)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView(view: WebView) {
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            userAgentString = XhsFetcher.DESKTOP_UA
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, true)
        view.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                // 只收集不拦截：必须返回 null 放行；匹配 sns-video/xhscdn 流与 sns-webpic 封面
                val u = request.url.toString()
                if (XhsDomCapture.VIDEO_URL.find(u) != null) videoCandidates.add(u)
                if (XhsDomCapture.COVER_URL.find(u) != null) coverCandidates.add(u)
                return null
            }

            override fun onPageFinished(v: WebView, url: String) {
                if (url.contains("xiaohongshu.com") && !pageLoaded) {
                    pageLoaded = true
                    uiState.value = "等待评论渲染…"
                    handler.postDelayed({ checkReady() }, 1200)
                }
            }
        }
        view.webChromeClient = WebChromeClient()
    }

    /* ── 就绪轮询：正文出现后进入滚动加载（评论区懒加载，不滚只能拿到首批 ~26 条） ── */
    private fun checkReady() {
        if (handled) return
        attempts++
        webView.evaluateJavascript(XhsDomCapture.READY_JS) { res ->
            if (handled) return@evaluateJavascript
            runCatching {
                val o = JSONObject(JSONTokener(res).nextValue().toString())
                val hasDesc = o.optBoolean("hasDesc")
                if (hasDesc) {
                    // 交给滚动阶段：滚到底直到条数稳定再提取（无评论 2-4 轮即放行）；
                    // 不能提前置 handled=true，否则 scrollForComments 会直接返回
                    uiState.value = "加载评论中…"
                    scrollForComments(0, -1)
                } else if (attempts >= 6) {
                    // 正文 6s 内没就绪（登录浮窗/风控页）不再空等，直接降级 HTTP
                    handled = true
                    uiState.value = "页面数据不完整，尝试降级抓取…"
                    fallbackHttp()
                } else {
                    handler.postDelayed({ checkReady() }, 1000)
                }
            }.onFailure { fallbackHttp() }
        }
    }

    /* ── 评论懒加载滚动：滚动分区容器+展开楼中楼，条数连续两轮不变即稳定 ── */
    private fun scrollForComments(round: Int, prevCount: Int) {
        if (handled) return
        webView.evaluateJavascript(XhsDomCapture.SCROLL_AND_COUNT_JS) { res ->
            if (handled) return@evaluateJavascript
            val count = runCatching {
                JSONObject(JSONTokener(res).nextValue().toString()).optInt("comments")
            }.getOrDefault(prevCount)
            val stable = count == prevCount
            // 稳定(≥2轮)或 0 评论 4 轮或总 14 轮（≈9.8s）→ 提取；
            // 50 条以上评论的笔记需要多轮滚动+点开回复才能加载全
            val done = round >= 14 || (round >= 2 && stable) || (count == 0 && round >= 4)
            if (done) {
                handled = true
                uiState.value = if (count > 0) "提取正文与 $count 条评论…" else "提取正文…"
                extractNow()
            } else {
                handler.postDelayed({ scrollForComments(round + 1, count) }, 700)
            }
        }
    }

    private fun extractNow() {
        webView.evaluateJavascript(XhsDomCapture.EXTRACT_JS) { res ->
            if (handled && res != "null") {
                val parsed = runCatching {
                    // evaluateJavascript 返回的是「字符串的 JSON 编码」，先解一层再解析
                    val inner = JSONTokener(res).nextValue().toString()
                    JSONObject(inner)
                }.getOrNull()
                if (parsed != null) {
                    // 调试：把评论区 HTML 落盘，便于校准选择器（真实登录态页面结构）
                    parsed.optString("commentHtml").takeIf { it.isNotBlank() }?.let { dump ->
                        runCatching { java.io.File(filesDir, "comment_dump.html").writeText(dump) }
                    }
                    // 视频类型只认真实可下载地址：页面上任意 blob: 视频元素（推荐位/广告）
                    // 都会让「字段非空」误判成视频笔记，必须过滤掉
                    val jsVideo = parsed.optString("video").takeIf { it.startsWith("http") }
                    val netVideo = jsVideo ?: videoCandidates.firstOrNull { it.startsWith("http") }
                    val note = ClipNote(
                        id = parsed.optString("noteId")
                            .ifBlank { noteUrl.substringAfterLast('/').substringBefore('?') },
                        title = parsed.optString("title").ifBlank { "未命名笔记" },
                        desc = parsed.optString("desc"),
                        author = parsed.optString("author"),
                        avatarUrl = parsed.optString("avatar"),
                        tags = parsed.getJSONArray("tags").let { a -> List(a.length()) { a.getString(it) } },
                        imageUrls = parsed.getJSONArray("images").let { a -> List(a.length()) { a.getString(it) } },
                        videoUrl = netVideo,
                        type = if (netVideo != null) "video" else "normal",
                        pageUrl = noteUrl,
                        clippedAt = System.currentTimeMillis(),
                        comments = parsed.getJSONArray("comments").let { arr ->
                            List(arr.length()) { i ->
                                val c = arr.getJSONObject(i)
                                ClipComment(
                                    nickname = c.optString("nickname"),
                                    avatar = c.optString("avatar"),
                                    content = c.optString("content"),
                                    likes = c.optInt("likes"),
                                    subComments = c.getJSONArray("subs").let { sa ->
                                        List(sa.length()) { j ->
                                            val sc = sa.getJSONObject(j)
                                            ClipComment(
                                                nickname = sc.optString("nickname"),
                                                avatar = sc.optString("avatar"),
                                                content = sc.optString("content"),
                                            )
                                        }
                                    },
                                )
                            }
                        },
                    )
                    mergeAndSave(note)
                    return@evaluateJavascript
                }
            }
            fallbackHttp()
        }
    }

    /**
     * 双路线合并（用户要求）：WebView（浏览器方式）拿登录态的评论/正文/图集，
     * XhsFetcher（importer 方式）从 __INITIAL_STATE__ 拿视频真实地址——
     * 登录态 PC 页的 <video> src 常是 blob: 动态流，DOM 提取不出可下载链接，
     * 而 note.video.media.stream.h264[0].masterUrl 始终在页面 JSON 里。
     * httpNote 失败时仍保留 DOM 结果（评论优先用 DOM，视频/缺失字段取 http 补）。
     */
    private fun mergeAndSave(domNote: ClipNote) {
        uiState.value = "合并抓取中（视频走 __INITIAL_STATE__）…"
        lifecycleScope.launch {
            val (httpNote, httpErr) = withContext(Dispatchers.IO) {
                runCatching { XhsFetcher.fetch(" $noteUrl") }
                    .fold({ it to null }, { null to it })
            }
            android.util.Log.d(
                "MemmosDbg",
                "merge: dom(video=${domNote.videoUrl != null}, imgs=${domNote.imageUrls.size}) " +
                    "http(video=${httpNote?.videoUrl != null}, imgs=${httpNote?.imageUrls?.size}) err=${httpErr?.message}",
            )
            val merged = domNote.copy(
                title = domNote.title.takeIf { it != "未命名笔记" }
                    ?: httpNote?.title.orEmpty().ifBlank { domNote.title },
                desc = domNote.desc.ifBlank { httpNote?.desc.orEmpty() },
                author = domNote.author.ifBlank { httpNote?.author.orEmpty() },
                avatarUrl = domNote.avatarUrl.ifBlank { httpNote?.avatarUrl.orEmpty() },
                tags = domNote.tags.ifEmpty { httpNote?.tags.orEmpty() },
                comments = domNote.comments.ifEmpty { httpNote?.comments.orEmpty() },
                // 封面链：DOM → 网络拦截的 sns-webpic → __INITIAL_STATE__（JSON 解析失败仍能兜住）
                imageUrls = domNote.imageUrls.ifEmpty {
                    val c = coverCandidates.firstOrNull { it.startsWith("http") }
                    if (c != null) listOf(c) else httpNote?.imageUrls.orEmpty()
                },
                videoUrl = domNote.videoUrl ?: httpNote?.videoUrl,
                type = if ((domNote.videoUrl ?: httpNote?.videoUrl) != null) "video" else domNote.type,
            )
            saveAndFinish(merged, viaWeb = true)
        }
    }

    /** HTTP 降级：无登录态/WebView 失败时的旧路线（无评论） */
    private fun fallbackHttp() {
        uiState.value = "降级抓取中…"
        lifecycleScope.launch {
            runCatching { XhsFetcher.fetch(" $noteUrl") }
                .onSuccess { note -> saveAndFinish(note, viaWeb = false) }
                .onFailure { e ->
                    uiState.value = "抓取失败：${e.message}"
                    handler.postDelayed({ finish() }, 1800)
                }
        }
    }

    private fun saveAndFinish(note: ClipNote, viaWeb: Boolean) {
        // 重复剪藏提醒（用户要求）：同 id 已存在 → 先确认再更新，不静默覆盖
        val existed = ClipStore(this).load().any { it.id == note.id }
        if (existed) {
            uiState.value = "发现重复剪藏，等待确认…"
            pendingDup.value = note
            return
        }
        applySave(note, viaWeb, existed = false)
    }

    /** 用户确认「仍然保存」后的落库 */
    private fun applyConfirmed(note: ClipNote) {
        pendingDup.value = null
        applySave(note, viaWeb = true, existed = true)
    }

    private fun applySave(note: ClipNote, viaWeb: Boolean, existed: Boolean) {
        // 防崩溃：保存失败只提示不闪退（用户反馈过闪退）
        val saveOk = runCatching {
            val store = ClipStore(this)
            val list = store.load()
            val merged = (listOf(note) + list.filter { it.id != note.id }).toMutableList()
            store.save(merged)
            true
        }.getOrDefault(false)
        setResult(RESULT_OK)
        if (!saveOk) {
            uiState.value = "抓取成功但保存失败，请重试"
            handler.postDelayed({ finish() }, 1800)
            return
        }

        // 视频自动下载（设置开关）：交给后台前台服务，页面显示提示后即关闭——
        // 进度条在通知栏实时更新，切走也不中断（用户要求后台存储）
        if (note.videoUrl != null && AppPrefs.autoDownloadVideo(this)) {
            uiState.value = if (existed) "已更新剪藏「${note.title.take(14)}」，视频后台下载中…"
            else "已保存「${note.title.take(14)}」，视频后台下载中…（进度见通知栏）"
            VideoSaverService.start(this, note.id)
            handler.postDelayed({ finish() }, 1600)
        } else {
            uiState.value = when {
                !saveOk -> "保存失败"
                existed -> "已更新剪藏「${note.title.take(14)}」"
                viaWeb -> "已保存到剪藏库"
                else -> "已保存（降级模式，无评论）"
            }
            handler.postDelayed({ finish() }, 1200)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (this::webView.isInitialized) {
            webView.destroy()
        }
        super.onDestroy()
    }

    @Composable
    private fun FetchScreen(
        state: androidx.compose.runtime.State<String>,
        dup: androidx.compose.runtime.State<ClipNote?>,
        onConfirm: (ClipNote) -> Unit,
        onCancel: () -> Unit,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xB3101218)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp)
                    .background(Color(0xFF1A1D26), RoundedCornerShape(18.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("正在抓取笔记", color = TextHi, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                val st by state
                Text(
                    st, color = TextMid, fontSize = 13.sp, lineHeight = 19.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // 重复剪藏确认（用户要求：重复内容要提醒）
        dup.value?.let { d ->
            AlertDialog(
                onDismissRequest = onCancel,
                containerColor = Color(0xFF1A1D26),
                titleContentColor = TextHi,
                textContentColor = TextMid,
                title = { Text("提醒：重复剪藏", fontSize = 17.sp, fontWeight = FontWeight.Bold) },
                text = { Text("「${d.title.take(20)}」已在剪藏库中。\n再次抓取将覆盖更新原记录，是否继续？", fontSize = 13.sp, lineHeight = 19.sp) },
                confirmButton = {
                    TextButton(onClick = { onConfirm(d) }) {
                        Text("仍然保存", color = Color(0xFF8B7BFF), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = onCancel) { Text("取消", color = TextMid) }
                },
            )
        }
    }

    companion object {
        const val EXTRA_TEXT = "text"
    }
}
