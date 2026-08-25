package com.tylor.memmos.ui.viewer

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.tylor.memmos.ui.md.MarkdownView
import com.tylor.memmos.ui.theme.Ink
import com.tylor.memmos.ui.theme.TextFaint
import com.tylor.memmos.ui.theme.TextHi
import com.tylor.memmos.ui.theme.TextMid
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Vault 文件查看器：按扩展名分发内置渲染或外部 App 打开。
 * 支持：md（轻量渲染）、pdf（PdfRenderer 逐页）、图片（直接显示）、视频（调播放器）、其他（外部打开）。
 */
class FileViewerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val path = intent.getStringExtra("path") ?: return finish()
        val file = File(filesDir, "vault/$path")
        if (!file.exists()) return finish()

        setContent {
            ViewerScreen(file, path)
        }
    }
}

@Composable
private fun ViewerScreen(file: File, displayPath: String) {
    val ctx = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .statusBarsPadding(),
    ) {
        // 顶栏
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("←", color = TextHi, fontSize = 20.sp,
                modifier = Modifier.clip(RoundedCornerShape(10.dp))
                    .clickable { (ctx as? ComponentActivity)?.finish() }
                    .padding(horizontal = 8.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(file.name, color = TextHi, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(displayPath.substringBeforeLast('/'), color = TextFaint, fontSize = 10.sp)
            }
            Text("外部打开", color = Color(0xFF6EE7B7), fontSize = 12.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { openExternal(file, ctx) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }

        when (file.extension.lowercase()) {
            "md" -> MarkdownView(
                md = file.readText(),
                originPath = displayPath,
                vaultRoot = File(ctx.filesDir, "vault"),
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
            "pdf" -> PdfView(file)
            "png", "jpg", "jpeg", "webp", "gif" -> ImageViewer(file)
            "mp4", "mov" -> VideoViewer(file, ctx)
            else -> UnknownViewer(file, ctx)
        }
    }
}

private fun openExternal(file: File, ctx: android.content.Context) {
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
    val mime = android.webkit.MimeTypeMap.getSingleton()
        .getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
    ctx.startActivity(
        Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
    )
}

/* ───────────── PDF 逐页渲染（PdfRenderer） ───────────── */

@Composable
private fun PdfView(file: File) {
    var pageCount by remember { mutableStateOf(0) }
    var pages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(file) {
        withContext(Dispatchers.IO) {
            runCatching {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                    PdfRenderer(fd).use { renderer ->
                        pageCount = renderer.pageCount
                        val out = mutableListOf<Bitmap>()
                        for (i in 0 until renderer.pageCount) {
                            renderer.openPage(i).use { page ->
                                val w = 1080
                                val h = (w.toFloat() / page.width * page.height).toInt()
                                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                bmp.eraseColor(android.graphics.Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                out.add(bmp)
                            }
                        }
                        pages = out
                    }
                }
            }.onFailure { e -> error = e.message }
        }
    }

    if (error != null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("PDF 打开失败：$error", color = TextFaint, fontSize = 12.sp)
        }
        return
    }
    if (pages.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("渲染中… $pageCount 页", color = TextFaint, fontSize = 12.sp)
        }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(pages) { i, bmp ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "第 ${i + 1} 页",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.FillWidth,
                )
                Text("${i + 1} / $pageCount", color = TextFaint, fontSize = 10.sp,
                    modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/* ───────────── 图片查看 ───────────── */

@Composable
private fun ImageViewer(file: File) {
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp)) {
        AsyncImage(
            model = file,
            contentDescription = file.name,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.FillWidth,
        )
    }
}

/* ───────────── 视频（调外部播放器） ───────────── */

@Composable
private fun VideoViewer(file: File, ctx: android.content.Context) {
    LaunchedEffect(file) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/mp4")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
        (ctx as? ComponentActivity)?.finish()
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("正在调起播放器…", color = TextFaint, fontSize = 12.sp)
    }
}

/* ───────────── 其他类型（外部打开） ───────────── */

@Composable
private fun UnknownViewer(file: File, ctx: android.content.Context) {
    LaunchedEffect(file) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        val mime = android.webkit.MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
        (ctx as? ComponentActivity)?.finish()
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("正在调起外部应用…", color = TextFaint, fontSize = 12.sp)
    }
}
