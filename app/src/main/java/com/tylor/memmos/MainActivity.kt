package com.tylor.memmos

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.TextButton
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import com.tylor.memmos.data.ClipNote
import com.tylor.memmos.data.ClipStore
import com.tylor.memmos.net.MediaDownloader
import com.tylor.memmos.net.XhsFetcher
import com.tylor.memmos.util.AppPrefs
import com.tylor.memmos.overlay.FloatingService
import com.tylor.memmos.sync.DeviceDiscovery
import com.tylor.memmos.sync.SyncClient
import com.tylor.memmos.sync.SyncEngine
import com.tylor.memmos.sync.SyncPrefs
import com.tylor.memmos.ui.clips.ClipDetailActivity
import com.tylor.memmos.ui.fetch.ClipFetchActivity
import com.tylor.memmos.ui.fetch.XhsCaptureService
import com.tylor.memmos.ui.login.XhsLoginActivity
import com.tylor.memmos.ui.viewer.FileViewerActivity
import com.tylor.memmos.ui.theme.AccentBrush
import com.tylor.memmos.ui.theme.BtnPrimaryBg
import com.tylor.memmos.ui.theme.BtnPrimaryText
import com.tylor.memmos.ui.theme.AccentGreen
import com.tylor.memmos.ui.theme.ChipBg
import com.tylor.memmos.ui.theme.GlassFill
import com.tylor.memmos.ui.theme.GlassStroke
import com.tylor.memmos.ui.theme.GlassStrokeSoft
import com.tylor.memmos.ui.theme.Ink
import com.tylor.memmos.ui.theme.MemmosTheme
import com.tylor.memmos.ui.theme.Success
import com.tylor.memmos.ui.theme.TextFaint
import com.tylor.memmos.ui.theme.TextHi
import com.tylor.memmos.ui.theme.TextMid
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 主容器：底部三页导航——捕捉 / 剪藏库 / 设置 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 小红书分享面板直达（用户要求：不用复制链接也能快速识别当前帖子）：
        // XHS 分享会把当前帖子链接作为 SEND text/plain 发来——不打开 App UI，
        // 直接交给后台抓取服务（通知/悬浮窗进度），本 Activity 立即退出，无跳转感
        val sharedText = intent?.takeIf { it.action == Intent.ACTION_SEND }
            ?.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        if (intent?.action == Intent.ACTION_SEND) {
            if (sharedText.isNotBlank() && XhsFetcher.extractUrl(sharedText) != null) {
                XhsCaptureService.start(this, sharedText)
            }
            finish()
            return
        }
        enableEdgeToEdge()
        // 悬浮窗按钮引导：本 Activity 在前台才能合法读剪贴板（Android 10+ 后台读取被拦截）
        val clipCapture = intent?.getBooleanExtra("clipCapture", false) == true
        setContent {
            MemmosTheme {
                MainTabs(sharedText, clipCapture)
            }
        }
    }
}

private enum class Tab(val label: String) { CAPTURE("捕捉"), LIBRARY("剪藏库"), SETTINGS("设置") }

@Composable
fun MainTabs(sharedText: String?, clipCapture: Boolean = false) {
    val ctx = LocalContext.current
    val store = remember { ClipStore(ctx) }
    val scope = rememberCoroutineScope()

    // 剪藏数据：三页共享；ON_RESUME 重载（详情页/外部变化后自动刷新）
    var clips by remember { mutableStateOf(store.load()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf(Tab.CAPTURE) }
    var query by remember { mutableStateOf("") }

    fun reload() {
        scope.launch {
            val loaded = withContext(Dispatchers.IO) { store.load() }
            clips = loaded
        }
    }

    /**
     * 批量删除：全选删除曾因「每篇一次全量过滤 + 主线程全量写盘」卡死闪退（ANR）。
     * 现在一次过滤 + 后台一次持久化；磁盘写改为原子写（见 ClipStore.save）。
     */
    fun removeMany(ids: Set<String>) {
        if (ids.isEmpty()) return
        val remaining = clips.filter { it.id !in ids }.toMutableList()
        clips = remaining // 先同步更新 UI，持久化放后台
        scope.launch { withContext(Dispatchers.IO) { store.save(remaining) } }
    }

    fun remove(note: ClipNote) = removeMany(setOf(note.id))


    // 系统分享进入：跳转抓取页（WebView 渲染管线），完成后 ON_RESUME 自动刷新列表
    LaunchedEffect(sharedText) {
        if (!sharedText.isNullOrBlank() && XhsFetcher.extractUrl(sharedText) != null) {
            tab = Tab.LIBRARY
            ctx.startActivity(
                Intent(ctx, ClipFetchActivity::class.java).putExtra(ClipFetchActivity.EXTRA_TEXT, sharedText),
            )
        }
    }

    // 悬浮窗「抓取当前笔记」引导：前台合法读剪贴板（Android 10+ 后台读取被拦截）
    LaunchedEffect(clipCapture) {
        if (!clipCapture) return@LaunchedEffect
        val clip = runCatching {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty()
        }.getOrDefault("")
        val url = XhsFetcher.extractUrl(clip)
        if (url != null) {
            ctx.startActivity(
                Intent(ctx, ClipFetchActivity::class.java).putExtra(ClipFetchActivity.EXTRA_TEXT, clip),
            )
        } else {
            message = "剪贴板里没有小红书链接——在小红书点「分享 → 复制链接」后，再点悬浮窗的「抓取当前笔记」"
        }
    }

    // 从设置/详情返回时刷新权限与列表
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                reload()
                // 服务自恢复：用户启动过悬浮窗且权限在，App 回到前台时自动拉起服务
                if (AppPrefs.serviceWanted(ctx) && Settings.canDrawOverlays(ctx) &&
                    !FloatingService.running.value
                ) {
                    ctx.startForegroundService(Intent(ctx, FloatingService::class.java))
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    Scaffold(
        containerColor = Ink,
        bottomBar = {
            // 自绘底栏：Material3 NavigationBar 内容按 80dp 排布，压到 56dp 会裁掉图标/标签；
            // 自绘 Row 完全控制高度与排版（选中态：图标红 + 标签白加粗）
            // 纯图标底栏：圆形涟漪（无方形触发）+ 单选圆指标平滑滑动（用户要求）
            var barW by remember { mutableStateOf(0) }
            val density = LocalDensity.current
            val pillPx = with(density) { 42.dp.toPx() }
            val pillY = with(density) { ((60.dp.toPx() - pillPx) / 2f).roundToInt() }
            val tileW = if (barW > 0) barW / 3f else 0f
            val animIdx by animateFloatAsState(
                tab.ordinal.toFloat(),
                tween(220, easing = FastOutSlowInEasing),
                label = "tabPill",
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .background(Color(0xFF16181F))
                    .onSizeChanged { barW = it.width },
            ) {
                // 滑动圆指标：浮动层不参与布局（原在 Row 内会占宽把图标推偏）
                if (tileW > 0f) {
                    Box(
                        Modifier
                            .offset {
                                IntOffset(
                                    (animIdx * tileW + (tileW - pillPx) / 2f).roundToInt(),
                                    pillY,
                                )
                            }
                            .size(42.dp)
                            .background(Color(0x2E10B981), CircleShape),
                    )
                }
                Row(Modifier.fillMaxWidth().height(60.dp)) {
                    Tab.entries.forEach { t ->
                        val selected = tab == t
                        val glyph = when (t) {
                            Tab.CAPTURE -> "⌖"
                            Tab.LIBRARY -> "☰"
                            Tab.SETTINGS -> "⚙"
                        }
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(CircleShape) // 涟漪随圆形边界（去掉方形触发）
                                .clickable { tab = t },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                glyph,
                                fontSize = 24.sp,
                                color = if (selected) Color.White else TextFaint,
                            )
                        }
                    }
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (tab) {
                Tab.CAPTURE -> CapturePage(
                    clips = clips, busy = false, message = message,
                    onFetch = { text ->
                        ctx.startActivity(
                            Intent(ctx, ClipFetchActivity::class.java).putExtra(ClipFetchActivity.EXTRA_TEXT, text),
                        )
                    },
                    onOpen = { ctx.startActivity(Intent(ctx, ClipDetailActivity::class.java).putExtra("id", it.id)) },
                )
                Tab.LIBRARY -> LibraryPage(
                    ctx = ctx,
                    clips = clips, query = query, onQuery = { query = it },
                    onOpen = { ctx.startActivity(Intent(ctx, ClipDetailActivity::class.java).putExtra("id", it.id)) },
                    onDelete = { remove(it) },
                    onDeleteMany = { removeMany(it) },
                    onOpenFile = { f ->
                        val rel = f.relativeTo(java.io.File(ctx.filesDir, "vault")).path.replace('\\', '/')
                        ctx.startActivity(Intent(ctx, FileViewerActivity::class.java).putExtra("path", rel))
                    },
                    onUploadNotes = { notes ->
                        val c = SyncPrefs.load(ctx)
                        if (c == null) {
                            message = "尚未配对：请先在设置页完成设备配对"
                        } else {
                            scope.launch {
                                runCatching { SyncEngine.uploadNotes(ctx, c, notes) }
                                    .onSuccess { up -> message = "已上传 $up 篇到 Obsidian" }
                                    .onFailure { message = "${it.javaClass.simpleName}: ${it.message}" }
                            }
                        }
                    },
                )
                Tab.SETTINGS -> SettingsPage(
                    message = message,
                    onMessage = { message = it },
                    onSync = { c ->
                        scope.launch {
                            runCatching { SyncEngine.sync(ctx, c) }
                                .onSuccess { r ->
                                    reload() // 同步完成立即刷新剪藏库（原来要等 ON_RESUME，需退出笔记才更新）
                                    message = "同步完成：上传 ${r.uploaded} · 下载 ${r.downloaded} · 已是最新 ${r.skipped}"
                                }
                                .onFailure {
                                    reload() // 部分成功也尽量刷新
                                    message = "${it.javaClass.simpleName}: ${it.message}"
                                }
                        }
                    },
                )
            }
        }
    }
}

/* ═══════════════ 页面 1：捕捉 ═══════════════ */

@Composable
private fun CapturePage(
    clips: List<ClipNote>,
    busy: Boolean,
    message: String?,
    onFetch: (String) -> Unit,
    onOpen: (ClipNote) -> Unit,
) {
    val ctx = LocalContext.current
    var canDraw by remember { mutableStateOf(Settings.canDrawOverlays(ctx)) }
    val running by FloatingService.running.collectAsState()
    var link by remember { mutableStateOf("") }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) canDraw = Settings.canDrawOverlays(ctx) }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(40.dp))
        Box(
            Modifier.size(56.dp).background(AccentBrush, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
        ) { Text("M", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(12.dp))
        PageTitle("Memmos 捕捉")
        Text(
            "悬浮窗速抓 · 剪藏库 · Obsidian 双向同步",
            fontSize = 12.sp, color = TextFaint,
            modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
        )
        CaptureProgressCard()
        Spacer(Modifier.height(24.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .background(GlassFill, RoundedCornerShape(12.dp))
                .border(1.dp, GlassStrokeSoft, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(7.dp).background(if (canDraw) Success else Color(0xFFFF2E4D), CircleShape))
            Spacer(Modifier.width(9.dp))
            Text(
                if (canDraw) "悬浮窗权限 已授予" else "悬浮窗权限 未授予",
                fontSize = 13.sp, color = TextHi.copy(alpha = 0.88f),
            )
            Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        when {
            !canDraw -> CtaButton("去授权悬浮窗权限") {
                ctx.startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")),
                )
            }
            !running -> CtaButton("启动悬浮窗") {
                AppPrefs.setServiceWanted(ctx, true)
                ctx.startForegroundService(Intent(ctx, FloatingService::class.java))
            }
            else -> GhostButton("关闭悬浮窗") {
                AppPrefs.setServiceWanted(ctx, false)
                ctx.stopService(Intent(ctx, FloatingService::class.java))
            }
        }

        Spacer(Modifier.height(28.dp))
        SectionLabel("快速抓取")
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TextField(
                value = link,
                onValueChange = { link = it },
                placeholder = { Text("粘贴小红书分享链接/文本", fontSize = 12.sp, color = TextFaint) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0x0DFFFFFF),
                    unfocusedContainerColor = Color(0x0DFFFFFF),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = TextHi,
                    unfocusedTextColor = TextHi,
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .height(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AccentBrush)
                    .clickable(enabled = !busy) {
                        onFetch(link); link = ""
                    }
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) { Text(if (busy) "…" else "抓取", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
        }
        message?.let {
            Text(
                it, fontSize = 11.5.sp,
                color = if (it.startsWith("已收进")) Color(0xFF8FD4AB) else Color(0xFF6EE7B7),
                modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
            )
        }

        if (clips.isNotEmpty()) {
            Spacer(Modifier.height(22.dp))
            SectionLabel("最近")
            Spacer(Modifier.height(10.dp))
            clips.take(3).forEach { note -> ClipRow(note, onRemove = null) { onOpen(note) } }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "提示：在任意内容 App 点「分享 → 更多 → Memmos」即可收进剪藏库（当前支持小红书）。",
            fontSize = 11.sp, color = TextFaint, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
        )
    }
}

/* ═══════════════ 页面 2：剪藏库（列表 + 管理） ═══════════════ */

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryPage(
    ctx: Context,
    clips: List<ClipNote>,
    query: String,
    onQuery: (String) -> Unit,
    onOpen: (ClipNote) -> Unit,
    onDelete: (ClipNote) -> Unit,
    onDeleteMany: (Set<String>) -> Unit,
    onUploadNotes: (List<ClipNote>) -> Unit,
    onOpenFile: (java.io.File) -> Unit,
) {
    val ctxFiles = LocalContext.current.filesDir
    // 同步文件：扫描 vault/ 下的非 md 文件（md 已作为剪藏条目展示）
    val syncFiles by remember(ctxFiles) {
        mutableStateOf(
            java.io.File(ctxFiles, "vault").walkTopDown()
                .filter { it.isFile && it.extension.lowercase() != "md" }
                .sortedBy { it.name.lowercase() }
                .toList(),
        )
    }
    var pendingDelete by remember { mutableStateOf<ClipNote?>(null) }
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    val filtered = if (query.isBlank()) clips else clips.filter {
        it.title.contains(query, true) || it.author.contains(query, true) ||
            it.tags.any { t -> t.contains(query, true) } || it.desc.contains(query, true)
    }

    fun exitSelection() { selecting = false; selected = emptySet() }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        // 顶栏：固定高度，普通/多选两种态切换时不引起列表跳动
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!selecting) {
                PageTitle("剪藏库")
                Spacer(Modifier.weight(1f))
                if (clips.isNotEmpty()) {
                    Text(
                        "编辑",
                        fontSize = 13.sp, color = Color(0xFF6EE7B7), fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { selecting = true },
                    )
                }
            } else {
                Text("已选 ${selected.size} 篇", color = TextHi, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(
                    if (selected.size == filtered.size) "取消全选" else "全选",
                    fontSize = 13.sp, color = Color(0xFF6EE7B7), fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable {
                        selected = if (selected.size == filtered.size) mutableStateOf(setOf<String>()).value
                        else filtered.map { it.id }.toSet()
                    },
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    "完成",
                    fontSize = 13.sp, color = TextHi, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { exitSelection() },
                )
            }
        }
        TextField(
            value = query,
            onValueChange = onQuery,
            placeholder = { Text("搜索标题 / 作者 / 标签", fontSize = 13.sp, color = TextFaint) },
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0x0DFFFFFF),
                unfocusedContainerColor = Color(0x0DFFFFFF),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = TextHi,
                unfocusedTextColor = TextHi,
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        )
        Text(
            if (selecting) "已选 ${selected.size} / 共 ${filtered.size} 篇"
            else "共 ${filtered.size} 篇 · 长按或点「编辑」进入多选",
            fontSize = 11.sp, color = TextFaint,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
        )
        CaptureProgressCard()
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp, end = 20.dp, top = 4.dp, bottom = 88.dp,
            ),
        ) {
            if (filtered.isEmpty()) {
                item {
                    com.tylor.memmos.ui.components.EmptyState(
                        title = if (clips.isEmpty()) "还没有剪藏" else "没有匹配的剪藏",
                        desc = if (clips.isEmpty())
                            "在任意内容 App 点「分享 → 更多 → Memmos」抓取第一篇\n（当前支持小红书）"
                        else "换个关键词再试试",
                    )
                }
            }
            // distinctBy：剪藏库可能出现同 id 条目（如两份相同内容 md），列表 key 重复会崩溃
            items(filtered.distinctBy { it.id }, key = { it.id }) { note ->
                val isSel = note.id in selected
                ClipRow(
                    note,
                    onRemove = if (!selecting) ({ pendingDelete = note }) else null,
                    selected = selecting && isSel,
                    selectionMode = selecting,
                    onToggleSelect = {
                        if (selecting) {
                            selected = if (isSel) selected - note.id else selected + note.id
                        } else selecting = true
                    },
                ) {
                    if (selecting) {
                        selected = if (isSel) selected - note.id else selected + note.id
                    } else onOpen(note)
                }
            }
            // 同步下来的非 md 文件（pdf/文档/表格/图片等）入口
            if (!selecting && syncFiles.isNotEmpty()) {
                item {
                    Text(
                        "同步文件 ${syncFiles.size} 个",
                        fontSize = 11.sp, color = TextFaint,
                        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
                    )
                }
                items(syncFiles, key = { it.absolutePath }) { f ->
                    SyncFileRow(f) { onOpenFile(f) }
                }
            }
        }

    }

    // 同步的文件（filesDir/vault 下的非 md 附件，如 pdf/docx/图片）
    val vaultFiles = remember(clips) {
        val dir = java.io.File(ctx.filesDir, "vault")
        if (!dir.exists()) emptyList()
        else dir.walkTopDown().filter { it.isFile && it.extension.lowercase() != "md" }
            .sortedByDescending { it.lastModified() }.toList()
    }
    if (vaultFiles.isNotEmpty() && !selecting) {
        Text(
            "同步的文件（${vaultFiles.size}）",
            fontSize = 12.sp, color = TextFaint,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp),
        )
        vaultFiles.take(20).forEach { f ->
            val rel = f.absolutePath.removePrefix("${java.io.File(ctx.filesDir, "vault").absolutePath}/")
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(GlassFill)
                    .border(1.dp, GlassStrokeSoft, RoundedCornerShape(12.dp))
                    .clickable {
                        ctx.startActivity(
                            Intent(ctx, com.tylor.memmos.ui.viewer.FileViewerActivity::class.java)
                                .putExtra("path", rel),
                        )
                    }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(30.dp).background(
                        Color(0x2EFFFFFF), RoundedCornerShape(9.dp),
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(fileBadge(f.extension), fontSize = 13.sp, color = Color(0xFF6EE7B7))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(f.name, fontSize = 13.sp, color = TextHi, maxLines = 1)
                    Text("${ClipStore.fmtSize(f.length())} · 点击打开", fontSize = 10.sp, color = TextFaint)
                }
            }
        }
    }

    // 多选底部：单个操作条，点击弹出菜单（避免与顶部全选/完成视觉重复）
    var showActionMenu by remember { mutableStateOf(false) }
    if (selecting && !showActionMenu) {
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF1A1D26))
                .border(1.dp, GlassStroke)
                .clickable(enabled = selected.isNotEmpty()) { showActionMenu = true }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (selected.isEmpty()) "选择条目后操作" else "操作已选 ${selected.size} 篇",
                color = TextHi, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text("⋯", color = Color(0xFF6EE7B7), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }

    if (selecting && showActionMenu) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x99000000))
                .clickable { showActionMenu = false },
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .background(Color(0xFF1A1D26))
                .padding(vertical = 10.dp),
        ) {
            Text(
                "删除所选 ${selected.size} 篇",
                color = Color(0xFF6EE7B7), fontSize = 15.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onDeleteMany(selected) // 一次批量删除，不再逐篇触发全量写盘
                        exitSelection()
                        showActionMenu = false
                    }
                    .padding(horizontal = 24.dp, vertical = 14.dp),
            )
            Text(
                "同步所选到 Obsidian",
                color = TextHi, fontSize = 15.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onUploadNotes(clips.filter { it.id in selected })
                        exitSelection()
                        showActionMenu = false
                    }
                    .padding(horizontal = 24.dp, vertical = 14.dp),
            )
            Text(
                "取消",
                color = TextMid, fontSize = 14.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showActionMenu = false }
                    .padding(horizontal = 24.dp, vertical = 12.dp),
            )
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = Color(0xFF1A1D26),
            titleContentColor = TextHi,
            textContentColor = TextMid,
            title = { Text("删除剪藏", fontSize = 17.sp, fontWeight = FontWeight.Bold) },
            text = { Text("「${target.title.take(24)}」将从剪藏库移除，确定吗？", fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = { onDelete(target); pendingDelete = null }) {
                    Text("删除", color = Color(0xFFFF5B6E), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消", color = TextMid) }
            },
        )
    }
    }
}

/* ═══════════════ 页面 3：设置 ═══════════════ */

@Composable
private fun SettingsPage(message: String?, onMessage: (String?) -> Unit, onSync: (SyncClient) -> Unit) {
    val ctx = LocalContext.current
    var canDraw by remember { mutableStateOf(Settings.canDrawOverlays(ctx)) }
    // 登录态：声明在生命周期观察器之前（ON_RESUME 会刷新：登录页返回/回前台即时更新）
    var loggedIn by remember { mutableStateOf(hasXhsSession(ctx)) }
    val running by FloatingService.running.collectAsState()
    val scope = rememberCoroutineScope()

    var paired by remember { mutableStateOf(SyncPrefs.load(ctx) != null) }
    var pairHost by remember { mutableStateOf("") }
    var pairCode by remember { mutableStateOf("") }
    var syncing by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    var devices by remember { mutableStateOf(listOf<DeviceDiscovery.Device>()) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                canDraw = Settings.canDrawOverlays(ctx)
                loggedIn = hasXhsSession(ctx) // 登录页返回/App 回前台时刷新登录状态
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PageTitle("设置")
            Spacer(Modifier.weight(1f))
            Text(
                "多源内容沉淀 · Obsidian 同步",
                fontSize = 11.sp, color = TextFaint,
            )
        }
        // 全局提示（同步/配对/抓取反馈）放在标题下，一眼可见
        message?.let {
            Text(
                it, fontSize = 11.sp, lineHeight = 16.sp,
                color = if (it.startsWith("配对成功") || it.startsWith("同步完成")) Color(0xFF8FD4AB) else Color(0xFF6EE7B7),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
        }


                SectionLabel("个人资料")
        Spacer(Modifier.height(8.dp))
        var profileName by remember { mutableStateOf(AppPrefs.profileName(ctx)) }
        var profileAvatar by remember { mutableStateOf(AppPrefs.profileAvatar(ctx)) }
        // 头像选择：相册取图 → 复制到 filesDir/profile_avatar（App 内可显示）
        val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) runCatching {
                val out = java.io.File(ctx.filesDir, "profile_avatar")
                ctx.contentResolver.openInputStream(uri)?.use { input ->
                    out.outputStream().use { input.copyTo(it) }
                }
                AppPrefs.setProfileAvatar(ctx, out.absolutePath)
                profileAvatar = out.absolutePath
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .background(GlassFill, RoundedCornerShape(12.dp))
                .border(1.dp, GlassStrokeSoft, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 头像圆片：点击换图
            val avFile = profileAvatar.takeIf { it.isNotBlank() }
                ?.let { java.io.File(it) }?.takeIf { it.exists() }
            Box(
                Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(Color(0x22FFFFFF))
                    .clickable { avatarPicker.launch("image/*") },
                contentAlignment = Alignment.Center,
            ) {
                if (avFile != null) {
                    AsyncImage(
                        model = avFile,
                        contentDescription = "头像",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                    )
                } else {
                    Text("头像", fontSize = 11.sp, color = TextFaint)
                }
            }
            Spacer(Modifier.width(12.dp))
            // 名字标签并入 TextField label（浮动标签）：列高=输入框本身，
            // 头像 54dp 与输入框严格居中对齐（原来标签在框上，头像对的是「标签+框」整体中心，视觉错位）
            Column(Modifier.weight(1f)) {
                TextField(
                    value = profileName,
                    onValueChange = {
                        profileName = it
                        AppPrefs.setProfileName(ctx, it.trim())
                    },
                    singleLine = true,
                    label = { Text("我的名字", fontSize = 12.sp, color = TextFaint) },
                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, color = TextHi),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0x0DFFFFFF),
                        unfocusedContainerColor = Color(0x0DFFFFFF),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedLabelColor = TextFaint,
                        unfocusedLabelColor = TextFaint,
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Text(
            "用于 Obsidian 同步笔记的作者与头像展示（头像点击可更换）",
            fontSize = 10.sp, color = TextFaint,
            modifier = Modifier.padding(top = 6.dp),
        )

        SectionLabel("剪藏")
        Spacer(Modifier.height(8.dp))
        var autoDl by remember { mutableStateOf(AppPrefs.autoDownloadVideo(ctx)) }
        Row(
            Modifier
                .fillMaxWidth()
                .background(GlassFill, RoundedCornerShape(12.dp))
                .border(1.dp, GlassStrokeSoft, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("自动下载视频", fontSize = 13.sp, color = TextHi.copy(alpha = 0.88f))
                Text("抓到视频笔记后自动下载，离线可看", fontSize = 11.sp, color = TextFaint)
            }
            Box(
                Modifier
                    .size(width = 44.dp, height = 26.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (autoDl) AccentBrush else SolidColor(Color(0x33FFFFFF)))
                    .clickable {
                        AppPrefs.setAutoDownloadVideo(ctx, !autoDl)
                        autoDl = !autoDl
                    },
            ) {
                Box(
                    Modifier
                        .align(if (autoDl) Alignment.CenterEnd else Alignment.CenterStart)
                        .padding(3.dp)
                        .size(20.dp)
                        .background(Color.White, CircleShape),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        var fetchC by remember { mutableStateOf(AppPrefs.fetchComments(ctx)) }
        var maxC by remember { mutableStateOf(AppPrefs.maxComments(ctx).toString()) }
        Column(
            Modifier
                .fillMaxWidth()
                .background(GlassFill, RoundedCornerShape(12.dp))
                .border(1.dp, GlassStrokeSoft, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("抓取评论", fontSize = 13.sp, color = TextHi.copy(alpha = 0.88f))
                    Text("含楼中楼回复（当前源为小红书）", fontSize = 11.sp, color = TextFaint)
                }
                Box(
                    Modifier
                        .size(width = 44.dp, height = 26.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (fetchC) AccentBrush else SolidColor(Color(0x33FFFFFF)))
                        .clickable {
                            AppPrefs.setFetchComments(ctx, !fetchC)
                            fetchC = !fetchC
                        },
                ) {
                    Box(
                        Modifier
                            .align(if (fetchC) Alignment.CenterEnd else Alignment.CenterStart)
                            .padding(3.dp)
                            .size(20.dp)
                            .background(Color.White, CircleShape),
                    )
                }
            }
            if (fetchC) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("条数上限", fontSize = 11.sp, color = TextFaint)
                    Spacer(Modifier.width(10.dp))
                    Box(
                        Modifier
                            .width(88.dp)
                            .height(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x0DFFFFFF))
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        BasicTextField(
                            value = maxC,
                            onValueChange = { v ->
                                maxC = v.filter { it.isDigit() }.take(3)
                                maxC.toIntOrNull()?.let { AppPrefs.setMaxComments(ctx, it) }
                            },
                            singleLine = true,
                            textStyle = TextStyle(color = TextHi, fontSize = 13.sp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(), // 只占内容高，垂直居中交给外层 Box
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        SectionLabel("悬浮窗")
        Spacer(Modifier.height(10.dp))
        // 按钮式整卡（用户要求）：整卡可点=动作；两行状态分别显示「权限/运行」
        Row(
            Modifier
                .fillMaxWidth()
                .background(
                    if (canDraw && running) Color(0x0D46C882) else GlassFill,
                    RoundedCornerShape(12.dp),
                )
                .border(
                    1.dp,
                    if (canDraw && running) Color(0x3346C882) else GlassStrokeSoft,
                    RoundedCornerShape(12.dp),
                )
                .clickable {
                    when {
                        !canDraw -> ctx.startActivity(
                            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")),
                        )
                        !running -> {
                            AppPrefs.setServiceWanted(ctx, true)
                            ctx.startForegroundService(Intent(ctx, FloatingService::class.java))
                        }
                        else -> {
                            AppPrefs.setServiceWanted(ctx, false)
                            ctx.stopService(Intent(ctx, FloatingService::class.java))
                        }
                    }
                }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).background(if (canDraw) Success else Color(0xFFFF2E4D), CircleShape))
                    Spacer(Modifier.width(7.dp))
                    Text(
                        if (canDraw) "悬浮窗权限 已授予" else "悬浮窗权限 未授予",
                        fontSize = 13.sp, color = TextHi.copy(alpha = 0.88f),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).background(if (running) Success else TextFaint, CircleShape))
                    Spacer(Modifier.width(7.dp))
                    Text(
                        if (running) "悬浮窗 运行中" else "悬浮窗 未启动",
                        fontSize = 13.sp, color = TextFaint,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                when {
                    !canDraw -> "去授权"
                    !running -> "启动悬浮窗"
                    else -> "停止悬浮窗"
                },
                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                color = if (canDraw && running) Color(0xFF6EE7B7) else BtnPrimaryText,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(SolidColor(if (canDraw && running) Color(0x14FF2E4D) else BtnPrimaryBg))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }

        Spacer(Modifier.height(22.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .background(GlassFill, RoundedCornerShape(12.dp))
                .border(1.dp, GlassStrokeSoft, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("内容源登录 · 小红书", fontSize = 13.sp, color = TextHi.copy(alpha = 0.88f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 状态点：与「悬浮窗权限」行同款语义（绿=已登录 红=未登录）
                    Box(
                        Modifier.size(7.dp)
                            .background(if (loggedIn) Success else Color(0xFFFF2E4D), CircleShape),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (loggedIn) "已登录 · 可抓取正文/图集/视频/评论"
                        else "未登录 · 免登录抓取（无评论）",
                        fontSize = 11.sp, color = TextFaint,
                    )
                }
            }
            Text(
                if (loggedIn) "重新登录" else "登录",
                fontSize = 13.sp, color = Color(0xFF6EE7B7), fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable {
                    ctx.startActivity(Intent(ctx, XhsLoginActivity::class.java))
                },
            )
        }

        

        SectionLabel("设备配对 · Obsidian")
        Spacer(Modifier.height(10.dp))

        if (!paired) {
            // 扫描设备：UDP 广播发现同网段的 Obsidian memos-graph
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x0DFFFFFF))
                        .clickable(enabled = !scanning) {
                            scanning = true
                            onMessage(null)
                            scope.launch {
                                devices = runCatching { DeviceDiscovery.discover(ctx) }.getOrDefault(emptyList())
                                scanning = false
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) { Text(if (scanning) "正在扫描局域网设备…" else "扫描局域网设备", fontSize = 13.sp, color = TextHi) }
            }
            if (devices.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                devices.forEach { dev ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(GlassFill)
                            .border(1.dp, GlassStrokeSoft, RoundedCornerShape(12.dp))
                            .clickable {
                                syncing = true
                                scope.launch {
                                    runCatching {
                                        val r = SyncClient.pair(dev.host, dev.port, dev.pairCode)
                                        SyncPrefs.save(ctx, dev.host, dev.port, r.token, r.folder)
                                        paired = true
                                        onMessage("已配对：${dev.name}")
                                    }.onFailure { onMessage("${it.javaClass.simpleName}: ${it.message}") }
                                    syncing = false
                                }
                            }
                            .padding(horizontal = 13.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(dev.name, fontSize = 13.sp, color = TextHi, fontWeight = FontWeight.SemiBold)
                            Text("${dev.host}:${dev.port}", fontSize = 11.sp, color = TextFaint)
                        }
                        Text("配对", fontSize = 13.sp, color = Color(0xFF6EE7B7), fontWeight = FontWeight.Bold)
                    }
                }
            } else if (!scanning) {
                Text(
                    "未发现设备：确认电脑 Obsidian 已启用同步服务，且手机与电脑在同一 Wi-Fi。",
                    fontSize = 11.sp, color = TextFaint,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                )
            }

            // 手动输入（备用）
            var showManual by remember { mutableStateOf(false) }
            Text(
                if (showManual) "收起手动输入" else "手动输入地址",
                fontSize = 11.5.sp, color = Color(0xFF6EE7B7),
                modifier = Modifier.clickable { showManual = !showManual }.padding(vertical = 6.dp),
            )
            if (showManual) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextField(
                        value = pairHost,
                        onValueChange = { pairHost = it },
                        placeholder = { Text("电脑IP:端口（默认28422）", fontSize = 11.sp, color = TextFaint) },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0x0DFFFFFF),
                            unfocusedContainerColor = Color(0x0DFFFFFF),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = TextHi,
                            unfocusedTextColor = TextHi,
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1.4f),
                    )
                    TextField(
                        value = pairCode,
                        onValueChange = { pairCode = it },
                        placeholder = { Text("配对码", fontSize = 11.sp, color = TextFaint) },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0x0DFFFFFF),
                            unfocusedContainerColor = Color(0x0DFFFFFF),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = TextHi,
                            unfocusedTextColor = TextHi,
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(0.7f),
                    )
                }
                Spacer(Modifier.height(10.dp))
                CtaButton("配对") {
                    scope.launch {
                        syncing = true; onMessage(null)
                        runCatching {
                            val raw = pairHost.trim().replace('：', ':')
                            val parts = raw.split(":")
                            require(parts[0].isNotBlank()) { "请填写电脑 IP" }
                            val host = parts[0]
                            val port = parts.getOrNull(1)?.toIntOrNull() ?: 28422
                            require(port in 1..65535) { "端口不合法" }
                            val r = SyncClient.pair(host, port, pairCode.trim())
                            SyncPrefs.save(ctx, host, port, r.token, r.folder)
                            paired = true
                            onMessage("配对成功（同步目录：${r.folder.ifBlank { "全库" }}）")
                        }.onFailure { onMessage("${it.javaClass.simpleName}: ${it.message}") }
                        syncing = false
                    }
                }
            }
        } else {
            // 配对状态标识：设备地址与同步目录
            SyncPrefs.loadInfo(ctx)?.let { info ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(GlassFill)
                        .border(1.dp, GlassStrokeSoft, RoundedCornerShape(12.dp))
                        .padding(horizontal = 13.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(8.dp).background(Success, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("已配对：${info.host}:${info.port}", fontSize = 13.sp, color = TextHi, fontWeight = FontWeight.SemiBold)
                        Text("同步目录：${info.folder.ifBlank { "全库" }}", fontSize = 11.sp, color = TextFaint)
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(50.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(BtnPrimaryBg) // 官方源：白底黑字胶囊（radius 9999）
                        .clickable(enabled = !syncing) {
                            val c = SyncPrefs.load(ctx) ?: return@clickable
                            syncing = true; onMessage(null)
                            scope.launch {
                                runCatching { SyncEngine.sync(ctx, c) }
                                    .onSuccess { r -> onMessage("同步完成：上传 ${r.uploaded} · 下载 ${r.downloaded} · 已是最新 ${r.skipped}") }
                                    .onFailure { onMessage("${it.javaClass.simpleName}: ${it.message}") }
                                syncing = false
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (syncing) "同步中…" else "立即同步", color = BtnPrimaryText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                Box(
                    Modifier
                        .width(110.dp)
                        .height(50.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0x6610B981), RoundedCornerShape(12.dp))
                        .clickable {
                            SyncPrefs.clear(ctx)
                            paired = false
                            onMessage("已取消配对")
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("取消配对", color = Color(0xFF6EE7B7), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 同步进度条（用户要求）：上传/下载阶段 + 进度 + 完成态
        val syncProg by SyncEngine.progress.collectAsState()
        syncProg?.let { p ->
            Spacer(Modifier.height(10.dp))
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${p.phase}", fontSize = 11.sp, color = TextMid,
                        modifier = Modifier.weight(1f),
                    )
                    Text("${p.done} / ${p.total}", fontSize = 11.sp, color = TextFaint)
                }
                LinearProgressIndicator(
                    progress = { p.fraction },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(99.dp)),
                    color = AccentGreen,
                    trackColor = Color(0x33FFFFFF),
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        SectionLabel("关于")
        Spacer(Modifier.height(8.dp))
        Text(
            "Memmos · 悬浮窗速抓 + 剪藏库 + Obsidian 双向同步（当前支持小红书）",
            fontSize = 11.sp, lineHeight = 17.sp, color = TextFaint,
        )
        Text(
            "© 2026 Memmos",
            fontSize = 10.sp, color = TextFaint,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.height(30.dp))
    }
}

/* ═══════════════ 共用组件 ═══════════════ */

private fun fileBadge(ext: String): String = when (ext.lowercase()) {
    "pdf" -> "PDF"
    "docx", "doc" -> "W"
    "pptx", "ppt" -> "P"
    "xlsx", "xls", "csv" -> "X"
    else -> ext.take(3).uppercase()
}

private fun hasXhsSession(ctx: Context): Boolean {
    val cookie = android.webkit.CookieManager.getInstance().getCookie("https://www.xiaohongshu.com").orEmpty()
    return cookie.contains("web_session=")
}

/**
 * 后台抓取进度卡（捕捉页/剪藏库共用，用户要求：不止悬浮窗显示）：
 * 抓取中显示进度条+阶段；失败显示红条提示；完成后由悬浮窗/通知承担结果展示。
 */
@Composable
private fun CaptureProgressCard() {
    val cap by XhsCaptureService.state.collectAsState()
    if (!cap.running && cap.done != false) return
    val accent = if (cap.done == false) Color(0xFFFF5B6E) else AccentGreen
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, bottom = 8.dp)
            .background(Color(0x141018), RoundedCornerShape(12.dp)) // 深底遮挡层，卡片呼应玻璃
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (cap.running) "后台抓取中" else "抓取失败",
                fontSize = 11.sp, fontWeight = FontWeight.Bold,
                color = if (cap.running) accent else Color(0xFFFF5B6E),
                modifier = Modifier.weight(1f),
            )
            if (cap.running) {
                Text("${(cap.progress * 100).toInt()}%", fontSize = 11.sp, color = TextFaint)
            }
        }
        if (cap.running) {
            LinearProgressIndicator(
                progress = { cap.progress },
                modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(99.dp)),
                color = AccentGreen,
                trackColor = Color(0x33FFFFFF),
            )
        }
        Text(
            cap.status,
            fontSize = 10.5.sp, color = if (cap.done == false) Color(0xFFFF5B6E) else TextFaint,
        )
    }
}

/** 页面大标题（mckp.live 节奏）：22sp 显示级 + 品牌紫短下划线（如 "A Real Experience." 强调） */
@Composable
private fun PageTitle(text: String) {
    Column {
        Text(text, color = TextHi, fontSize = 22.sp, fontWeight = FontWeight.Light, letterSpacing = (-0.55).sp)
        Spacer(Modifier.height(5.dp))
        Box(
            Modifier
                .width(34.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(AccentGreen),
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontSize = 11.sp, letterSpacing = 2.sp, color = TextFaint, modifier = Modifier.fillMaxWidth())
}

/** 剪藏行：多选模式下显示勾选圈（点按切换、长按进入多选由外层处理） */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClipRow(
    note: ClipNote,
    onRemove: (() -> Unit)?,
    selected: Boolean = false,
    selectionMode: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onClick: () -> Unit,
) {
    val ctx = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(GlassFill)
            .border(
                1.dp,
                if (selected) Color(0xFFFF5B6E) else GlassStrokeSoft,
                RoundedCornerShape(12.dp),
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onToggleSelect,
            )
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            Box(
                Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (selected) AccentBrush else SolidColor(Color.Transparent))
                    .border(1.5.dp, if (selected) Color.Transparent else Color(0x66FFFFFF), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Text("✓", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
        }
        val cover = note.imageUrls.firstOrNull()
        if (cover != null) {
            AsyncImage(
                model = cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)),
            )
        } else {
            Box(
                Modifier.size(64.dp).background(
                    Brush.linearGradient(listOf(Color(0x2E10B981), Color(0x2E9E8FFF))),
                    RoundedCornerShape(10.dp),
                ),
                contentAlignment = Alignment.Center,
            ) { Text(if (note.type == "video") "▶" else if (note.origin == "vault") "M" else "文", color = Color(0xFF6EE7B7), fontSize = 18.sp) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                note.title,
                fontSize = 13.sp, color = TextHi, fontWeight = FontWeight.SemiBold,
                lineHeight = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                buildString {
                    // 个人资料兜底：Obsidian 同步笔记显示设置里的名字（用户要求）
                    append(
                        note.author.ifBlank {
                            if (note.origin == "vault") AppPrefs.profileName(ctx).ifBlank { "未知作者" }
                            else "未知作者"
                        },
                    )
                    append(" · ")
                    append(ClipStore.fmtTime(note.clippedAt))
                    if (note.type == "video") append(" · 视频")
                    else if (note.imageUrls.isNotEmpty()) append(" · ${note.imageUrls.size}图")
                    if (note.origin == "vault") append(" · 库")
                },
                fontSize = 11.sp, color = TextFaint,
            )
        }
        if (onRemove != null) {
            Text(
                "删除",
                fontSize = 11.sp, color = Color(0xFFFF5B6E),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onRemove)
                    .padding(6.dp),
            )
        }
    }
}

/** 同步文件行：类型图标 + 名称 + 大小，点击打开查看器 */
@Composable
private fun SyncFileRow(f: java.io.File, onClick: () -> Unit) {
    val ext = f.extension.lowercase()
    val badge = when (ext) {
        "pdf" -> "PDF"
        "doc", "docx" -> "W"
        "ppt", "pptx" -> "P"
        "xls", "xlsx" -> "X"
        "csv" -> "CSV"
        "png", "jpg", "jpeg" -> "图"
        "mp4", "mov" -> "▶"
        else -> "F"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(GlassFill)
            .border(1.dp, GlassStrokeSoft, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).background(
                Brush.linearGradient(listOf(Color(0x33FF2E4D), Color(0x33FF7A45))),
                RoundedCornerShape(9.dp),
            ),
            contentAlignment = Alignment.Center,
        ) { Text(badge, color = Color(0xFF6EE7B7), fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(f.name, fontSize = 13.sp, color = TextHi, maxLines = 1)
            Text("${ext.uppercase()} · ${com.tylor.memmos.data.ClipStore.fmtSize(f.length())}", fontSize = 10.sp, color = TextFaint)
        }
    }
}

@Composable
private fun CtaButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(AccentBrush)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun GhostButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, Color(0x6610B981), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color(0xFF6EE7B7), fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}
