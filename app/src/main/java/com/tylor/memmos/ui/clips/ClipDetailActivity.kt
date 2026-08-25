package com.tylor.memmos.ui.clips

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.tylor.memmos.data.ClipComment
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt
import com.tylor.memmos.data.ClipNote
import com.tylor.memmos.data.ClipStore
import com.tylor.memmos.net.MediaDownloader
import com.tylor.memmos.ui.IconFullscreen
import com.tylor.memmos.ui.IconFullscreenExit
import com.tylor.memmos.ui.IconPause
import com.tylor.memmos.ui.IconPlayFilled
import com.tylor.memmos.ui.IconSun
import com.tylor.memmos.ui.IconVolumeSpeaker
import com.tylor.memmos.ui.md.MarkdownView
import com.tylor.memmos.util.AppPrefs
import com.tylor.memmos.util.MediaSaver
import com.tylor.memmos.util.VideoSaverService
import com.tylor.memmos.ui.theme.AccentBrush
import com.tylor.memmos.ui.theme.GlassFill
import com.tylor.memmos.ui.theme.GlassStroke
import coil.compose.AsyncImage
import java.io.File
import com.tylor.memmos.ui.theme.GlassStrokeSoft
import com.tylor.memmos.ui.theme.Ink
import com.tylor.memmos.ui.theme.TextFaint
import com.tylor.memmos.ui.theme.TextHi
import com.tylor.memmos.ui.theme.TextMid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 剪藏详情：App 内阅读抓下来的帖子。
 * 媒体（图集 Pager / 视频）在正文上方；图片点击进入全屏查看器（双击缩放、左右切换）。
 */
class ClipDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val id = intent.getStringExtra("id") ?: return finish()
        val note = ClipStore(this).load().firstOrNull { it.id == id } ?: return finish()
        setContent {
            Box(Modifier.fillMaxSize().background(Ink)) {
                DetailContent(note)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailContent(initial: ClipNote) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var cur by remember { mutableStateOf(initial) }
    var playerPath by remember { mutableStateOf<String?>(null) } // 刚下载完待内嵌播放的路径
    var viewerPage by remember { mutableStateOf<Int?>(null) } // 全屏查看器打开时的起始页
    // 视频后台保存进度（VideoSaverService）：当前笔记在下才显示进度条
    val vidProgress by VideoSaverService.progress.collectAsState()
    val vidActive by VideoSaverService.activeNoteId.collectAsState()
    // 后台保存结束（进度回空闲）：重载本地库切换内嵌播放；刚下载完的自动开播
    LaunchedEffect(vidProgress, vidActive) {
        if (vidActive == cur.id && vidProgress == null) {
            val fresh = ClipStore(ctx).load().firstOrNull { it.id == cur.id }
            if (fresh != null && fresh != cur) {
                val wasLocal = cur.localVideoPath?.let(::File)?.exists() == true
                cur = fresh
                if (!wasLocal && fresh.localVideoPath != null) playerPath = fresh.localVideoPath
            }
        }
    }
    var savingImages by remember { mutableStateOf(false) }
    var imgMsg by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .clickable { (ctx as? ComponentActivity)?.finish() },
                contentAlignment = Alignment.Center,
            ) { Text("←", color = TextHi, fontSize = 20.sp) }
            Spacer(Modifier.width(10.dp))
            Text("剪藏详情", color = TextHi, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(ClipStore.fmtTime(cur.clippedAt), fontSize = 11.sp, color = TextFaint)
        }
        Spacer(Modifier.height(16.dp))

        Text(cur.title, color = TextHi, fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp)
        Spacer(Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            // 个人资料兜底（用户要求）：Obsidian 同步笔记自动显示设置里的头像/名字；
            // 本地文件路径直接给 File，http 给 URL
            val displayAvatar = cur.avatarUrl.ifBlank {
                if (cur.origin == "vault") AppPrefs.profileAvatar(ctx) else ""
            }
            val avatarModel: Any? = displayAvatar.takeIf { it.isNotBlank() }?.let {
                if (it.startsWith("http")) it
                else File(it).takeIf { f -> f.exists() } ?: it
            }
            if (avatarModel != null) {
                AsyncImage(
                    model = avatarModel,
                    contentDescription = "作者头像",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(26.dp).clip(CircleShape),
                )
            } else {
                Box(Modifier.size(26.dp).background(AccentBrush, CircleShape))
            }
            Spacer(Modifier.width(8.dp))
            Text(
                cur.author.ifBlank {
                    if (cur.origin == "vault") AppPrefs.profileName(ctx).ifBlank { "未知作者" }
                    else "未知作者"
                },
                fontSize = 13.sp, color = TextMid,
            )
            if (cur.type == "video") {
                Spacer(Modifier.width(10.dp))
                TagPill("视频笔记")
            }
        }
        /* ── 媒体区：在正文上方（用户要求）──
           视频笔记：封面 + 播放按钮，点击播放（未下载则自动下载后播放）
           图文笔记：多图 Pager / 单图，点击进全屏查看器 */
        Spacer(Modifier.height(16.dp))
        when {
            cur.type == "video" && cur.videoUrl != null -> {
                val local = cur.localVideoPath?.let(::File)?.takeIf { it.exists() }
                val playFile = playerPath?.let(::File)?.takeIf { it.exists() } ?: local
                if (playFile != null) {
                    // 内嵌播放（不放大）；全屏按钮才进全屏，左/右半区竖滑=亮度/音量
                    InlineVideoPlayer(
                        file = playFile,
                        autoplay = playerPath != null,
                        cover = cur.imageUrls.firstOrNull(), // 未播放时用封面当海报（VideoView 是黑底）
                    )
                } else {
                    // 下载交给后台前台服务（通知栏进度条）；封面就地显示进度
                    VideoCoverBlock(
                        cover = cur.imageUrls.firstOrNull(),
                        progress = if (vidActive == cur.id) vidProgress else null,
                        onDownload = { VideoSaverService.start(ctx, cur.id) },
                    )
                }
            }
            cur.imageUrls.size > 1 -> {
                ImagePager(
                    urls = cur.imageUrls,
                    onOpenViewer = { viewerPage = it },
                )
                Spacer(Modifier.height(8.dp))
                ActionRow(
                    if (savingImages) "图片保存中…" else "保存全部图片到相册",
                    if (savingImages) "…" else "保存",
                ) {
                    if (savingImages) return@ActionRow
                    savingImages = true
                    scope.launch {
                        var saved = 0
                        var existed = 0
                        cur.imageUrls.forEachIndexed { i, u ->
                            runCatching { MediaSaver.saveImageToGallery(ctx, u, cur.id.take(8) + "-${i + 1}") }
                                .onSuccess { r -> if (r.existed) existed++ else saved++ }
                        }
                        savingImages = false
                        imgMsg = when {
                            saved > 0 && existed > 0 -> "已保存 $saved 张 · $existed 张已在相册"
                            saved > 0 -> "已保存 $saved 张到相册"
                            existed > 0 -> "全部已在相册中"
                            else -> "保存失败，请检查网络"
                        }
                    }
                }
                imgMsg?.let { Text(it, fontSize = 11.sp, color = Color(0xFF8FD4AB), modifier = Modifier.padding(bottom = 6.dp)) }
            }
            cur.imageUrls.size == 1 -> {
                SingleImage(cur.imageUrls[0]) { viewerPage = 0 }
                Spacer(Modifier.height(8.dp))
                ActionRow(
                    if (savingImages) "图片保存中…" else "保存图片到相册",
                    if (savingImages) "…" else "保存",
                ) {
                    if (savingImages) return@ActionRow
                    savingImages = true
                    scope.launch {
                        runCatching { MediaSaver.saveImageToGallery(ctx, cur.imageUrls[0], cur.id.take(8)) }
                            .onSuccess { r -> imgMsg = if (r.existed) "该图片已在相册中" else "已保存到相册" }
                            .onFailure { imgMsg = "保存失败" }
                        savingImages = false
                    }
                }
            }
        }

        /* ── 正文 ── */
        val rawMd = cur.rawMd
        if (cur.origin == "vault" && rawMd != null) {
            Spacer(Modifier.height(16.dp))
            MdInlineView(rawMd, cur.originPath)
        } else if (cur.desc.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Text(cur.desc, color = TextHi.copy(alpha = 0.92f), fontSize = 14.sp, lineHeight = 25.sp)
        }

        /* ── 标签：底部展示（正文已清洗话题文本）── */
        if (cur.tags.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                cur.tags.forEach { TagPill("#$it") }
            }
        }

        /* ── 评论区 ── */
        if (cur.comments.isNotEmpty()) {
            Spacer(Modifier.height(22.dp))
            val subsTotal = cur.comments.sumOf { it.subComments.size }
            Text(
                if (subsTotal > 0) "评论 ${cur.comments.size} 条 · 回复 $subsTotal 条"
                else "评论 ${cur.comments.size} 条",
                color = TextHi, fontSize = 15.sp, fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            // 主/子评论渲染（用户要求：子回复可展开/收起，默认收起避免长贴刷屏）
            for ((i, c) in cur.comments.withIndex()) {
                CommentCard(c)
                if (c.subComments.isNotEmpty()) {
                    var subsExpanded by remember("subs-$i") { mutableStateOf(false) }
                    Text(
                        if (subsExpanded) "收起回复（${c.subComments.size} 条）" else "查看 ${c.subComments.size} 条回复 ▾",
                        fontSize = 11.sp, color = TextFaint,
                        modifier = Modifier
                            .padding(start = 10.dp, top = 2.dp, bottom = 8.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color(0x0AFFFFFF))
                            .clickable { subsExpanded = !subsExpanded }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                    if (subsExpanded) {
                        for (sc in c.subComments) {
                            CommentCard(sc, indent = true)
                        }
                    }
                }
            }
            Text(
                "主评论与楼中楼回复均已抓取",
                fontSize = 10.sp, color = TextFaint,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        Spacer(Modifier.height(18.dp))
        if (cur.type == "video" && cur.videoUrl != null)
            ActionRow("视频直链", "复制") { copy(ctx, cur.videoUrl!!) }
        ActionRow("原帖页面", "复制链接") { copy(ctx, cur.pageUrl) }
        ActionRow("在浏览器打开", "前往") {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(cur.pageUrl)))
        }

        Spacer(Modifier.height(30.dp))
    }

    /* ── 全屏图片查看器 ── */
    viewerPage?.let { start ->
        ImageViewer(
            ctx = ctx,
            urls = cur.imageUrls,
            initialPage = start,
            onDismiss = { viewerPage = null },
        )
    }
}

/* ───────────── 媒体组件 ───────────── */

/**
 * 视频封面块：封面图 + 居中播放按钮；点击启动后台保存（VideoSaverService），
 * 封面底部实时显示下载进度条；完成自动转内嵌播放。已下载时不再显示本块。
 */
@Composable
private fun VideoCoverBlock(cover: String?, progress: Float?, onDownload: () -> Unit) {
    val busy = progress != null
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = !busy, onClick = onDownload),
    ) {
        if (cover != null) {
            AsyncImage(
                model = cover,
                contentDescription = "视频封面",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Box(
                Modifier.fillMaxWidth().height(200.dp).background(Color(0x22FFFFFF)),
            )
        }
        Box(
            Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = 0.18f)),
        )
        Box(
            Modifier
                .size(64.dp)
                .align(Alignment.Center)
                .background(Color(0xAA000000), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (busy) Text("…", color = Color.White, fontSize = 20.sp)
            else IconPlayFilled(20.dp, Color.White)
        }
        // 后台保存进度条（用户要求「进度条表示」；进度由 VideoSaverService 推送）
        if (busy) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                color = Color(0xFFB4A7FF),
                trackColor = Color(0x33FFFFFF),
            )
        }
        Text(
            when {
                busy -> "后台保存中 ${(progress!! * 100).toInt()}%…"
                else -> "点击下载（后台保存，可切走）"
            },
            fontSize = 11.sp,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp)
                .background(Color(0x99000000), RoundedCornerShape(999.dp))
                .padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

/**

 * 手势提示内容类型（音量/亮度带矢量图标，进度只有文字） */
private sealed class GestureTip(val text: String) {
    class Brightness(pct: Int) : GestureTip("$pct%")
    class Volume(pct: Int) : GestureTip("$pct%")
    class Seek(t: String) : GestureTip(t)
}

/* ───────────── 内嵌视频播放器 ───────────── */

/**
 * 内嵌播放器（用户要求）：就地播放不放大，点全屏按钮才进全屏（Dialog 全屏层）。
 * 手势：左半区竖滑=亮度、右半区竖滑=音量（右上角有提示浮层）、横滑=进度；
 * 单击=控制条显隐；系统栏仅在全屏时隐藏，退回还原。
 */
@Composable
private fun InlineVideoPlayer(file: File, autoplay: Boolean, cover: String? = null) {
    val ctx = LocalContext.current
    val activity = ctx as? ComponentActivity
    val audio = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var fullscreen by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var controls by remember { mutableStateOf(true) }
    var position by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var aspect by remember { mutableStateOf(16f / 9f) }
    var gestureTip by remember { mutableStateOf<GestureTip?>(null) }
    var dragActive by remember { mutableStateOf(false) }
    var resumeMs by remember { mutableStateOf<Long?>(null) } // 全屏退出后在指定位置续播
    val originalBright = remember {
        (activity?.window?.attributes?.screenBrightness ?: -1f).takeIf { it in 0f..1f } ?: -1f
    }
    val vv = remember(file) { VideoView(ctx) }

    // 亮度还原（离开页面时；只有改过才动）
    DisposableEffect(Unit) {
        onDispose {
            if (originalBright >= 0f) {
                val w = activity?.window ?: return@onDispose
                val lp = w.attributes; lp.screenBrightness = originalBright; w.attributes = lp
            }
        }
    }
    // 全屏时隐藏系统栏 + 横屏播放（用户要求），退出还原竖屏
    LaunchedEffect(fullscreen) {
        val w = activity?.window ?: return@LaunchedEffect
        val c = WindowInsetsControllerCompat(w, w.decorView)
        if (fullscreen) {
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.systemBars())
            activity.requestedOrientation =
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            c.show(WindowInsetsCompat.Type.systemBars())
            activity.requestedOrientation =
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED // 还原清单默认竖屏
        }
    }
    val exitFullscreen = {
        val p = position
        fullscreen = false
        controls = true
        resumeMs = if (p > 0) p else null // 内嵌层续播
    }
    BackHandler(enabled = fullscreen) { exitFullscreen() }

    if (fullscreen) {
        Dialog(
            onDismissRequest = { exitFullscreen() },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            FullscreenPlayer(
                file = file, startMs = position, audio = audio, activity = activity,
                onSeek = { position = it }, onDuration = { duration = it },
                onExit = { exitFullscreen() },
            )
        }
    } else {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(aspect)
                .heightIn(max = 360.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black),
        ) {
            VideoSurface(
                vv = vv, file = file, autoplay = autoplay,
                onAspect = { aspect = it }, onDuration = { duration = it },
                onPosition = { position = it }, onPlaying = { playing = it },
            )
            // 未播放时的海报（用户要求：VideoView 未播放是黑底，封面代替）
            // 条件=未播放且位置在片头——暂停在中间时显示真实画面，不动它
            if (!playing && position <= 500) {
                cover?.let { c ->
                    val model: Any? = if (c.startsWith("http")) c else File(c).takeIf { it.exists() } ?: c
                    AsyncImage(
                        model = model,
                        contentDescription = "视频封面",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.matchParentSize().background(Color.Black),
                    )
                }
            }
            // 全屏退出后的续播/续玩（resumeMs 变化时触发一次）
            LaunchedEffect(resumeMs) {
                val ms = resumeMs ?: return@LaunchedEffect
                if (ms > 0) vv.seekTo(ms.toInt())
                vv.start(); playing = true
            }
            // 手势层 + 单击层按顺序链在同一 Box 上（叠放会挡事件，链在同一节点两者都收）
            Box(
                Modifier
                    .fillMaxSize()
                    .videoGestures(vv, audio, activity, position, duration, { gestureTip = it }, { dragActive = it })
                    .pointerInput(Unit) {
                        detectTapGestures { if (gestureTip == null && !dragActive) controls = !controls }
                    },
            )
            // 中央大按钮（用户要求：暂停=大播放键，播放中=大暂停键，点击切换）
            if (!dragActive) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .background(if (playing) Color(0x33000000) else Color(0x66000000))
                            .clickable {
                                if (playing) { vv.pause(); playing = false }
                                else { vv.start(); playing = true }
                            }
                            .padding(12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (playing) IconPause(28.dp, Color.White)
                        else IconPlayFilled(28.dp, Color.White)
                    }
                }
            }
            // 控制条：进度 + 播放/暂停 + 时间 + 全屏
            if (controls) {
                PlayerControlBar(
                    vv = vv, playing = playing, position = position, duration = duration,
                    onPlaying = { playing = it }, fullscreen = false,
                    onFullscreen = { fullscreen = true },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
            gestureTip?.let { tip ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Row(
                        Modifier
                            .background(Color(0xAA000000), RoundedCornerShape(999.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        when (tip) {
                            is GestureTip.Brightness -> IconSun(14.dp, Color.White)
                            is GestureTip.Volume -> IconVolumeSpeaker(14.dp, Color.White)
                            is GestureTip.Seek -> {}
                        }
                        if (tip !is GestureTip.Seek) Spacer(Modifier.width(5.dp))
                        Text(tip.text, color = Color.White, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

/** 全屏播放层（Dialog 全屏黑底）：独立 VideoView，按传入位置续播；返回键/退出按钮回内嵌 */
@Composable
private fun FullscreenPlayer(
    file: File, startMs: Long,
    audio: AudioManager, activity: ComponentActivity?,
    onSeek: (Long) -> Unit, onDuration: (Long) -> Unit,
    onExit: () -> Unit,
) {
    val ctx = LocalContext.current
    val vv = remember(file) { VideoView(ctx) }
    var playing by remember { mutableStateOf(true) }
    var controls by remember { mutableStateOf(true) }
    var position by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var gestureTip by remember { mutableStateOf<GestureTip?>(null) }
    var dragActive by remember { mutableStateOf(false) }
    LaunchedEffect(vv) {
        vv.setOnPreparedListener { mp ->
            if (startMs > 0) mp.seekTo(startMs.toInt())
            mp.start(); playing = true
            onDuration(mp.duration.toLong())
        }
        vv.setOnCompletionListener { playing = false }
        vv.setOnErrorListener { _, _, _ -> playing = false; true }
        vv.setVideoPath(file.absolutePath)
    }
    // 注意：这里不能放空 BackHandler —— 它会注册在 Dialog 自己的返回分发器上把返回键吞掉，
    // 返回键交给 Dialog 的 onDismissRequest（=退出全屏回内嵌）
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VideoSurface(
            vv = vv, file = file, autoplay = true,
            onPosition = { position = it; onSeek(it) }, onDuration = { duration = it; onDuration(it) },
        )
        Box(
            Modifier
                .fillMaxSize()
                .videoGestures(vv, audio, activity, position, duration, { gestureTip = it }, { dragActive = it })
                .pointerInput(Unit) {
                    detectTapGestures { if (gestureTip == null && !dragActive) controls = !controls }
                },
        )
        if (controls) {
            PlayerControlBar(
                vv = vv, playing = playing, position = position, duration = duration,
                onPlaying = { playing = it }, fullscreen = true, onFullscreen = onExit,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
            Text(
                "返回内嵌播放", color = Color.White, fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(12.dp)
                    .background(Color(0xAA000000), RoundedCornerShape(999.dp))
                    .clickable { onExit() }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        gestureTip?.let { tip ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Row(
                    Modifier
                        .background(Color(0xAA000000), RoundedCornerShape(999.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    when (tip) {
                        is GestureTip.Brightness -> IconSun(14.dp, Color.White)
                        is GestureTip.Volume -> IconVolumeSpeaker(14.dp, Color.White)
                        is GestureTip.Seek -> {}
                    }
                    if (tip !is GestureTip.Seek) Spacer(Modifier.width(5.dp))
                    Text(tip.text, color = Color.White, fontSize = 13.sp)
                }
            }
        }
    }
}

/** VideoView 挂载 + 生命周期回调；位置轮询统一放这里 */
@Composable
private fun VideoSurface(
    vv: VideoView,
    file: File,
    autoplay: Boolean,
    onAspect: ((Float) -> Unit)? = null,
    onDuration: (Long) -> Unit = {},
    onPosition: (Long) -> Unit = {},
    onPlaying: (Boolean) -> Unit = {},
) {
    LaunchedEffect(vv, file) {
        vv.setVideoPath(file.absolutePath)
        vv.setOnPreparedListener { mp ->
            if (mp.videoWidth > 0 && mp.videoHeight > 0) onAspect?.invoke(mp.videoWidth.toFloat() / mp.videoHeight)
            onDuration(mp.duration.toLong())
            if (autoplay) { mp.start(); onPlaying(true) } else onPlaying(false)
        }
        vv.setOnCompletionListener { onPlaying(false) }
        vv.setOnErrorListener { _, _, _ -> onPlaying(false); true }
    }
    LaunchedEffect(vv) {
        while (true) {
            onPosition(vv.currentPosition.toLong())
            delay(400)
        }
    }
    AndroidView(factory = { vv }, modifier = Modifier.fillMaxSize())
}

/**
 * 手势 Modifier：左半区竖滑=亮度、右半区竖滑=音量、横滑=进度；
 * isDragActive 供单击层判断是否刚拖过（避免拖完误触发控制条切换）。
 */
private fun Modifier.videoGestures(
    vv: VideoView,
    audio: AudioManager,
    activity: ComponentActivity?,
    position: Long,
    duration: Long,
    setTip: (GestureTip?) -> Unit,
    setDragging: (Boolean) -> Unit,
): Modifier = pointerInput(vv) {
    var totalX = 0f; var totalY = 0f; var startX = 0f
    var mode = 0 // 0 未定 1 亮度 2 音量 3 进度
    var startB = 0.5f; var startV = 0; var startPos = 0L
    detectDragGestures(
        onDragStart = { startX = it.x; totalX = 0f; totalY = 0f; mode = 0; setTip(null) },
        onDrag = { change, amount ->
            change.consume()
            totalX += amount.x; totalY += amount.y
            val w = size.width.toFloat(); val h = size.height.toFloat()
            if (mode == 0) {
                mode = if (abs(totalX) > abs(totalY)) 3 else if (startX < w / 2f) 1 else 2
                setDragging(true)
                startB = (activity?.window?.attributes?.screenBrightness ?: 0.5f).takeIf { it >= 0f } ?: 0.5f
                startV = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                startPos = position
            }
            when (mode) {
                1 -> { // 亮度：上滑增
                    val v = (startB - totalY / h).coerceIn(0.02f, 1f)
                    activity?.window?.attributes?.apply {
                        screenBrightness = v
                        activity.window.attributes = this
                    }
                    setTip(GestureTip.Brightness((v * 100).toInt()))
                }
                2 -> { // 音量：上滑增
                    val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    val nv = (startV - totalY / h * max).roundToInt().coerceIn(0, max)
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, nv, 0)
                    setTip(GestureTip.Volume(nv * 100 / max))
                }
                3 -> { // 进度：右滑前进
                    val ms = (startPos + (totalX / w * duration.toFloat()).toLong()).coerceIn(0L, duration)
                    vv.seekTo(ms.toInt())
                    setTip(GestureTip.Seek("${fmtMs(ms)} / ${fmtMs(duration)}"))
                }
            }
        },
        onDragEnd = { setTip(null); setDragging(false) },
        onDragCancel = { setTip(null); setDragging(false) },
    )
}

/** 底部控制条：进度点按跳转 + 播放/暂停 + 时间 + 全屏切换 */
@Composable
private fun PlayerControlBar(
    vv: VideoView, playing: Boolean, position: Long, duration: Long,
    onPlaying: (Boolean) -> Unit, fullscreen: Boolean, onFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val frac = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Column(
        modifier
            .fillMaxWidth()
            .background(Color(0xAA000000))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        // 进度条：点击跳转
        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .pointerInput(Unit) {
                    detectTapGestures { off ->
                        if (duration > 0) {
                            val ms = ((off.x / size.width) * duration).toLong()
                            vv.seekTo(ms.toInt())
                        }
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.fillMaxWidth().height(3.dp).background(Color(0x55FFFFFF)))
            Box(Modifier.fillMaxWidth(frac).height(3.dp).background(Color(0xFFB4A7FF)))
        }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .clip(CircleShape)
                    .clickable {
                        if (playing) { vv.pause(); onPlaying(false) }
                        else { vv.start(); onPlaying(true) }
                    }
                    .padding(4.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (playing) IconPause(15.dp, Color.White) else IconPlayFilled(15.dp, Color.White)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "${fmtMs(position)} / ${fmtMs(duration)}",
                color = Color.White, fontSize = 11.sp,
            )
            Spacer(Modifier.weight(1f))
            Row(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onFullscreen() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (fullscreen) IconFullscreenExit(13.dp, Color.White) else IconFullscreen(13.dp, Color.White)
                Spacer(Modifier.width(4.dp))
                Text(if (fullscreen) "内嵌" else "全屏", color = Color.White, fontSize = 12.sp)
            }
        }
    }
}

private fun fmtMs(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}

/** 多图：横向滑动翻页 + 页码指示 + 点击放大 */
@Composable
private fun ImagePager(urls: List<String>, onOpenViewer: (Int) -> Unit) {
    val pagerState = rememberPagerState(pageCount = { urls.size })
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            AsyncImage(
                model = urls[page],
                contentDescription = "图 ${page + 1}",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenViewer(page) },
            )
        }
        // 页码指示
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(10.dp)
                .background(Color(0x99000000), RoundedCornerShape(999.dp))
                .padding(horizontal = 10.dp, vertical = 3.dp),
        ) { Text("${pagerState.currentPage + 1} / ${urls.size}", fontSize = 11.sp, color = Color.White) }
    }
    Spacer(Modifier.height(4.dp))
    Text("点击图片可放大查看", fontSize = 11.sp, color = TextFaint)
}

/** 单图：点击放大 */
@Composable
private fun SingleImage(url: String, onOpen: () -> Unit) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.FillWidth,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onOpen() },
    )
}

/** 全屏查看器：黑底 + Pager 左右切换 + 双击缩放 + 放大后拖动 */
@Composable
private fun ImageViewer(ctx: Context, urls: List<String>, initialPage: Int, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var viewerMsg by remember { mutableStateOf<String?>(null) }
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { urls.size })
    // 双指缩放（用户要求：pinch 1~5 倍 + 拖拽 + 越界钳制）；翻页自动复位
    var zoomScale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(pagerState.currentPage) { zoomScale = 1f; offset = Offset.Zero }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onDismiss() },
                        onDoubleTap = {
                            offset = Offset.Zero
                            zoomScale = if (zoomScale > 1f) 1f else 2.5f // 双击快速缩放
                        },
                    )
                },
        ) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = zoomScale == 1f, // 放大后禁止翻页，先缩小
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                AsyncImage(
                    model = urls[page],
                    contentDescription = "图 ${page + 1}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = zoomScale
                            scaleY = zoomScale
                            translationX = offset.x
                            translationY = offset.y
                        }
                        .pointerInput(page) {
                            detectTransformGestures(
                                onGesture = { _, pan, zoom, _ ->
                                    val ns = (zoomScale * zoom).coerceIn(1f, 5f)
                                    zoomScale = ns
                                    // 拖拽随手指；越界钳制（放大后的出画距离）
                                    val mx = (size.width * (ns - 1f)) / 2f
                                    val my = (size.height * (ns - 1f)) / 2f
                                    offset = Offset(
                                        (offset.x + pan.x).coerceIn(-mx, mx),
                                        (offset.y + pan.y).coerceIn(-my, my),
                                    )
                                    if (ns == 1f) offset = Offset.Zero
                                },
                            )
                        },
                )
            }
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 56.dp), // 避开系统手势导航条（原 32dp 会盖住底部文字）
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val alreadySaved = viewerMsg?.contains("已在相册") == true
                Row(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0x66000000))
                        .border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(999.dp))
                        .clickable {
                            if (alreadySaved) return@clickable
                            scope.launch {
                                runCatching {
                                    MediaSaver.saveImageToGallery(
                                        ctx, urls[pagerState.currentPage],
                                        "memmos-${pagerState.currentPage + 1}",
                                    )
                                }.onSuccess { r ->
                                    viewerMsg = if (r.existed) "该图片已在相册中" else "已保存到相册"
                                }.onFailure { viewerMsg = "保存失败" }
                            }
                        }
                        .padding(horizontal = 18.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Text(
                        if (alreadySaved) "✓ 已在相册中" else "保存到相册",
                        color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                    )
                }
                Spacer(Modifier.height(6.dp))
                viewerMsg?.let { Text(it, color = Color(0xFF8FD4AB), fontSize = 11.5.sp) }
            }
            // 页码置顶（用户反馈：原在底部会与保存按钮堆叠且被导航条遮挡）
            Text(
                "${pagerState.currentPage + 1} / ${urls.size}",
                color = Color.White, fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 14.dp)
                    .background(Color(0x66000000), RoundedCornerShape(999.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
            Text(
                "✕",
                color = Color.White,
                fontSize = 18.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 10.dp, end = 16.dp)
                    .clickable { onDismiss() },
            )
        }
    }
}

/* ───────────── 工具 ───────────── */

private fun persist(ctx: Context, note: ClipNote) {
    val list = ClipStore(ctx).load()
    val idx = list.indexOfFirst { it.id == note.id }
    if (idx >= 0) list[idx] = note
    ClipStore(ctx).save(list)
}

/** 评论卡片：头像 + 昵称 + 内容 + 点赞；indent=true 为子评论（缩进+浅底） */
@Composable
private fun CommentCard(c: ClipComment, indent: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .padding(start = if (indent) 26.dp else 0.dp)
            .background(
                if (indent) Color(0x08FFFFFF) else GlassFill,
                RoundedCornerShape(12.dp),
            )
            .border(1.dp, GlassStrokeSoft, RoundedCornerShape(12.dp))
            .padding(11.dp),
    ) {
        if (c.avatar.isNotBlank()) {
            AsyncImage(
                model = c.avatar,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(24.dp).clip(CircleShape),
            )
        } else {
            Box(Modifier.size(24.dp).background(Color(0x2EFFFFFF), CircleShape))
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(
                c.nickname.ifBlank { "匿名用户" },
                fontSize = 11.sp, color = TextFaint,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                c.content,
                fontSize = 13.sp, color = TextHi.copy(alpha = 0.92f), lineHeight = 19.sp,
            )
            if (c.likes > 0) {
                Spacer(Modifier.height(4.dp))
                Text("♥ ${c.likes}", fontSize = 10.sp, color = TextFaint)
            }
        }
    }
}

/** vault 来源剪藏的 md 渲染（Obsidian 兼容语法，见 ui/md/MarkdownView.kt） */
@Composable
private fun MdInlineView(md: String, originPath: String?) {
    val ctx = LocalContext.current
    MarkdownView(md = md, originPath = originPath, vaultRoot = java.io.File(ctx.filesDir, "vault"))
}

@Composable
private fun TagPill(text: String) {
    Text(
        text,
        fontSize = 11.sp,
        color = Color(0xFFD9D0FF),
        modifier = Modifier
            .background(Color(0x2E8B7BFF), RoundedCornerShape(999.dp))
            .border(1.dp, Color(0x668B7BFF), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun ActionRow(label: String, action: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .background(GlassFill, RoundedCornerShape(12.dp))
            .border(1.dp, GlassStroke, RoundedCornerShape(12.dp))
            .padding(horizontal = 13.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 13.sp, color = TextMid, modifier = Modifier.weight(1f))
        Text(action, fontSize = 13.sp, color = Color(0xFFB4A7FF), fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable(onClick = onClick))
    }
}

private fun copy(ctx: Context, text: String) {
    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
        .setPrimaryClip(ClipData.newPlainText("memmos", text))
}
