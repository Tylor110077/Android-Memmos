package com.tylor.memmos.overlay

import androidx.compose.runtime.mutableStateOf
import com.tylor.memmos.ui.TabEdge

/**
 * 悬浮层共享状态：服务（拖拽/停靠计算）与 Compose 内容（视觉绑定、设置抽屉）双向读写。
 */
class OverlayModel {
    var edge = mutableStateOf(TabEdge.LEFT)
    var frac = mutableStateOf(0.62f)
    var opacity = mutableStateOf(0.92f)
    /** 浮条宽度/长度（dp，独立可调，用户要求「同时设定宽度和长度」） */
    var barWidth = mutableStateOf(20f)
    var barLength = mutableStateOf(88f)
    var dragging = mutableStateOf(false)
    var sheetOpen = mutableStateOf(false)

    /** 服务注入：模型变化后重算滑块窗口停靠位置（透明度不影响位置，无需回调） */
    var onChange: (() -> Unit)? = null

    fun setEdge(v: TabEdge) { edge.value = v; onChange?.invoke() }
    fun setFrac(v: Float) { frac.value = v.coerceIn(0.05f, 0.95f); onChange?.invoke() }
    fun setBarWidth(v: Float) { barWidth.value = v.coerceIn(5f, 36f); onChange?.invoke() }
    fun setBarLength(v: Float) { barLength.value = v.coerceIn(48f, 150f); onChange?.invoke() }
    fun setOpacity(v: Float) { opacity.value = v }
}
