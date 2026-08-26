package com.tylor.memmos.ui.fetch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.tylor.memmos.MainActivity
import com.tylor.memmos.R
import com.tylor.memmos.data.ClipComment
import com.tylor.memmos.data.ClipNote
import com.tylor.memmos.data.ClipStore
import com.tylor.memmos.net.AiSummaryRunner
import com.tylor.memmos.net.XhsFetcher
import com.tylor.memmos.util.AppPrefs
import com.tylor.memmos.util.VideoSaverService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONTokener

/**
 * 后台抓取前台服务（用户要求「不跳转、后台完成、悬浮窗显示进度」）：
 * 悬浮窗按钮 → 本服务内跑完整管线（隐藏 WebView 渲染→评论滚动→DOM 提取→
 * XhsFetcher 合并→落库→视频交 VideoSaverService），浮动面板/通知栏实时显示进度条。
 * 页面流程为唯一抓取管线（XhsDomCapture 共享 JS）。
 */
class XhsCaptureService : Service() {

    companion object {
        data class CaptureState(
            val running: Boolean = false,
            val progress: Float = 0f,
            val status: String = "",
            val done: Boolean? = null, // true=成功 false=失败 null=进行中
        )

        /** 全局唯一抓取：面板/通知观察用 */
        val state = MutableStateFlow(CaptureState())

        private const val CHANNEL = "memmos_capture"
        private const val NOTIF_ID = 2002
        private const val EXTRA_TEXT = "text"

        fun start(ctx: Context, text: String) {
            ctx.startForegroundService(
                Intent(ctx, XhsCaptureService::class.java).putExtra(EXTRA_TEXT, text),
            )
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val videoCandidates = java.util.concurrent.CopyOnWriteArrayList<String>()

    /** 只认"标准正文图"形态：sns-webpic + 路径含 1040g/1040h/1060g…或 noteImage——
     * 排除 glo/frame（小程序 frame）、oss-sg/notes（区域资源）、spectrum/notes_pre_post（预览卡）等
     * 非正文封面资源（这些形态每次加载都可能不同，导致"图片样式和数量不一样"） */
    private fun isNoteImage(u: String): Boolean {
        if (!u.startsWith("http") || !u.contains("sns-webpic")) return false
        val p = u.substringAfter(".com/")
        return p.contains("/1040g") || p.contains("/1040h") || p.contains("/1060g") ||
            p.contains("noteImage") || p.contains("/1040z") || p.contains("/1041g")
    }

    private val coverCandidates = java.util.concurrent.CopyOnWriteArrayList<String>()
    private var webView: WebView? = null
    /** 短链展开后的最终页 URL：DOM 路线落库 pageUrl 用长链（短链有时效） */
    private var webFinalUrl: String? = null
    private var noteUrl = ""
    private var attempts = 0
    private var handled = false
    /** 并发抓取请求排队（第二个请求被静默忽略=漏抓；改为串行处理） */
    private val pendingQueue = java.util.ArrayDeque<String>()
    private val nm get() = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val text = intent?.getStringExtra(EXTRA_TEXT).orEmpty()
        if (state.value.running || text.isBlank()) {
            if (text.isBlank()) {
                state.value = CaptureState(done = false, status = "未识别到链接")
                stopSelf()
            } else {
                // 正在抓另一条：入队排队（原来直接忽略 → 用户的第二条"漏抓"）。
                // 去重 + 上限 5：避免历史积压/重复请求堆积造成一次性补跑出几十条
                if (pendingQueue.none { it == text }) {
                    if (pendingQueue.size >= 5) pendingQueue.pollFirst()
                    pendingQueue.add(text)
                }
            }
            return START_NOT_STICKY
        }
        ensureChannel()
        state.value = CaptureState(running = true, progress = 0.05f, status = "准备抓取…", done = null)
        updateNotif(0.05f, "准备抓取…")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notif(0.05f, "准备抓取…"), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, notif(0.05f, "准备抓取…"))
        }
        handler.post { startCapture(text) }
        return START_NOT_STICKY
    }

    /* ───────────── 抓取管线（唯一实现，主页/悬浮窗共用） ───────────── */

    private fun startCapture(text: String) {
        val url = XhsFetcher.extractUrl(text)
        if (url == null) {
            fail("未识别到小红书分享链接")
            return
        }
        noteUrl = url
        update(0.12f, "加载笔记页…")
        val wv = WebView(this)
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            userAgentString = XhsFetcher.DESKTOP_UA
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
        wv.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val u = request.url.toString()
                if (XhsDomCapture.VIDEO_URL.find(u) != null) videoCandidates.add(u)
                // 封面候选同样过滤预览卡（notes_pre_post/spectrum 是分享/预览资源，不是正文封面——
                // 视频帖 DOM 无图时用它兜底会把封面挂成预览卡 → 内容与封面错位）
                if (XhsDomCapture.COVER_URL.find(u) != null && isNoteImage(u)) coverCandidates.add(u)
                return null
            }

            override fun onPageFinished(view: WebView, u: String) {
                if (u.contains("xiaohongshu.com")) {
                    webFinalUrl = u // 展开结果（含真实 noteId）作长链依据
                    android.util.Log.d("MemmosDbg", "capture final page: ${u.take(120)}")
                }
                if (u.contains("xiaohongshu.com") && !handled) {
                    update(0.22f, "就绪，加载评论…")
                    handler.postDelayed({ checkReady() }, 1200)
                }
            }
        }
        webView = wv
        wv.loadUrl(url)
    }

    private fun checkReady() {
        if (handled) return
        attempts++
        val wv = webView ?: return
        wv.evaluateJavascript(XhsDomCapture.READY_JS) { res ->
            if (handled) return@evaluateJavascript
            runCatching {
                val o = JSONObject(JSONTokener(res).nextValue().toString())
                val hasDesc = o.optBoolean("hasDesc")
                if (hasDesc) {
                    update(0.3f, "滚动加载评论…")
                    scrollForComments(0, -1)
                } else if (attempts >= 6) {
                    update(0.45f, "页面数据不完整，走 __INITIAL_STATE__…")
                    httpFallback()
                } else {
                    handler.postDelayed({ checkReady() }, 1000)
                }
            }.onFailure { httpFallback() }
        }
    }

    private fun scrollForComments(round: Int, prevCount: Int) {
        if (handled) return
        val wv = webView ?: return
        wv.evaluateJavascript(XhsDomCapture.SCROLL_AND_COUNT_JS) { res ->
            if (handled) return@evaluateJavascript
            val count = runCatching {
                JSONObject(JSONTokener(res).nextValue().toString()).optInt("comments")
            }.getOrDefault(prevCount)
            val stable = count == prevCount
            val doneR = round >= 14 || (round >= 2 && stable) || (count == 0 && round >= 4)
            // 滚动越久说明评论越多，进度按轮数爬升（0.3 → 0.6）
            update(0.3f + (round.toFloat() / 20f).coerceAtMost(0.3f), "加载评论中（$count 条）…")
            if (doneR) {
                update(0.65f, "提取正文与评论…")
                extractNow()
            } else {
                handler.postDelayed({ scrollForComments(round + 1, count) }, 700)
            }
        }
    }

    private fun extractNow() {
        val wv = webView ?: return httpFallback()
        wv.evaluateJavascript(XhsDomCapture.EXTRACT_JS) { res ->
            if (handled) return@evaluateJavascript
            val parsed = runCatching {
                val inner = JSONTokener(res).nextValue().toString()
                JSONObject(inner)
            }.getOrNull()
            if (parsed != null) {
                val ogImage = parsed.optString("ogImage").takeIf { it.startsWith("http") }
                val jsVideo = parsed.optString("video").takeIf { it.startsWith("http") }
                val netVideo = jsVideo ?: videoCandidates.firstOrNull { it.startsWith("http") }
                val noteId = parsed.optString("noteId").ifBlank {
                    // 落点必须是笔记页（explore/discovery/item）：合集/主页/失效跳转不保存
                    val m = Regex("""(?:discovery/item|explore)/([a-zA-Z0-9]+)""")
                        .find(webFinalUrl ?: noteUrl)?.groupValues?.get(1)
                    m ?: run {
                        val v = webView ?: null
                        fail("页面不是笔记页（可能是合集/主页/失效跳转），请用原贴完整链接抓取")
                        return@evaluateJavascript
                    }
                }
                val note = ClipNote(
                    id = noteId,
                    title = parsed.optString("title").ifBlank { "未命名笔记" },
                    desc = parsed.optString("desc"),
                    author = parsed.optString("author"),
                    avatarUrl = parsed.optString("avatar"),
                    tags = parsed.getJSONArray("tags").let { a -> List(a.length()) { a.getString(it) } },
                    imageUrls = parsed.getJSONArray("images").let { a -> List(a.length()) { a.getString(it) } },
                    videoUrl = netVideo,
                    type = if (netVideo != null) "video" else "normal",
                    pageUrl = webFinalUrl ?: noteUrl, // 短链→长链：DOM 落库一律长链
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
                mergeAndSave(note, ogImage)
            } else {
                httpFallback()
            }
        }
    }

    /** im importer 方式合并（视频真地址/封面/字段兜底），失败仍保存 DOM 结果 */
    private fun mergeAndSave(domNote: ClipNote, ogImage: String? = null) {
        handled = true
        update(0.8f, "合并抓取中…")
        scope.launch {
            val httpNote = withContext(Dispatchers.IO) {
                runCatching { XhsFetcher.fetch(" $noteUrl") }.getOrNull()
            }
            android.util.Log.d(
                "MemmosDbg",
                "title dbg: dom=${domNote.title.take(24)} http=${httpNote?.title?.take(24) ?: "-"}",
            )
            val merged = domNote.copy(
                // 抓取与落库一律用长链：httpNote 来自 fetch（短链已展开），最稳的锚；DOM 兜底
                pageUrl = httpNote?.pageUrl?.takeIf { it.contains("discovery/item") || it.contains("explore") }
                    ?: domNote.pageUrl,
                // 标题与图片同源原则：__INITIAL_STATE__ noteDetailMap 的标题是权威链
                // （视频页 DOM 的 #detail-title/.title/document.title 会命中相关推荐/页面标题 → 标题错误）
                title = httpNote?.title?.takeIf { it.isNotBlank() }
                    ?: domNote.title.takeIf { it != "未命名笔记" } ?: "未命名笔记",
                desc = domNote.desc.ifBlank { httpNote?.desc.orEmpty() },
                author = domNote.author.ifBlank { httpNote?.author.orEmpty() },
                avatarUrl = domNote.avatarUrl.ifBlank { httpNote?.avatarUrl.orEmpty() },
                tags = domNote.tags.ifEmpty { httpNote?.tags.orEmpty() },
                comments = domNote.comments.ifEmpty { httpNote?.comments.orEmpty() },
                // 媒体必须与笔记 ID 严格对应：__INITIAL_STATE__（httpNote）是最权威的数据链
                // （页面 DOM 的 .media-container 等选择器在桌面版会命中"相关推荐"卡片 → 封面/视频错位）；
                // DOM/网络拦截只在权威链缺失（变体页）时兜底，且兜底前过滤预览卡（页面上已滤）。
                // 图片优先级：① __INITIAL_STATE__ 权威链 ② og:image（=本文封面，页面 meta）
                //   ③ DOM 收集（桌面版有"相关推荐"图风险）④ 网络拦截（已滤预览卡）
                imageUrls = (httpNote?.imageUrls.orEmpty().takeIf { it.isNotEmpty() } ?: run {
                    // httpNote 缺失（极端变体）才允许 og/DOM/拦截兜底；权威链存在时图片只从它来，
                    // 顺序=imageList 原帖顺序（DOM 顺序 2..10,1 是错位根源，禁止混入）
                    val og = ogImage?.takeIf { isNoteImage(it) }
                    val domImgs = domNote.imageUrls.filter { isNoteImage(it) }
                    when {
                        og != null -> listOf(og) + domImgs.filter { it != og }
                        else -> domImgs
                    }.ifEmpty { coverCandidates }
                }).distinct(),
                videoUrl = httpNote?.videoUrl ?: domNote.videoUrl,
                type = if ((httpNote?.videoUrl ?: domNote.videoUrl) != null) "video" else domNote.type,
            )
            // 图片已是纯 imageList 序列（权威优先；兜底分支仅权威缺失才启用）
            handler.post { finish(merged) }
        }
    }

    /** 页面 DOM 拿不到 → XhsFetcher（importer 方式）整条兜底 */
    private fun httpFallback() {
        handled = true
        update(0.5f, "降级抓取（__INITIAL_STATE__）…")
        scope.launch {
            val (note, err) = withContext(Dispatchers.IO) {
                runCatching { XhsFetcher.fetch(" $noteUrl") }
                    .fold({ it to null }, { null to (it.message ?: "未知错误") })
            }
            handler.post {
                if (note != null) finish(note) else fail("抓取失败：$err")
            }
        }
    }

    /** 内容指纹：标题+作者+正文相同视作同一篇（防短链失效/不同入口抓到同内容不同 id 的多抓） */
    private fun contentKey(n: com.tylor.memmos.data.ClipNote): String =
        "${n.title}|${n.author}|${(n.desc ?: "").take(160)}"

    private fun finish(note: ClipNote) {
        android.util.Log.d(
            "MemmosDbg",
            "save note: ${note.title.take(20)} imgs=${note.imageUrls.size}" +note.imageUrls.firstOrNull()?.let { " cover=" + it.substringAfterLast("/").take(24) } ?: " no-cover",
        )
        val ok = runCatching {
            val store = ClipStore(this)
            val list = store.load()
            val k = contentKey(note)
            val merged = (listOf(note) + list.filter { it.id != note.id && contentKey(it) != k })
                .toMutableList()
            store.save(merged)
            true
        }.getOrDefault(false)
        if (!ok) {
            fail("保存失败，请重试")
            return
        }
        // 视频交后台保存服务（通知进度条）
        if (note.videoUrl != null && AppPrefs.autoDownloadVideo(this)) {
            VideoSaverService.start(this, note.id)
        }
        // AI 总结（时机=剪藏完成后后台自动；点开帖子时/不生成 两个档位在详情页处理）：
        // 统一走 AiSummaryRunner——进程级存活、详情页立即显示「生成中」，完成后写库/通知/自动同步
        if (AppPrefs.aiSummaryMode(this) == 0 && AppPrefs.aiApiKey(this).isNotBlank()) {
            AiSummaryRunner.start(this, note.id)
        }
        // 完成文案区分：视频另走 VideoSaverService（独立「正在保存视频」通知）——
        // 只写「抓取完成」会让用户误以为视频也已保存好（用户反馈「悬浮窗提示已保存好，点开还在保存中」）
        val videoPending = note.videoUrl != null && AppPrefs.autoDownloadVideo(this)
        state.value = CaptureState(
            running = false, progress = 1f, done = true,
            status = if (videoPending)
                "抓取完成「${note.title.take(12)}」，视频后台保存中（见通知）"
            else "抓取完成「${note.title.take(12)}」",
        )
        updateNotif(1f, if (videoPending) "抓取完成，视频后台保存中" else "抓取完成")
        stopForeground(false)
        nm.notify(NOTIF_ID, notif(1f, "抓取完成 ✓", done = true))
        // 队列空才停：否则处理下一个排队项（stopSelf 会杀 coroutine，把正在抓的第二个请求中断）
        handler.postDelayed({
            if (pendingQueue.isEmpty()) stopSelf() else pollPending()
        }, 4000)
    }

    /** 串行处理排队中的抓取请求（原来并发请求被直接忽略 → 漏抓） */
    private fun pollPending() {
        handler.postDelayed({
            if (state.value.running) return@postDelayed
            val next = pendingQueue.pollFirst() ?: return@postDelayed
            state.value = CaptureState(running = true, progress = 0.05f, status = "准备抓取…", done = null)
            updateNotif(0.05f, "准备抓取…")
            startCapture(next)
        }, 400)
    }

    private fun fail(msg: String) {
        state.value = CaptureState(running = false, progress = 0f, status = msg, done = false)
        updateNotif(0f, msg)
        stopForeground(true)
        nm.notify(NOTIF_ID, notif(0f, msg, done = true))
        handler.postDelayed({
            if (pendingQueue.isEmpty()) stopSelf() else pollPending()
        }, 3000)
    }

    private fun update(p: Float, s: String) {
        // 进度单调不减：短链两段式跳转会重复 onPageFinished，0.6 的阶段可能被 0.22 重写 → 回跳
        val mono = if (state.value.running && p < state.value.progress) state.value.progress else p
        state.value = CaptureState(running = true, progress = mono, status = s, done = null)
        updateNotif(mono, s)
    }

    /* ───────────── 通知/通道 ───────────── */

    private fun updateNotif(p: Float, s: String) {
        nm.notify(NOTIF_ID, notif(p, s))
    }

    private fun notif(p: Float, s: String, done: Boolean = false): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle("Memmos 抓取")
                .setContentText(s)
                .setContentIntent(pi)
                .setOngoing(!done)
                .setAutoCancel(done)
                .setProgress(100, (p * 100).toInt(), false)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle("Memmos 抓取")
                .setContentText(s)
                .setContentIntent(pi)
                .setOngoing(!done)
                .setAutoCancel(done)
                .setProgress(100, (p * 100).toInt(), false)
                .build()
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "抓取进度", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        runCatching { webView?.destroy() }
        webView = null
        scope.cancel()
        super.onDestroy()
    }
}
