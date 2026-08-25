package com.tylor.memmos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tylor.memmos.ui.components.AmbientBackdrop
import com.tylor.memmos.ui.theme.AccentBrush
import com.tylor.memmos.ui.theme.IslandFill
import com.tylor.memmos.ui.theme.TextFaint
import com.tylor.memmos.ui.theme.TextHi
import com.tylor.memmos.ui.theme.TextMid
import com.tylor.memmos.ui.theme.TextSoft

/**
 * 浮条设置抽屉（设计稿场景 06）。
 * 与 MemmosApp 的滑块状态直接双向绑定：改边、高度、大小、不透明度立即生效。
 */
@Composable
fun SettingsSheet(
    edge: TabEdge,
    frac: Float,
    opacity: Float,
    barWidth: Float,
    barLength: Float,
    onEdgeChange: (TabEdge) -> Unit,
    onFracChange: (Float) -> Unit,
    onOpacityChange: (Float) -> Unit,
    onWidthChange: (Float) -> Unit,
    onLengthChange: (Float) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .fillMaxHeight(0.84f),
    ) {
        // 环境背景：与主界面/面板同一片环境光（模板 Ambient Background）
        AmbientBackdrop(Modifier.fillMaxSize(), alpha = 0.42f)
        Column(
            Modifier
                .fillMaxSize()
                .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
        // 把手
        Box(
            Modifier.size(width = 34.dp, height = 4.dp)
                .clip(CircleShape)
                .background(Color(0x38FFFFFF))
                .align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("浮条设置", fontSize = 16.5.sp, fontWeight = FontWeight.Bold, color = TextHi)
            Text("完成", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6EE7B7),
                modifier = Modifier.clickable { onDismiss() })
        }
        Spacer(Modifier.height(16.dp))

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            SectionLabel("贴 边 位 置（长按滑块拖动亦可）")
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 演示示意图：只显示真实浮条（左右贴边，位置/大小实时）
                PositionWidget(edge, frac, barWidth, barLength)
                Spacer(Modifier.width(15.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // 分段控件（模板 Mesh/Depth：黑 30% 胶囊容器 + 选中白胶囊黑字）
            Row(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(IslandFill)
                    .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(999.dp))
                    .padding(4.dp),
            ) {
                EdgeChip("左缘", edge == TabEdge.LEFT) { onEdgeChange(TabEdge.LEFT) }
                Spacer(Modifier.width(4.dp))
                EdgeChip("右缘", edge == TabEdge.RIGHT) { onEdgeChange(TabEdge.RIGHT) }
            }
                    Text(
                        "位置 ${(frac * 100).toInt()}%\n长按滑块拖拽可在左右缘间切换（震动提示）。",
                        fontSize = 11.sp, lineHeight = 19.sp, color = TextMid,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            // 滑杆：左右贴边时调纵向位置一致
            SliderRow(
                label = "贴边位置",
                value = frac, valueText = "${(frac * 100).toInt()}%",
            ) { onFracChange(it) }
            // 宽/长独立（用户要求：浮条同时设定宽度和长度）
            SliderRow(
                label = "宽度",
                value = (barWidth - 12f) / 24f,
                valueText = "${barWidth.toInt()}dp",
            ) { onWidthChange(12f + it * 24f) }
            SliderRow(
                label = "长度",
                value = (barLength - 48f) / 102f,
                valueText = "${barLength.toInt()}dp",
            ) { onLengthChange(48f + it * 102f) }
            SliderRow(
                label = "不透明度",
                value = (opacity - 0.4f) / 0.6f,
                valueText = "${(opacity * 100).toInt()}%",
            ) { onOpacityChange(0.4f + it * 0.6f) }
            Spacer(Modifier.height(10.dp))
        }
        }
    }
}


@Composable
private fun SectionLabel(text: String) {
    Text(text, fontSize = 11.sp, letterSpacing = 1.6.sp, color = TextSoft)
}

/** 手机轮廓示意图（只保留一条真实演示条；方向切换在下方「左缘/右缘」控制） */
@Composable
private fun PositionWidget(
    edge: TabEdge,
    frac: Float,
    barWidth: Float,
    barLength: Float,
) {
    val lMini = (24f + (barLength - 48f) / 102f * 88f).coerceIn(20f, 112f)
    val wMini = (3f + (barWidth - 12f) / 24f * 6f).coerceIn(3f, 9f)
    Box(
        Modifier
            .size(width = 74.dp, height = 128.dp)
            .border(1.5.dp, Color(0x47FFFFFF), RoundedCornerShape(12.dp)),
    ) {
        when (edge) {
            TabEdge.LEFT -> Box(
                Modifier.offset(x = 0.dp, y = ((128f - lMini) * frac).dp)
                    .size(width = wMini.dp, height = lMini.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(AccentBrush),
            )
            TabEdge.RIGHT -> Box(
                Modifier.offset(x = (74f - wMini).dp, y = ((128f - lMini) * frac).dp)
                    .size(width = wMini.dp, height = lMini.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(AccentBrush),
            )
            else -> {} // 只保留左右贴边
        }
    }
}

/** 贴边方向分段按钮（模板 seg-btn：选中白底黑字，未选中白/60） */
@Composable
private fun EdgeChip(label: String, selected: Boolean, onSelect: () -> Unit) {
    Text(
        label,
        fontSize = 12.sp, fontWeight = FontWeight.Medium,
        color = if (selected) Color(0xFF09090B) else TextSoft,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) Color.White else Color.Transparent)
            .clickable { onSelect() }
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

@Composable
private fun SliderRow(label: String, value: Float, valueText: String, onChange: (Float) -> Unit) {
    var trackW by remember { mutableStateOf(1f) }
    val density = LocalDensity.current
    fun setFromX(x: Float) {
        if (trackW > 0f) onChange((x / trackW).coerceIn(0f, 1f))
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 13.sp, color = TextHi.copy(alpha = 0.8f))
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .weight(1f)
                .height(18.dp)
                .onSizeChanged { trackW = it.width.toFloat() }
                .pointerInput("tap") {
                    detectTapGestures(onTap = { off -> setFromX(off.x) })
                }
                .pointerInput("drag") {
                    detectHorizontalDragGestures { change, _ ->
                        setFromX(change.position.x)
                        change.consume()
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.fillMaxWidth().height(5.dp).background(Color(0x17FFFFFF), RoundedCornerShape(99.dp)))
            Box(
                Modifier.fillMaxWidth(value.coerceIn(0.02f, 1f)).height(5.dp).background(AccentBrush, RoundedCornerShape(99.dp)),
            )
            val knobX = with(density) { (trackW * value.coerceIn(0f, 1f)).toDp() }
            Box(
                Modifier
                    .offset(x = knobX - 8.dp)
                    .size(16.dp)
                    .background(Color.White, CircleShape),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(valueText, fontSize = 11.sp, color = TextFaint, modifier = Modifier.width(36.dp))
    }
}
