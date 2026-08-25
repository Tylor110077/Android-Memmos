package com.tylor.memmos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import com.tylor.memmos.data.ClipStore
import com.tylor.memmos.sync.SyncPrefs
import com.tylor.memmos.ui.components.GlassCircleButton
import com.tylor.memmos.ui.components.VisionRowCard
import com.tylor.memmos.ui.fetch.XhsCaptureService
import com.tylor.memmos.ui.theme.BtnPrimaryBg
import com.tylor.memmos.ui.theme.BtnPrimaryText
import com.tylor.memmos.ui.theme.ChipBg
import com.tylor.memmos.ui.theme.ChipText
import com.tylor.memmos.ui.theme.RingWhite
import com.tylor.memmos.ui.theme.Success
import com.tylor.memmos.ui.theme.TextFaint
import com.tylor.memmos.ui.theme.TextHi
import com.tylor.memmos.ui.theme.TextSoft

/**
 * 功能框主面板：从滑块侧滑入，覆盖宿主 ~86% 宽。
 * 只保留实际功能（用户要求删除演示内容）：
 * - 头像行：标题 / 真实配对状态 / 设置 / 关闭
 * - 抓取当前笔记（后台管线，进度实时显示）
 * - 最近剪藏（真实数据，点击进详情）
 * 视觉：模板同款环境光背景 + 渐变发丝壳玻璃卡 + 白色主按钮（官方配方）。
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

    Box(modifier.fillMaxWidth()) {
        // 背景：有一点透明的纯黑（用户要求，去掉环境背景图；90% 黑底下宿主隐约可见但不影响阅读）
        Box(Modifier.fillMaxSize().background(Color(0xE6000000)))
        BoxWithConstraints(
            Modifier.fillMaxWidth()
                // 上下 34dp：NO_LIMITS 后窗口延伸进状态栏/手势区，补偿避免内容被系统 UI 压住
                .padding(start = 20.dp, end = 20.dp, top = 34.dp, bottom = 34.dp),
        ) {
            // 黄金比例锚点：按钮顶落在面板可用高度 61.8% 处
            // content 高度构成：header 40 + spacer 14 + 分区标签 ~40（anchorOffset = 54 + 40）
            val anchor = maxHeight * 0.618f
            Column(Modifier.fillMaxSize()) {
                // 头部（模板 Header）：玻璃圆形设置/关闭钮 + 标题 + 配对 chip
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Memmos 捕捉",
                        color = Color(0xFFF5F7FA), fontSize = 15.sp, fontWeight = FontWeight.Medium,
                        letterSpacing = (-0.3).sp,
                    )
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.width(10.dp)) // 配对 chip 与标题文本的最小间距（用户反馈太近）
                    PairedChip(paired)
                    Spacer(Modifier.width(10.dp))
                    GlassCircleButton(
                        size = 40.dp,
                        content = { IconGear(20.dp, Color.White) },
                        onClick = { onOpenSettings() },
                    )
                    Spacer(Modifier.width(10.dp))
                    GlassCircleButton(
                        size = 42.dp,
                        content = { IconClose(22.dp, Color.White) },
                        onClick = { onClose() },
                    )
                }
                Spacer(Modifier.height(14.dp))

                // 最近剪藏滚动区：止于黄金锚点
                Column(
                    Modifier
                        .fillMaxWidth()
                        .height((anchor - 94.dp).coerceAtLeast(0.dp))
                        .verticalScroll(rememberScrollState()),
                ) {
                    SectionLabel("最近剪藏")
                    if (recent.isEmpty()) {
                        com.tylor.memmos.ui.components.EmptyState(
                            title = "暂无剪藏",
                            desc = "点下方 抓取当前笔记，或在小红书复制笔记链接后点它",
                        )
                    } else {
                        recent.forEach { n ->
                            RecentRow(note = n) { onOpenNote(n.id) }
                        }
                    }
                }

                // 快速抓取区：黄金位以下（按钮顶 ≈ 61.8%）
                Column(Modifier.fillMaxWidth().weight(1f)) {
                    SectionLabel("快速抓取")
                    Spacer(Modifier.height(10.dp))
                    // 后台抓取入口（剪贴板识别），进度实时显示
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(BtnPrimaryBg)
                            .clickable(enabled = !cap.running) { onCaptureCurrent() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (cap.running) "抓取中…（进度见下方）" else "抓取当前笔记",
                            color = BtnPrimaryText, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                            letterSpacing = 0.3.sp,
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
                                fontSize = 11.sp, color = TextSoft,
                                modifier = Modifier.weight(1f),
                            )
                            Text("后台进行中", fontSize = 10.sp, color = Success)
                        }
                        Spacer(Modifier.height(5.dp))
                        LinearProgressIndicator(
                            progress = { cap.progress },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(99.dp)),
                            color = ChipText,
                            trackColor = Color(0x29FFFFFF),
                        )
                    } else if (cap.done == true || cap.done == false) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            cap.status,
                            fontSize = 11.sp, color = if (cap.done == true) Success else ChipText,
                        )
                    }
                    Text(
                        "在小红书复制笔记链接后，点这里即可抓取（后台完成）。",
                        fontSize = 10.sp, color = TextSoft, lineHeight = 15.sp,
                        modifier = Modifier.padding(top = 5.dp),
                    )
                }
            }
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
                if (paired) Color(0x4D46C882) else Color(0x26FFFFFF),
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

/** 最近剪藏行（模板 Recent sessions 行）：发丝壳玻璃卡 + 封面描边环 + 类型角标 */
@Composable
private fun RecentRow(note: com.tylor.memmos.data.ClipNote, onClick: () -> Unit) {
    val ctx = LocalContext.current
    VisionRowCard(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        radius = 14.dp,
        contentModifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val cover = note.imageUrls.firstOrNull()
            Box(
                Modifier.size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, RingWhite, RoundedCornerShape(12.dp)),
            ) {
                if (cover != null) {
                    AsyncImage(
                        model = cover,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        Modifier.fillMaxSize().background(ChipBg),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(if (note.origin == "vault") "M" else "文",
                            fontSize = 13.sp, color = ChipText, fontWeight = FontWeight.Bold)
                    }
                }
                if (note.type == "video") {
                    Box(
                        Modifier.align(Alignment.BottomEnd).padding(3.dp).size(15.dp)
                            .background(Color(0xB3000000), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { IconPlayFilled(8.dp, Color.White) }
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    note.title.ifBlank { "未命名笔记" },
                    fontSize = 12.5.sp, color = TextHi,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                val author = note.author.ifBlank {
                    if (note.origin == "vault") {
                        com.tylor.memmos.util.AppPrefs.profileName(ctx).ifBlank { "未知作者" }
                    } else "未知作者"
                }
                Text(
                    "$author · ${ClipStore.fmtTime(note.clippedAt)}",
                    fontSize = 10.sp, color = TextSoft,
                )
            }
            if (note.type == "video") {
                Text("视频", fontSize = 9.5.sp, color = ChipText,
                    modifier = Modifier.background(ChipBg, RoundedCornerShape(999.dp)).padding(horizontal = 7.dp, vertical = 2.dp))
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 11.sp, letterSpacing = 2.sp,
        color = TextSoft,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}
