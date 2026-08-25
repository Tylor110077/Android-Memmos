package com.tylor.memmos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tylor.memmos.ui.GlyphIcon
import com.tylor.memmos.ui.theme.AccentVioletSoft
import com.tylor.memmos.ui.theme.GlassFill
import com.tylor.memmos.ui.theme.GlassStrokeSoft
import com.tylor.memmos.ui.theme.Shapes
import com.tylor.memmos.ui.theme.TextFaint
import com.tylor.memmos.ui.theme.TextHi
import com.tylor.memmos.ui.theme.TextMid

/**
 * 样式基元组件（v4 规范，参考 Linear/Raycast/Notion）：
 * - GlassCard：统一玻璃卡（16dp 圆角 + 玻璃底 + 发丝描边 + 可选点击）
 * - SectionTitle：分区标题（小字号大写间距，弱色）
 * - EmptyState：空态（图标 + 标题 + 引导文案 + 可选动作按钮）
 * - PillAction：胶囊文字动作按钮
 */

/** 标准玻璃卡：全站卡片统一走这里，改造时勿再手写 fillMaxWidth+GlassFill 组合 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val base = modifier
        .clip(Shapes.Card)
        .background(GlassFill)
        .border(1.dp, GlassStrokeSoft, Shapes.Card)
    Box(
        if (onClick != null) base.clickable(onClick = onClick) else base,
    ) {
        content()
    }
}

/** 分区标题：小节标签（弱提示色 + 字距） */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        fontSize = 11.sp, letterSpacing = 1.6.sp, color = TextFaint,
        modifier = modifier,
    )
}

/**
 * 空态（Notion 式引导）：大描边图标 + 标题 + 说明 + 可选动作按钮。
 * icon: GlyphIcon 绘制的 24x24 图形（draw24 闭包经 GlyphIcon 缩放）。
 */
@Composable
fun EmptyState(
    title: String,
    desc: String,
    modifier: Modifier = Modifier,
    iconDraw: (androidx.compose.ui.graphics.drawscope.DrawScope.(androidx.compose.ui.graphics.drawscope.Stroke) -> Unit)? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (iconDraw != null) {
            GlyphIcon(44.dp, TextFaint, strokeWidth = 1.4f, draw24 = iconDraw)
        }
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextHi)
        Text(
            desc,
            fontSize = 11.5.sp, lineHeight = 17.sp, color = TextFaint,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 36.dp),
        )
        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(6.dp))
            PillAction(actionText, onAction)
        }
    }
}

/** 胶囊文字动作（次级操作一致的形态） */
@Composable
fun PillAction(text: String, onClick: () -> Unit) {
    Text(
        text,
        fontSize = 12.sp, color = AccentVioletSoft, fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(Shapes.Pill)
            .background(Color(0x1A8B7BFF))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}
