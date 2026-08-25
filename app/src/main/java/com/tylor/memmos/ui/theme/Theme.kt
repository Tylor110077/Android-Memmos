package com.tylor.memmos.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 方向 A「琉璃 Glass」色彩令牌 · v3 多源化（docs/ui-design/design-language.md v3）
 * 设计语言：App 从「小红书专用」升级为多内容源（bilibili/抖音/微博…）通用剪藏——
 * 品牌色由单红改为「多源光谱」紫→粉→琥珀渐变（每种源一个色相，汇成一条光谱），
 * 红色只保留语义用途（删除/失败/危险/未授权）。玻璃质感用半透明白叠深底模拟。
 */
val AccentRed = Color(0xFFFF2E4D) // 语义红：删除/失败/危险
val AccentOrange = Color(0xFFFF9E5C) // 语义橙：仅警示（callout warning）
val AccentAmber = AccentOrange

/* 品牌色（官方源：Vechooool vision-engine-scan-console design.md）：
 * Accent #10B981 荧光绿；画布 #000000；Surface #FFFFFF；Secondary #EF4444=语义红。
 * 一个品牌色 + 三语义色（绿系成功/红危险/橙警示） */
val AccentGreen = Color(0xFF10B981)
val AccentGreenSoft = Color(0xFF6EE7B7) // 绿的浅调：链接/高亮文字

/** 品牌强调：同一绿的轻微双调渐变（视觉立体但仍是单色系） */
val AccentBrush = Brush.linearGradient(listOf(Color(0xFF10B981), Color(0xFF34D399)))

val Ink = Color(0xFF000000) // 屏幕底色（官方源 Canvas #000000）

/* 玻璃层：半透明白 + 高光描边 */
val GlassHi = Color(0x24FFFFFF) // 渐变上端白 .14
val GlassLo = Color(0x0DFFFFFF) // 渐变下端白 .05
val GlassFill = Color(0x0DFFFFFF) // 卡片填充白 .05
val GlassStroke = Color(0x29FFFFFF) // 描边白 .16
val GlassStrokeSoft = Color(0x17FFFFFF) // 更弱的分隔线白 .09

/* ── Vision Engine 玻璃发丝壳（gs-card 官方配方，来自模板 index.html）──
 * 外层：1px padding 露出渐变发丝（from-white/40 via-white/5 to-white/10 × opacity .7）；
 * 内层：bg-black/10 玻璃面（视觉上透出底层环境光，模糊由环境背景承担）。 */
val ShellHi = Color(0x47FFFFFF) // .28 高光角（左上）
val ShellMid = Color(0x0DFFFFFF) // .05 过渡
val ShellLo = Color(0x1AFFFFFF) // .10 低光角（右下）
val ShellGradient = Brush.linearGradient(listOf(ShellHi, ShellMid, ShellLo)) // 大卡 23px 发丝壳
val ShellGradientDim = Brush.linearGradient(listOf(Color(0x2EFFFFFF), Color(0x0DFFFFFF), Color(0x00000000))) // 小卡/列表行发丝壳
val VisionSurface = Color(0x1A000000) // 内层玻璃面 bg-black/10
val IslandFill = Color(0x8C000000) // 岛屿底栏黑 55%
val TextSoft = Color(0x80FFFFFF) // white/50（模板次级正文）
val TextGhost = Color(0x4DFFFFFF) // white/30（模板弱提示）
val RingWhite = Color(0x1FFFFFFF) // ring-1 white/10~12（头像/缩略图描边）

/* 文字三级 */
val TextHi = Color(0xFFF2F4F8)
val TextMid = Color(0xFF9BA1AE)
val TextFaint = Color(0xFF676D7A)

/* 主按钮（mckp.live 风格：白色胶囊黑字；次级=透明+白描边） */
val BtnPrimaryBg = Color(0xFFF4F4F5)
val BtnPrimaryText = Color(0xFF0A0A0C)
val BtnGhostBorder = Color(0x33FFFFFF)

/* 功能色 */
val Success = Color(0xFF34D399) // 成功：绿系浅调（与品牌绿同族，语义区分靠明度）
val ChipBg = Color(0x2E10B981) // 品牌绿 .18 容器
val ChipStroke = Color(0x6610B981) // 品牌绿 .40 描边
val ChipText = Color(0xFFA7F3D0) // 绿调浅文

// 占位封面：单色绿调深浅变体（官方源 Canvas 黑 + 绿 accent 家族）
val CoverGradients = listOf(
    listOf(Color(0xFF062A20), Color(0xFF0E5C43)),
    listOf(Color(0xFF051F18), Color(0xFF0B4634)),
    listOf(Color(0xFF073226), Color(0xFF126A4C)),
    listOf(Color(0xFF04160F), Color(0xFF093526)),
    listOf(Color(0xFF06271D), Color(0xFF0F5840)),
    listOf(Color(0xFF051F18), Color(0xFF0D4A37)),
)

/* ───────── 样式规范 v4（参考 Linear/Raycast/Notion/Apple HIG） ───────── */

/** 圆角体系（官方源家族：12/16/23/24/48/9999）：28 面板 / 23 大卡 / 16 卡 / 12 次级容器 / 999 胶囊 */
object Shapes {
    val Panel = RoundedCornerShape(28.dp)
    val Card = RoundedCornerShape(16.dp)
    val Tile = RoundedCornerShape(23.dp) // 官方主卡 radius 23px
    val Sub = RoundedCornerShape(12.dp)
    val Pill = RoundedCornerShape(999.dp)
}

/** 字阶（4 级正文弱化体系：标题/正文/说明/弱提示） */
object Type {
    val Title = 17.sp   // 页面/面板标题 Bold
    val Sub = 15.sp     // 卡片标题 SemiBold
    val Body = 13.sp    // 正文
    val Hint = 11.sp    // 说明
    val Dim = 10.sp     // 弱提示/元信息
}

/** 间距栅格（4dp） */
object Space {
    const val XS = 4
    const val S = 8
    const val M = 12
    const val L = 16
    const val XL = 20
    const val XXL = 24
}

@Composable
fun MemmosTheme(content: @Composable () -> Unit) {
    // 全程深色：本 App 只以悬浮层形态覆盖在宿主上，不跟随系统浅色（决策记录 §5）
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = AccentGreen,
            background = Ink,
            surface = Ink,
        ),
        content = content,
    )
}
