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
fun IconClose(size: Dp, tint: Color, modifier: Modifier = Modifier) = GlyphIcon(size, tint, modifier) { st ->
    line(6f, 6f, 18f, 18f, st, tint)
    line(18f, 6f, 6f, 18f, st, tint)
}

@Composable
fun IconCheck(size: Dp, tint: Color, modifier: Modifier = Modifier) = GlyphIcon(size, tint, modifier, strokeWidth = 2.4f) { st ->
    val p = Path().apply {
        moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 7f)
    }
    drawPath(p, tint, style = st)
}

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

/** 亮度：圆点 + 8 条射线（视频手势提示用，不用 emoji） */
@Composable
fun IconSun(size: Dp, tint: Color, modifier: Modifier = Modifier) = GlyphIcon(size, tint, modifier) { st ->
    drawCircle(tint, radius = 3.2f, center = Offset(12f, 12f))
    for (a in intArrayOf(0, 45, 90, 135, 180, 225, 270, 315)) {
        val r = a * kotlin.math.PI / 180.0
        val x1 = 12f + (6.4f * kotlin.math.cos(r)).toFloat()
        val y1 = 12f + (6.4f * kotlin.math.sin(r)).toFloat()
        val x2 = 12f + (9.2f * kotlin.math.cos(r)).toFloat()
        val y2 = 12f + (9.2f * kotlin.math.sin(r)).toFloat()
        line(x1, y1, x2, y2, st, tint)
    }
}

/** 音量：喇叭（填充）+ 两道声波（视频手势提示用，不用 emoji） */
@Composable
fun IconVolumeSpeaker(size: Dp, tint: Color, modifier: Modifier = Modifier) = GlyphIcon(size, tint, modifier) { st ->
    val p = Path().apply {
        moveTo(4f, 9.4f); lineTo(8f, 9.4f); lineTo(13f, 4.5f)
        lineTo(13f, 19.5f); lineTo(8f, 14.6f); lineTo(4f, 14.6f); close()
    }
    drawPath(p, tint)
    drawArc(
        tint, startAngle = -48f, sweepAngle = 96f, useCenter = false,
        topLeft = Offset(12.4f, 6.8f), size = Size(9.4f, 10.4f), style = st,
    )
    drawArc(
        tint, startAngle = -52f, sweepAngle = 104f, useCenter = false,
        topLeft = Offset(13f, 4.2f), size = Size(15.6f, 15.6f), style = st,
    )
}

/** 设置齿轮：6 齿细弧 + 中环 + 中孔，占满 24 视口（与靶心/四宫格同视觉重量） */
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
fun IconHeart(size: Dp, tint: Color, modifier: Modifier = Modifier) = GlyphIcon(size, tint, modifier, strokeWidth = 1.8f) { st ->
    val p = Path().apply {
        moveTo(12f, 20.5f)
        cubicTo(6f, 15.5f, 3f, 12.3f, 3f, 8.8f)
        cubicTo(3f, 5.9f, 5.2f, 3.8f, 7.8f, 3.8f)
        cubicTo(9.6f, 3.8f, 11.1f, 4.8f, 12f, 6.3f)
        cubicTo(12.9f, 4.8f, 14.4f, 3.8f, 16.2f, 3.8f)
        cubicTo(18.8f, 3.8f, 21f, 5.9f, 21f, 8.8f)
        cubicTo(21f, 12.3f, 18f, 15.5f, 12f, 20.5f)
        close()
    }
    drawPath(p, tint, style = st)
}

@Composable
fun IconBookmark(size: Dp, tint: Color, modifier: Modifier = Modifier) = GlyphIcon(size, tint, modifier, strokeWidth = 1.8f) { st ->
    val p = Path().apply {
        moveTo(6f, 3f); lineTo(18f, 3f); lineTo(18f, 21f); lineTo(12f, 17f); lineTo(6f, 21f); close()
    }
    drawPath(p, tint, style = st)
}

@Composable
fun IconBubble(size: Dp, tint: Color, modifier: Modifier = Modifier) = GlyphIcon(size, tint, modifier, strokeWidth = 1.8f) { st ->
    drawRoundRect(tint, topLeft = Offset(3f, 3f), size = Size(18f, 14f), cornerRadius = CornerRadius(4f, 4f), style = st)
    line(8f, 17f, 8f, 21.5f, st, tint)
    line(8f, 21.5f, 13f, 21.5f, st, tint)
}

@Composable
fun IconMic(size: Dp, tint: Color, modifier: Modifier = Modifier) = GlyphIcon(size, tint, modifier) { st ->
    drawRoundRect(tint, topLeft = Offset(9f, 2.5f), size = Size(6f, 10.5f), cornerRadius = CornerRadius(3f, 3f), style = st)
    drawArc(tint, startAngle = 0f, sweepAngle = 180f, useCenter = false,
        topLeft = Offset(5f, 6.5f), size = Size(14f, 14f), style = st)
    line(12f, 20.5f, 12f, 22.5f, st, tint)
}

@Composable
fun IconSearch(size: Dp, tint: Color, modifier: Modifier = Modifier) = GlyphIcon(size, tint, modifier) { st ->
    drawCircle(tint, radius = 7f, center = Offset(11f, 11f), style = st)
    line(16.5f, 16.5f, 21f, 21f, st, tint)
}

@Composable
fun IconMonitor(size: Dp, tint: Color, modifier: Modifier = Modifier) = GlyphIcon(size, tint, modifier) { st ->
    drawRoundRect(tint, topLeft = Offset(2.5f, 4f), size = Size(19f, 13f), cornerRadius = CornerRadius(2f, 2f), style = st)
    line(12f, 17f, 12f, 21f, st, tint)
    line(8f, 21f, 16f, 21f, st, tint)
}

@Composable
fun IconCircleCheck(size: Dp, tint: Color, modifier: Modifier = Modifier) = GlyphIcon(size, tint, modifier, strokeWidth = 1.8f) { st ->
    drawCircle(tint, radius = 9f, center = Offset(12f, 12f), style = st)
    val p = Path().apply {
        moveTo(8f, 12.5f); lineTo(11f, 15.5f); lineTo(16.5f, 9.5f)
    }
    drawPath(p, tint, style = st)
}

/**
 * 捕捉/扫描（岛屿底栏「捕捉」项）：圆形靶心——外圆 + 四向短刻度 + 实心中心点。
 * 与「剪藏库」的四宫格方块在形状上明确区分（圆 vs 方）。
 */
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
