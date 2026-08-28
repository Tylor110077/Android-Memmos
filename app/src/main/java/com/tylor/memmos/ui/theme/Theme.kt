package com.tylor.memmos.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
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

// ── 主题强调色（用户 2026-08-28：主题色可选，新增 Obsidian 紫，整个主界面跟随）──
// 品牌色族令牌（AccentGreen/AccentBrush/Success/Chip 系列/CoverGradients）不再是指针常量，
// 而是读 themeAccent 状态的 @Composable getter（MaterialTheme.colors 同款模式），
// 换主题=换 themeAccent 状态，全 App 立即换装。语义红/橙不随主题。
enum class ThemeAccent(val label: String) {
    GREEN("绿"), PURPLE("紫");

    val primary: Color get() = if (this == GREEN) Color(0xFF10B981) else Color(0xFF7C3AED)
    val soft: Color get() = if (this == GREEN) Color(0xFF6EE7B7) else Color(0xFFA78BFA) // 链接/高亮浅调
    val success: Color get() = if (this == GREEN) Color(0xFF34D399) else Color(0xFFC4B5FD)
    val chipBg: Color get() = if (this == GREEN) Color(0x2E10B981) else Color(0x2E7C3AED)
    val chipStroke: Color get() = if (this == GREEN) Color(0x6610B981) else Color(0x667C3AED)
    val chipText: Color get() = if (this == GREEN) Color(0xFFA7F3D0) else Color(0xFFDDD6FE)
    val brush: List<Color> get() = if (this == GREEN) listOf(Color(0xFF10B981), Color(0xFF34D399))
    else listOf(Color(0xFF7C3AED), Color(0xFFA78BFA))
    // 占位封面：品牌色系深浅变体
    val covers: List<List<Color>> get() = if (this == GREEN) listOf(
        listOf(Color(0xFF062A20), Color(0xFF0E5C43)),
        listOf(Color(0xFF051F18), Color(0xFF0B4634)),
        listOf(Color(0xFF073226), Color(0xFF126A4C)),
        listOf(Color(0xFF04160F), Color(0xFF093526)),
        listOf(Color(0xFF06271D), Color(0xFF0F5840)),
        listOf(Color(0xFF051F18), Color(0xFF0D4A37)),
    ) else listOf(
        listOf(Color(0xFF1A1030), Color(0xFF46297F)),
        listOf(Color(0xFF140C24), Color(0xFF351F61)),
        listOf(Color(0xFF1E1338), Color(0xFF54339A)),
        listOf(Color(0xFF0F091C), Color(0xFF2A1852)),
        listOf(Color(0xFF171029), Color(0xFF3F2774)),
        listOf(Color(0xFF140C24), Color(0xFF372166)),
    )
}

/** 当前主题（进程级状态）：设置页切换即改。令牌 getter 直接读这个 State——
 *  任何 Compose 组合（主界面/详情/悬浮层）读到它都会在其变化时自动重组合 */
val themeAccent: MutableState<ThemeAccent> = mutableStateOf(ThemeAccent.GREEN)

/** 存取（AppPrefs 里只存字符串，这里做枚举桥）；各 Activity onCreate 时 load 一次 */
fun loadThemeAccent(ctx: android.content.Context) {
    themeAccent.value =
        if (com.tylor.memmos.util.AppPrefs.themeColor(ctx) == "purple") ThemeAccent.PURPLE else ThemeAccent.GREEN
}

fun setThemeAccent(ctx: android.content.Context, a: ThemeAccent) {
    com.tylor.memmos.util.AppPrefs.setThemeColor(ctx, if (a == ThemeAccent.PURPLE) "purple" else "green")
    themeAccent.value = a
}

/* 品牌色令牌：官方源 vision-engine #10B981 荧光绿；现随主题切换（紫=Obsidian #7C3AED） */
val AccentGreen: Color @Composable get() = themeAccent.value.primary
val AccentGreenSoft: Color @Composable get() = themeAccent.value.soft // 浅调：链接/高亮文字

/** 品牌强调：同一色相的轻微双调渐变（视觉立体但仍是单色系） */
val AccentBrush: Brush @Composable get() = Brush.linearGradient(themeAccent.value.brush)

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

/* 功能色：随主题（成功/Chip 容器与描边/Chip 浅文） */
val Success: Color @Composable get() = themeAccent.value.success
val ChipBg: Color @Composable get() = themeAccent.value.chipBg
val ChipStroke: Color @Composable get() = themeAccent.value.chipStroke
val ChipText: Color @Composable get() = themeAccent.value.chipText

// 占位封面：单色深浅变体（随主题：绿系/Obsidian 紫系）
val CoverGradients: List<List<Color>> @Composable get() = themeAccent.value.covers

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
            primary = themeAccent.value.primary,
            background = Ink,
            surface = Ink,
        ),
        content = content,
    )
}
