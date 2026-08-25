package com.tylor.memmos.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 极简线性图标：统一在 24×24 视口内书写坐标，Canvas 自动缩放。
 * 不引入 material-icons 依赖，P1 只需要这十来个字形，自绘可控且体积零成本。
 */
@Composable
fun GlyphIcon(
    size: Dp,
    tint: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Float = 2f,
    draw24: DrawScope.(Stroke) -> Unit,
) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension / 24f
        withTransform({ scale(s, s, pivot = Offset.Zero) }) {
            draw24(
                Stroke(
                    width = strokeWidth * s,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }
    }
}

internal fun DrawScope.line(x1: Float, y1: Float, x2: Float, y2: Float, stroke: Stroke, color: Color) =
    drawLine(color, Offset(x1, y1), Offset(x2, y2), strokeWidth = stroke.width, cap = stroke.cap)





@Composable
fun IconPlayFilled(size: Dp, tint: Color, modifier: Modifier = Modifier) = GlyphIcon(size, tint, modifier) { _ ->
    val p = Path().apply {
        moveTo(8f, 5f); lineTo(19f, 12f); lineTo(8f, 19f); close()
    }
    drawPath(p, tint)
}

/** 暂停：两条竖杠（视频播放器控制条用，不使用 emoji 字形） */
@Composable
fun IconPause(size: Dp, tint: Color, modifier: Modifier = Modifier) = GlyphIcon(size, tint, modifier) { _ ->
    drawRect(tint, topLeft = Offset(7.2f, 5f), size = Size(3f, 14f))
    drawRect(tint, topLeft = Offset(13.8f, 5f), size = Size(3f, 14f))
}

/** 全屏：四角外扩括号（点全屏） */
@Composable
fun IconFullscreen(size: Dp, tint: Color, modifier: Modifier = Modifier) = GlyphIcon(size, tint, modifier) { st ->
    line(6f, 12f, 6f, 6f, st, tint); line(6f, 6f, 12f, 6f, st, tint)
    line(18f, 12f, 18f, 6f, st, tint); line(18f, 6f, 12f, 6f, st, tint)
    line(6f, 12f, 6f, 18f, st, tint); line(6f, 18f, 12f, 18f, st, tint)
    line(18f, 12f, 18f, 18f, st, tint); line(18f, 18f, 12f, 18f, st, tint)
}

/** 退出全屏「缩小」：四角 L 括号 + 四条从角指向中心的实心箭头（小尺寸下清晰可辨） */
@Composable
fun IconFullscreenExit(size: Dp, tint: Color, modifier: Modifier = Modifier) = GlyphIcon(size, tint, modifier) { _ ->
    val st = Stroke(width = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    // 四角 L 括号
    line(4.6f, 10.2f, 4.6f, 4.6f, st, tint); line(4.6f, 4.6f, 10.2f, 4.6f, st, tint)
    line(13.8f, 4.6f, 19.4f, 4.6f, st, tint); line(19.4f, 4.6f, 19.4f, 10.2f, st, tint)
    line(19.4f, 13.8f, 19.4f, 19.4f, st, tint); line(19.4f, 19.4f, 13.8f, 19.4f, st, tint)
    line(10.2f, 19.4f, 4.6f, 19.4f, st, tint); line(4.6f, 19.4f, 4.6f, 13.8f, st, tint)
    // 中心四条向心实心箭头（箭头头用填充三角，小尺寸更醒目）
    drawLine(tint, Offset(10.4f, 10.4f), Offset(6.8f, 6.8f), strokeWidth = st.width, cap = StrokeCap.Round)
    val t1 = Path().apply { moveTo(6.8f, 6.8f); lineTo(11.2f, 7.1f); lineTo(7.1f, 11.2f); close() }
    drawPath(t1, tint)
    drawLine(tint, Offset(13.6f, 10.4f), Offset(17.2f, 6.8f), strokeWidth = st.width, cap = StrokeCap.Round)
    val t2 = Path().apply { moveTo(17.2f, 6.8f); lineTo(12.8f, 7.1f); lineTo(16.9f, 11.2f); close() }
    drawPath(t2, tint)
    drawLine(tint, Offset(10.4f, 13.6f), Offset(6.8f, 17.2f), strokeWidth = st.width, cap = StrokeCap.Round)
    val t3 = Path().apply { moveTo(6.8f, 17.2f); lineTo(11.2f, 16.9f); lineTo(7.1f, 12.8f); close() }
    drawPath(t3, tint)
    drawLine(tint, Offset(13.6f, 13.6f), Offset(17.2f, 17.2f), strokeWidth = st.width, cap = StrokeCap.Round)
    val t4 = Path().apply { moveTo(17.2f, 17.2f); lineTo(12.8f, 16.9f); lineTo(16.9f, 12.8f); close() }
    drawPath(t4, tint)
}



@Composable
fun IconGear(size: Dp, tint: Color, modifier: Modifier = Modifier) = GlyphIcon(size, tint, modifier, strokeWidth = 1.6f) { _ ->
    val tooth = Stroke(width = 3.2f, cap = StrokeCap.Butt)
    for (a in 0 until 360 step 60) {
        drawArc(
            tint, startAngle = (a - 11f), sweepAngle = 22f, useCenter = false,
            topLeft = Offset(2.6f, 2.6f), size = Size(18.8f, 18.8f), style = tooth,
        )
    }
    drawCircle(tint, radius = 6.4f, center = Offset(12f, 12f), style = Stroke(width = 2.2f, cap = StrokeCap.Butt))
    drawCircle(tint, radius = 2.7f, center = Offset(12f, 12f), style = Stroke(width = 1.7f, cap = StrokeCap.Butt))
}














@Composable
fun IconScan(size: Dp, tint: Color, modifier: Modifier = Modifier) = GlyphIcon(size, tint, modifier) { st ->
    drawCircle(tint, radius = 6.6f, center = Offset(12f, 12f), style = st)
    drawCircle(tint, radius = 2.1f, center = Offset(12f, 12f))
    line(12f, 2.0f, 12f, 4.6f, st, tint)
    line(12f, 19.4f, 12f, 22.0f, st, tint)
    line(2.0f, 12f, 4.6f, 12f, st, tint)
    line(19.4f, 12f, 22.0f, 12f, st, tint)
}

/** 剪藏库：四宫格圆角方块（岛屿底栏「剪藏库」项） */
@Composable
fun IconGrid(size: Dp, tint: Color, modifier: Modifier = Modifier) = GlyphIcon(size, tint, modifier) { st ->
    val cr = CornerRadius(1.8f, 1.8f)
    val s1 = Size(7.2f, 7.2f)
    drawRoundRect(tint, topLeft = Offset(3.2f, 3.2f), size = s1, cornerRadius = cr, style = st)
    drawRoundRect(tint, topLeft = Offset(13.6f, 3.2f), size = s1, cornerRadius = cr, style = st)
    drawRoundRect(tint, topLeft = Offset(3.2f, 13.6f), size = s1, cornerRadius = cr, style = st)
    drawRoundRect(tint, topLeft = Offset(13.6f, 13.6f), size = s1, cornerRadius = cr, style = st)
}
