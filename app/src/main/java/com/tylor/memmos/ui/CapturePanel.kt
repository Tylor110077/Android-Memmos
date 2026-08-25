package com.tylor.memmos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import com.tylor.memmos.data.ClipStore
import com.tylor.memmos.sync.SyncPrefs
import com.tylor.memmos.ui.fetch.XhsCaptureService
import com.tylor.memmos.ui.theme.BtnPrimaryBg
import com.tylor.memmos.ui.theme.BtnPrimaryText
import com.tylor.memmos.ui.theme.ChipBg
import com.tylor.memmos.ui.theme.ChipStroke
import com.tylor.memmos.ui.theme.ChipText
import com.tylor.memmos.ui.theme.GlassFill
import com.tylor.memmos.ui.theme.GlassStrokeSoft
import com.tylor.memmos.ui.theme.Success
import com.tylor.memmos.ui.theme.TextFaint
import com.tylor.memmos.ui.theme.TextHi
import com.tylor.memmos.ui.theme.TextMid

/**
 * 功能框主面板：从滑块侧滑入，覆盖宿主 ~86% 宽。
 * 只保留实际功能（用户要求删除演示内容）：
 * - 头像行：标题 / 真实配对状态 / 设置 / 关闭
 * - 抓取当前笔记（后台管线，进度实时显示）
 * - 最近剪藏（真实数据，点击进详情）
 */
@Composable
fun CapturePanel(
    onClose: () -> Unit,
    onOpenSettings: () -> Unit,
    onCaptureCurrent: () -> Unit,
    onOpenNote: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val cap by XhsCaptureService.state.collectAsState()
    val paired = remember { SyncPrefs.load(ctx) != null }
    // 抓取完成后刷新最近列表
    val recent = remember(cap.done) { ClipStore(ctx).load().take(3) }

    Column(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0xE61A1D26), Color(0xF30E1016))))
            // 上下 34dp：NO_LIMITS 后窗口延伸进状态栏/手势区，补偿避免内容被系统 UI 压住
            .padding(start = 20.dp, end = 20.dp, top = 34.dp, bottom = 34.dp),
    ) {
        // 头部：标题 + 配对状态 + 设置（齿轮）/关闭（用户要求：无横杠、齿轮更大、chip 与文案留距）
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Memmos 捕捉", color = TextHi, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(10.dp)) // 配对 chip 与标题文本的最小间距（用户反馈太近）
            PairedChip(paired)
            Spacer(Modifier.width(10.dp))
            Text(
                "⚙",
                fontSize = 22.sp, color = Color(0xFFBFC4CE),
                modifier = Modifier.padding(4.dp).clickable { onOpenSettings() }, // 与主页设置按钮同款
            )
            Spacer(Modifier.width(12.dp))
            IconClose(20.dp, Color(0xFFBFC4CE), Modifier.padding(4.dp).clickable { onClose() })
        }
        Spacer(Modifier.height(14.dp))

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            SectionLabel("最近剪藏")
            if (recent.isEmpty()) {
                com.tylor.memmos.ui.components.EmptyState(
                    title = "暂无剪藏",
                    desc = "点上方 抓取当前笔记，或在内容 App「分享 → 更多 → Memmos」抓取第一篇",
                )
            } else {
                recent.forEach { n ->
                    RecentRow(note = n) { onOpenNote(n.id) }
                }
            }
            Spacer(Modifier.height(12.dp))
SectionLabel("快速抓取")
            // 后台抓取入口（剪贴板识别 / 小红书分享面板直达），进度实时显示
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(ChipBg)
                    .border(1.dp, ChipStroke, RoundedCornerShape(16.dp))
                    .clickable(enabled = !cap.running) { onCaptureCurrent() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (cap.running) "抓取中…（进度见下方）" else "抓取当前笔记",
                    color = ChipText, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                )
            }
            if (cap.running) {
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${cap.status} ${(cap.progress * 100).toInt()}%",
                        fontSize = 11.sp, color = TextFaint,
                        modifier = Modifier.weight(1f),
                    )
                    Text("后台进行中", fontSize = 10.sp, color = Success)
                }
                Spacer(Modifier.height(5.dp))
                LinearProgressIndicator(
                    progress = { cap.progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(99.dp)),
                    color = ChipText,
                    trackColor = Color(0x33FFFFFF),
                )
            } else if (cap.done == true || cap.done == false) {
                Spacer(Modifier.height(8.dp))
                Text(
                    cap.status,
                    fontSize = 11.sp, color = if (cap.done == true) Success else Color(0xFFB4A7FF),
                )
            }
            Text(
                "① 在内容 App「分享 → 更多 → Memmos」识别当前帖子（当前支持小红书）；② 复制链接后点这里。均后台完成。",
                fontSize = 10.sp, color = TextFaint, lineHeight = 15.sp,
                modifier = Modifier.padding(top = 5.dp),
            )

            
        }
    }
}

/** 配对状态：读真实 SyncPrefs（演示版为写死文案，已替换） */
@Composable
private fun PairedChip(paired: Boolean) {
    Row(
        Modifier
            .background(
                if (paired) Color(0x1F46C882) else Color(0x14FFFFFF),
                RoundedCornerShape(999.dp),
            )
            .border(
                1.dp,
                if (paired) Color(0x4D46C882) else GlassStrokeSoft,
                RoundedCornerShape(999.dp),
            )
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(5.dp).background(if (paired) Success else TextFaint, CircleShape))
        Spacer(Modifier.width(5.dp))
        Text(
            if (paired) "已配对 · 局域网" else "未配对",
            fontSize = 11.sp, color = if (paired) Success else TextFaint,
        )
    }
}

/** 最近剪藏行：真实数据（封面/标题/时间/类型角标），点击进详情 */
@Composable
private fun RecentRow(note: com.tylor.memmos.data.ClipNote, onClick: () -> Unit) {
    val ctx = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x0AFFFFFF))
            .border(1.dp, Color(0x12FFFFFF), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val cover = note.imageUrls.firstOrNull()
        if (cover != null) {
            AsyncImage(
                model = cover,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(9.dp)),
            )
        } else {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(9.dp))
                    .background(ChipBg),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (note.type == "video") "▶" else if (note.origin == "vault") "M" else "文",
                    fontSize = 13.sp, color = ChipText, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                note.title.ifBlank { "未命名笔记" },
                fontSize = 12.sp, color = TextHi.copy(alpha = 0.88f),
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            val author = note.author.ifBlank {
                if (note.origin == "vault") {
                    com.tylor.memmos.util.AppPrefs.profileName(ctx).ifBlank { "未知作者" }
                } else "未知作者"
            }
            Text(
                "$author · ${ClipStore.fmtTime(note.clippedAt)}",
                fontSize = 10.sp, color = TextFaint,
            )
        }
        if (note.type == "video") {
            Text("视频", fontSize = 9.5.sp, color = ChipText,
                modifier = Modifier.background(ChipBg, RoundedCornerShape(999.dp)).padding(horizontal = 7.dp, vertical = 2.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 11.sp, letterSpacing = 2.sp,
        color = TextFaint,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}

/** 三道横线的调节图标，兼作设置入口（GlyphIcon/line 来自同包 Icons.kt） */
@Composable
fun IconSliders(size: androidx.compose.ui.unit.Dp, tint: Color, modifier: Modifier = Modifier) = GlyphIcon(size, tint, modifier) { st ->
    line(4f, 6f, 20f, 6f, st, tint)
    line(4f, 12f, 20f, 12f, st, tint)
    line(4f, 18f, 20f, 18f, st, tint)
    drawCircle(tint, radius = 2.4f, center = androidx.compose.ui.geometry.Offset(15f, 6f))
    drawCircle(tint, radius = 2.4f, center = androidx.compose.ui.geometry.Offset(8f, 12f))
    drawCircle(tint, radius = 2.4f, center = androidx.compose.ui.geometry.Offset(16f, 18f))
}
