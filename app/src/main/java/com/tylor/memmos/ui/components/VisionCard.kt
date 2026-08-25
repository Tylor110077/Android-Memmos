package com.tylor.memmos.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tylor.memmos.R
import com.tylor.memmos.ui.theme.ShellGradient
import com.tylor.memmos.ui.theme.ShellGradientDim
import com.tylor.memmos.ui.theme.VisionSurface

/**
 * Vision 玻璃卡：复刻模板 gs-card 的「渐变发丝壳」配方——
 * 外层 1px 渐变壳（白 .28→.05→.10 对角），内层 bg-black/10 玻璃面（半径 inner），
 * 壳只露 1px，像一圈发丝光边；实际玻璃透感由内层半透明 + 底下环境背景承担。
 * contentModifier 用于接点击/长按（如 combinedClickable），涟漪被内层圆角裁剪。
 */
@Composable
fun VisionCard(
    modifier: Modifier = Modifier,
    radius: Dp = 16.dp,
    shell: Brush = ShellGradient,
    surface: Color = VisionSurface,
    contentModifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val inner = RoundedCornerShape(radius)
    Box(
        modifier
            .clip(RoundedCornerShape(radius + 1.dp))
            .background(shell)
            .padding(1.dp),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(inner)
                .background(surface)
                .then(contentModifier),
            content = content,
        )
    }
}

/** 列表行级玻璃卡：弱一档的发丝壳（模板 Recent sessions 行 gs-card 变体） */
@Composable
fun VisionRowCard(
    modifier: Modifier = Modifier,
    radius: Dp = 16.dp,
    contentModifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) = VisionCard(modifier, radius, ShellGradientDim, VisionSurface, contentModifier, content)

/** 玻璃圆形小按钮（模板 header 的 w-9 h-9：bg-white/10 + border-white/20 + blur） */
@Composable
fun GlassCircleButton(
    size: Dp = 36.dp,
    content: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0x1AFFFFFF))
            .border(1.dp, Color(0x33FFFFFF), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/**
 * 环境背景（模板 Ambient Background）：
 * 宽幅光斑图低透明铺底（原 mix-blend-screen，用低 alpha 等效）+
 * 三段纵向渐变罩 from-black/50 via-black/10 to-black/80。
 * 全 App 主面/悬浮面板共用同一片环境光，构成「一块玻璃」的视觉连续性。
 */
@Composable
fun AmbientBackdrop(
    modifier: Modifier = Modifier,
    alpha: Float = 0.42f,
) {
    Box(modifier) {
        Image(
            painter = painterResource(R.drawable.ambient_glow),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = 1.1f
                    scaleY = 1.1f
                    this.alpha = alpha
                },
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0x80000000), // from-black/50
                            Color(0x1A000000), // via-black/10
                            Color(0xCC000000), // to-black/80
                        ),
                    ),
                ),
        )
    }
}
