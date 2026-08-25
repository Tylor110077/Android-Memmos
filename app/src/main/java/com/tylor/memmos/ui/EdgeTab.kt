package com.tylor.memmos.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** 滑块停靠的四条边（吸附目标） */
enum class TabEdge { LEFT, RIGHT, TOP, BOTTOM }

/**
 * 贴边滑块（方向 A · 简洁版）：
 * 20×88dp 细条紧贴屏幕边缘，无内部装饰与常驻动效，靠玻璃渐变 + 发丝描边表达质感；
 * 朝屏幕外侧的两角做成半圆（半径 = 宽度一半），像从边框里「长出来」的一枚胶囊。
 * 触控热区由外层容器保证 ≥48dp（余量全部留在屏幕内侧，见 FloatingService）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EdgeTab(
    edge: TabEdge,
    barWidth: Float,
    barLength: Float,
    alpha: Float,
    dragging: Boolean,
    modifier: Modifier = Modifier,
) {
    val w = barWidth
    val h = barLength
    // 外侧两角全圆（半圆收头），贴边侧两角微圆
    val rEdge = 4.dp
    val rOuter = (w / 2f).dp
    val shape = when (edge) {
        TabEdge.LEFT -> RoundedCornerShape(topStart = rEdge, topEnd = rOuter, bottomEnd = rOuter, bottomStart = rEdge)
        TabEdge.RIGHT -> RoundedCornerShape(topStart = rOuter, topEnd = rEdge, bottomEnd = rEdge, bottomStart = rOuter)
        TabEdge.TOP -> RoundedCornerShape(topStart = rEdge, topEnd = rEdge, bottomEnd = rOuter, bottomStart = rOuter)
        TabEdge.BOTTOM -> RoundedCornerShape(topStart = rOuter, topEnd = rOuter, bottomEnd = rEdge, bottomStart = rEdge)
    }
    // 玻璃实度：不透明度滑块 0.4→1.0 映射到「填充透明度」0.18→1.0——
    // 只靠图层透明度时上限也是半透玻璃，用户要求最不透明状态=完全不透明（纯白实心）
    val s = ((alpha - 0.4f) / 0.6f).coerceIn(0f, 1f)
    Box(
        modifier
            .graphicsLayer { this.alpha = alpha }
            // 滑块可视区排除系统返回手势：向屏幕内滑的手势让给面板打开（API 29+，低版本无手势导航）
            .systemGestureExclusion()
            .size(w.dp, h.dp)
            .shadow(if (dragging) 14.dp else 6.dp, shape)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = (if (dragging) 0.24f else 0.18f) + (1f - (if (dragging) 0.24f else 0.18f)) * s),
                        Color.White.copy(alpha = (if (dragging) 0.12f else 0.08f) + (1f - (if (dragging) 0.12f else 0.08f)) * s),
                    ),
                ),
                shape,
            )
            .border(0.5.dp, Color.White.copy(alpha = 0.22f + 0.78f * s), shape),
    )
}
