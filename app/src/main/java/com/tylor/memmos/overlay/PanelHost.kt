package com.tylor.memmos.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.tylor.memmos.ui.CapturePanel
import com.tylor.memmos.ui.SettingsSheet
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * 面板悬浮窗内容：变暗层（点按收回）+ 功能面板（从滑块侧滑入）+ 设置抽屉。
 * 与旧应用内演示的区别：没有宿主场景背景——面板下面就是用户正在使用的真实 App；
 * 窗口全屏，面板本体占 86%，右侧 14% 为透明捕获层（点击关闭/左滑关闭），
 * 模态语义：点空白先关面板，关掉后即可操作宿主 App。
 *
 * 收起易用性（用户反馈手小）：面板与右缘捕获层都响应横向拖拽，拖回 ~28% 屏宽
 * （至少 96dp）或轻甩即关闭；速度由 draggable 真实上报（旧 detectHorizontalDragGestures
 * 永远 vel=0，速度关永远不生效，只能拖过半屏）。
 */
@Composable
fun PanelHost(
    model: OverlayModel,
    panelOnLeft: Boolean,
    onClose: () -> Unit,
    onCaptureCurrent: () -> Unit,
    onOpenNote: (String) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onRelease: (Float) -> Unit,
) {
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val dim by animateFloatAsState(if (appeared) 1f else 0f, tween(220), label = "dim")
    // 面板外轮廓：朝内容侧两角 28dp 大圆角，贴屏幕侧直角（设计语言 §1）
    val panelShape = if (panelOnLeft) RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp)
    else RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp)

    Box(Modifier.fillMaxSize()) {
        // 变暗层必须与面板同轮廓裁剪：否则弧线外的角会被罩成方形灰块（用户反馈）
        // clip 不影响命中区域：弧线外点击仍走 onClose
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.86f)
                .align(if (panelOnLeft) Alignment.CenterStart else Alignment.CenterEnd)
                .clip(panelShape)
                .background(Color(0xFF050610).copy(alpha = 0.30f * dim))
                .clickable(onClick = onClose),
        )
        AnimatedVisibility(
            visible = appeared,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(160)),
            modifier = Modifier.align(if (panelOnLeft) Alignment.CenterStart else Alignment.CenterEnd),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.86f)
                    .align(if (panelOnLeft) Alignment.CenterStart else Alignment.CenterEnd)
                    .clip(panelShape)
                    .swipeDrag(onDragStart, onDrag, onRelease),
            ) {
                CapturePanel(
                    onClose = onClose,
                    onOpenSettings = { model.sheetOpen.value = true },
                    onCaptureCurrent = onCaptureCurrent,
                    onOpenNote = onOpenNote,
                )
            }
        }
        // 面板外侧透明捕获层：点击关闭 / 跟手拖拽关闭（本层不绘制颜色，桌面原样透出）。
        // 对齐面板对侧：浮条在左→空白在右（CenterEnd）；浮条在右→空白在左（CenterStart）。
        // 只允许存在一层：叠加多个 Box 时最上层会把下层拖拽吞掉（Compose 命中测试）
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.14f)
                .align(if (panelOnLeft) Alignment.CenterEnd else Alignment.CenterStart)
                .clickable(onClick = onClose)
                .swipeDrag(onDragStart, onDrag, onRelease),
        )

        // 设置抽屉（用户要求：与面板同宽不超浮窗、下滑关闭）；面板保持打开时可见。
        // 单一 Animatable 自管理滑入/跟手/回弹/滑出——原来「AnimatedVisibility 动画 +
        // 手拖 offset 双动画叠加」，松手未过阈值时 sheetDrag 瞬间归零（硬跳回）、关闭时与
        // exit 动画叠加，表现为「抽动」。
        val sheetAnim = remember { Animatable(0f) }
        val density = LocalDensity.current
        val sheetHpx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
        val scope = rememberCoroutineScope()
        LaunchedEffect(model.sheetOpen.value) {
            if (model.sheetOpen.value) {
                if (sheetAnim.value <= 0f) sheetAnim.snapTo(sheetHpx) // 从屏下进入
                sheetAnim.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
            } else {
                sheetAnim.animateTo(sheetHpx, tween(240)) // 整段滑出（同一值，与 offset 无冲突）
            }
        }
        if (model.sheetOpen.value || sheetAnim.value < sheetHpx) {
            val closePx = with(density) { 56.dp.toPx() }
            Box(
                Modifier
                    .fillMaxWidth(0.86f)
                    .offset { IntOffset(0, sheetAnim.value.roundToInt()) }
                    .draggable(
                        state = rememberDraggableState { d ->
                            scope.launch {
                                sheetAnim.stop()
                                sheetAnim.snapTo((sheetAnim.value + d).coerceAtLeast(0f))
                            }
                        },
                        orientation = Orientation.Vertical,
                        onDragStarted = { scope.launch { sheetAnim.stop() } },
                        onDragStopped = { v ->
                            // 短滑即收：56dp 位移或 900px/s 甩动；回弹走同一 Animatable，平滑无抽动
                            if (sheetAnim.value > closePx || v > 900f) model.sheetOpen.value = false
                            else scope.launch {
                                sheetAnim.animateTo(0f, tween(220, easing = FastOutSlowInEasing))
                            }
                        },
                    ),
            ) {
                SettingsSheet(
                    edge = model.edge.value,
                    frac = model.frac.value,
                    opacity = model.opacity.value,
                    barWidth = model.barWidth.value,
                    barLength = model.barLength.value,
                    onEdgeChange = model::setEdge,
                    onFracChange = model::setFrac,
                    onOpacityChange = model::setOpacity,
                    onWidthChange = model::setBarWidth,
                    onLengthChange = model::setBarLength,
                    onDismiss = { model.sheetOpen.value = false },
                )
            }
        }
    }
}

/**
 * 跟手水平拖拽：增量上报窗口层（FloatingService 负责窗口位移与松手判定）。
 * draggable 的 onDragStopped 提供真实甩动速度（px/s），换算 px/ms 后上报——
 * 旧的 detectHorizontalDragGestures 拿不到速度，松手只能纯按位移判定。
 */
@Composable
private fun Modifier.swipeDrag(
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onRelease: (Float) -> Unit,
): Modifier = draggable(
    state = rememberDraggableState { delta -> onDrag(delta) },
    orientation = Orientation.Horizontal,
    onDragStarted = { onDragStart() },
    onDragStopped = { velocity -> onRelease(velocity / 1000f) },
)
