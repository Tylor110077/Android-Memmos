package com.tylor.memmos.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** 滑块停靠的四条边（吸附目标） */
enum class TabEdge { LEFT, RIGHT, TOP, BOTTOM }

/** 滑块本体配色（纯色，用户要求绿/白/深灰三选一） */
enum class TabColor(val body: Color) {
    GREEN(Color(0xFF10B981)), // 品牌绿
    WHITE(Color(0xFFF5F7FA)), // 纯白
    DARK(Color(0xFF3A3F4B)),  // 深灰
}

/**
 * 贴边滑块（柱状胶囊版）：
 * 柱状本体、头尾两角全圆（半径=宽度一半，标准胶囊）；纯色统一填充（无渐变）；
 * 触控热区由外层容器保证 ≥48dp（余量全部留在屏幕内侧，见 FloatingService）。
 * alpha=不透明度滑块（0.4→1.0，图层整体透明度）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EdgeTab(
    edge: TabEdge,
    barWidth: Float,
    barLength: Float,
    alpha: Float,
    color: TabColor,
    dragging: Boolean,
    /** 面板打开/被拖拽时全透明可见；静止态乘 0.55 更低调（用户要求触发器存在更不明显） */
    active: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val w = barWidth
    val h = barLength
    // 柱状胶囊：头尾全圆（用户要求「头尾为类似圆形」）
    val shape = RoundedCornerShape(999.dp)
    // 透明度/阴影在「拖拽中 ↔ 静止」切换时渐变过渡：瞬时跳变在滑动过程中表现为
    // 浮条本体闪亮/闪暗（用户反馈「闪动的内容是浮条的东西」）
    val effTarget = (alpha * (if (dragging || active) 1f else 0.55f)).coerceIn(0f, 1f)
    val eff by animateFloatAsState(effTarget, tween(180), label = "tabEff")
    val shadowElev by animateDpAsState(if (dragging) 14.dp else 6.dp, tween(180), label = "tabShadow")
    Box(
        modifier
            .graphicsLayer { this.alpha = eff }
            // 滑块可视区排除系统返回手势：向屏幕内滑的手势让给面板打开（API 29+，低版本无手势导航）
            .systemGestureExclusion()
            .size(w.dp, h.dp)
            .shadow(shadowElev, shape)
            .clip(shape)
            .background(color.body, shape)
            // 深灰色在暗宿主上会融掉，加一道白 25% 描边保轮廓；绿/白本体自带对比不描边
            .then(if (color == TabColor.DARK) Modifier.border(1.dp, Color(0x40FFFFFF), shape) else Modifier),
    )
}
