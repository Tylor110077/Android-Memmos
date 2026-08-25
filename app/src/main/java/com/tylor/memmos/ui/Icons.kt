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

/** 退出全屏：四角向中心压缩箭头（点退回内嵌） */
@Composable
fun IconFullscreenExit(size: Dp, tint: Color, modifier: Modifier = Modifier) = GlyphIcon(size, tint, modifier) { st ->
    line(8f, 8f, 12f, 12f, st, tint); line(12f, 12f, 8f, 12f, st, tint)
    line(16f, 8f, 12f, 12f, st, tint); line(12f, 12f, 16f, 12f, st, tint)
    line(8f, 16f, 12f, 12f, st, tint); line(12f, 12f, 8f, 13.2f, st, tint)
    line(16f, 16f, 12f, 12f, st, tint); line(12f, 12f, 16f, 13.2f, st, tint)
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

/** 设置齿轮（简洁版）：6 齿细弧 + 细环 + 小孔，线条更轻 */
@Composable
fun IconGear(size: Dp, tint: Color, modifier: Modifier = Modifier) = GlyphIcon(size, tint, modifier, strokeWidth = 1.5f) { _ ->
    val tooth = Stroke(width = 2.6f, cap = StrokeCap.Butt)
    for (a in 0 until 360 step 60) {
        drawArc(
            tint, startAngle = (a - 9f), sweepAngle = 18f, useCenter = false,
            topLeft = Offset(4.8f, 4.8f), size = Size(14.4f, 14.4f), style = tooth,
        )
    }
    drawCircle(tint, radius = 5f, center = Offset(12f, 12f), style = Stroke(width = 2f, cap = StrokeCap.Butt))
    drawCircle(tint, radius = 2f, center = Offset(12f, 12f), style = Stroke(width = 1.5f, cap = StrokeCap.Butt))
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

/** 捕捉/扫描：四角括号 + 中心环（岛屿底栏「捕捉」项） */
@Composable
fun IconScan(size: Dp, tint: Color, modifier: Modifier = Modifier) = GlyphIcon(size, tint, modifier) { st ->
    line(4f, 9f, 4f, 4f, st, tint); line(4f, 4f, 9f, 4f, st, tint)
    line(15f, 4f, 20f, 4f, st, tint); line(20f, 4f, 20f, 9f, st, tint)
    line(20f, 15f, 20f, 20f, st, tint); line(20f, 20f, 15f, 20f, st, tint)
    line(9f, 20f, 4f, 20f, st, tint); line(4f, 20f, 4f, 15f, st, tint)
    drawCircle(tint, radius = 3f, center = Offset(12f, 12f), style = st)
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
