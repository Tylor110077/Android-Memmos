package com.tylor.memmos.overlay

import androidx.compose.runtime.mutableStateOf
import com.tylor.memmos.util.OverlayPrefs
import com.tylor.memmos.ui.TabColor
import com.tylor.memmos.ui.TabEdge
import com.tylor.memmos.util.AppPrefs

/**
 * 悬浮层共享状态：服务（拖拽/停靠计算）与 Compose 内容（视觉绑定、设置抽屉）双向读写。
 */
class OverlayModel {
    var edge = mutableStateOf(TabEdge.LEFT)
    var frac = mutableStateOf(0.39f) // 默认贴边位置 39%（用户要求）
    var opacity = mutableStateOf(0.92f)
    /** 浮条宽度/长度（dp，独立可调，用户要求「同时设定宽度和长度」） */
    var barWidth = mutableStateOf(5f) // 默认 5，最低可调到 4（用户要求）
    var barLength = mutableStateOf(100f) // 默认 100（用户要求）
    var dragging = mutableStateOf(false)
    var sheetOpen = mutableStateOf(false)
    /** 滑块本体配色（纯色：绿/白/深灰/紫） */
    var barColor = mutableStateOf(TabColor.WHITE) // 默认白色（用户要求）

    /** 服务注入：模型任何变化（几何/颜色）→ 重算窗口停靠 + 持久化（下次启动生效，用户要求） */
    var onChange: (() -> Unit)? = null

    fun setEdge(v: TabEdge) { edge.value = v; onChange?.invoke() }
    fun setFrac(v: Float) { frac.value = v.coerceIn(0.05f, 0.95f); onChange?.invoke() }
    fun setBarWidth(v: Float) { barWidth.value = v.coerceIn(4f, 36f); onChange?.invoke() }
    fun setBarLength(v: Float) { barLength.value = v.coerceIn(48f, 150f); onChange?.invoke() }
    fun setOpacity(v: Float) { opacity.value = v; onChange?.invoke() }
    fun setColor(v: TabColor) { barColor.value = v; onChange?.invoke() }

    /** 从偏好恢复（服务 onCreate 调用；未设置过的字段保持默认） */
    fun loadFrom(prefs: OverlayPrefs) {
        barWidth.value = prefs.width
        barLength.value = prefs.length
        opacity.value = prefs.opacity
        barColor.value = runCatching { TabColor.valueOf(prefs.color) }.getOrDefault(TabColor.WHITE)
        edge.value = runCatching { TabEdge.valueOf(prefs.edge) }.getOrDefault(TabEdge.LEFT)
        frac.value = prefs.frac.coerceIn(0.05f, 0.95f)
    }
}
