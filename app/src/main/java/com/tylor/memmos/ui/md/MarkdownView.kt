package com.tylor.memmos.ui.md

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tylor.memmos.ui.theme.AccentOrange
import com.tylor.memmos.ui.theme.AccentGreenSoft
import com.tylor.memmos.ui.theme.ThemeAccent
import com.tylor.memmos.ui.theme.themeAccent
import com.tylor.memmos.ui.theme.AccentRed
import com.tylor.memmos.ui.theme.ChipText
import com.tylor.memmos.ui.theme.GlassStrokeSoft
import com.tylor.memmos.ui.theme.Success
import com.tylor.memmos.ui.theme.TextFaint
import com.tylor.memmos.ui.theme.TextHi
import com.tylor.memmos.ui.theme.TextMid
import com.tylor.memmos.ui.viewer.FileViewerActivity
import java.io.File

/**
 * Obsidian 兼容的 md 轻量渲染器（手写，与仓库其他模块风格一致）。
 * 覆盖 Obsidian 实际会处理的语法：标题/粗斜体/删除线/==高亮==/内联代码/代码围栏/
 * 任务清单(- [ ])/嵌套列表/引用/提示框(> [!note])/GFM 表格/水平线/图片与 ![[嵌入]]/
 * [[双链]]/[]() 链接/自动 URL/#标签。数学公式 Obsidian 用 KaTeX 渲染，本端无 LaTeX 引擎，
 * 以等宽字体呈现避免「原样裸文本」；链接可点击跳浏览器，[[双链]] 可打开本地 vault 文件。
 */

// ────────────────────────── 解析层（纯字符串，无 Android 依赖） ──────────────────────────

private object MdParse {

    sealed interface Block {
        data class Heading(val level: Int, val text: String) : Block
        data class Paragraph(val text: String) : Block
        data class Quote(val lines: List<String>) : Block
        data class Fence(val lang: String?, val code: String) : Block
        data class ListItem(
            val level: Int, val ordered: Boolean, val marker: String,
            val task: Boolean?, val text: String,
        ) : Block
        data class ListBlock(val items: List<ListItem>) : Block
        data class Table(val rows: List<List<String>>) : Block
        data class Image(val ref: String, val alt: String, val embed: Boolean) : Block
        object Rule : Block
    }

    private val FRONTMATTER = Regex("^---\\r?\\n[\\s\\S]*?\\r?\\n---\\r?\\n?")
    private val HEADING = Regex("^(#{1,6})\\s+(.*)$")
    private val HR = Regex("^ {0,3}(?:-{3,}|\\*{3,}|_{3,})\\s*$")
    private val FENCE_OPEN = Regex("^\\s*(```+|~~~+)(.*)$")
    private val FENCE_CLOSE = Regex("^\\s*(```+|~~~+)\\s*$")
    private val QUOTE = Regex("^\\s*>\\s?(.*)$")
    private val LIST_ITEM = Regex("^(\\s*)([-*+]|\\d+[.)])\\s+(.*)$")
    private val TASK = Regex("^\\[( |x|X)]\\s+(.*)$")
    private val TABLE_SEP = Regex("^\\s*\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)*\\|?\\s*$")
    private val IMG_LINE = Regex("^!\\[[^\\]]*]\\(([^)]+)\\)$")
    private val EMBED_LINE = Regex("^!\\[\\[([^\\]]+)]]$")
    val CALLOUT = Regex("^\\[!([A-Za-z]+)](?:\\s+(.*))?$")

    fun parse(md: String): List<Block> {
        val body = md.replace(FRONTMATTER, "").trimEnd()
        if (body.isBlank()) return emptyList()
        val lines = body.split("\n")
        val out = mutableListOf<Block>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            when {
                line.isBlank() -> i++

                // 代码围栏：``` / ~~~ 到结束符，内容原样输出
                FENCE_OPEN.matches(line) -> {
                    val lang = FENCE_OPEN.find(line)!!.groupValues[2].trim().ifBlank { null }
                    val sb = StringBuilder()
                    i++
                    while (i < lines.size && !FENCE_CLOSE.matches(lines[i])) {
                        sb.appendLine(lines[i]); i++
                    }
                    i++ // 跳过结束围栏（若存在）
                    out += Block.Fence(lang, sb.toString().trimEnd())
                }

                HR.matches(line.trim()) -> { out += Block.Rule; i++ }

                HEADING.matches(line) -> {
                    val m = HEADING.find(line)!!
                    // 去掉标题末尾的闭合 #（### 标题 ###）
                    out += Block.Heading(m.groupValues[1].length, m.groupValues[2].replace(Regex("\\s+#+\\s*$"), ""))
                    i++
                }

                // GFM 表格：表头行 + |---| 分隔行，之后连续 | 行都是数据行
                isTableStart(lines, i) -> {
                    val rows = mutableListOf<List<String>>()
                    while (i < lines.size && lines[i].isNotBlank() && lines[i].contains("|")) {
                        rows += lines[i].trim().trim('|').split("|").map { it.trim() }
                        i++
                    }
                    out += Block.Table(rows)
                }

                QUOTE.matches(line) -> {
                    val q = mutableListOf<String>()
                    while (i < lines.size && QUOTE.matches(lines[i])) {
                        q += QUOTE.find(lines[i])!!.groupValues[1]
                        i++
                    }
                    out += Block.Quote(q)
                }

                LIST_ITEM.matches(line) -> {
                    val items = mutableListOf<Block.ListItem>()
                    while (i < lines.size && LIST_ITEM.matches(lines[i])) {
                        val m = LIST_ITEM.find(lines[i])!!
                        val indent = m.groupValues[1].length
                        val marker = m.groupValues[2]
                        var text = m.groupValues[3]
                        val t = TASK.find(text)
                        val task = t?.let { it.groupValues[1] == "x" || it.groupValues[1] == "X" }
                        if (t != null) text = t.groupValues[2]
                        items += Block.ListItem(
                            level = indent / 2,
                            ordered = marker[0].isDigit(),
                            marker = marker,
                            task = task,
                            text = text,
                        )
                        i++
                    }
                    out += Block.ListBlock(items)
                }

                else -> {
                    // 段落：连续行合并（Obsidian 软换行=空格）；遇块级起点提前结束
                    val buf = mutableListOf<String>()
                    while (i < lines.size) {
                        val l = lines[i]
                        if (l.isBlank()) break
                        if (HEADING.matches(l) || HR.matches(l.trim()) || FENCE_OPEN.matches(l) ||
                            QUOTE.matches(l) || LIST_ITEM.matches(l) || isTableStart(lines, i)) break
                        buf += l; i++
                    }
                    val text = buf.joinToString(" ")
                    when {
                        IMG_LINE.matches(text) -> out += Block.Image(IMG_LINE.find(text)!!.groupValues[1], "", false)
                        EMBED_LINE.matches(text) -> out += Block.Image(EMBED_LINE.find(text)!!.groupValues[1], "", true)
                        // $$ 块级公式：无 LaTeX 引擎，等宽代码块呈现
                        text.matches(Regex("^\\$\\$[\\s\\S]+\\$\\$$")) ->
                            out += Block.Fence(null, text.removeSurrounding("$$"))
                        else -> out += Block.Paragraph(text)
                    }
                }
            }
        }
        return out
    }

    private fun isTableStart(lines: List<String>, i: Int): Boolean =
        i + 1 < lines.size && lines[i].contains("|") && TABLE_SEP.matches(lines[i + 1].trim())
}

// ────────────────────────── 行内格式化 ──────────────────────────

/** 顺序即优先级：双链/嵌入/行内图 → 代码 → 删除线/高亮 → 粗体 → 斜体 → 数学 → 链接 → URL → 标签 */
private val INLINE = Regex(
    "(!?\\[\\[[^\\]]+]]|!\\[[^\\]]*]\\([^)]+\\))" +       // 1
        "|(`[^`]+`)" +                                       // 2
        "|(~~[^~]+~~|==[^=]+==)" +                           // 3
        "|(\\*\\*[^*]+\\*\\*|__[^_]+__)" +                   // 4
        "|(\\*[^*]+\\*|_[^_]+_)" +                           // 5
        "|(\\$[^$]+\\$)" +                                   // 6
        "|(\\[[^\\]]+]\\([^)]+\\))" +                        // 7
        "|(https?://[^\\s)\\]>]+)" +                         // 8
        "|(#[\\p{L}\\p{N}_/-]+)"                             // 9
)

private data class LinkSpan(val start: Int, val end: Int, val url: String? = null, val wiki: String? = null)

private data class InlineResult(val text: AnnotatedString, val links: List<LinkSpan>)

private fun renderInline(raw: String, accent: ThemeAccent = themeAccent.value): InlineResult {
    val sb = AnnotatedString.Builder()
    val links = mutableListOf<LinkSpan>()
    var last = 0
    for (m in INLINE.findAll(raw)) {
        if (m.range.first > last) sb.append(raw.substring(last, m.range.first))
        last = m.range.last + 1
        val tok = m.value
        val start = sb.length
        // 组 2（代码）比较特殊，其余按前缀判断；顺序与 INLINE 优先级一致
        if (m.groups[2] != null) {
            sb.append(tok.trim('`'))
            sb.addStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = accent.soft, background = Color(0x1AFFFFFF)), start, sb.length)
        } else when {
            tok.startsWith("![") -> { // 行内图片（独立一行时已在块级放大渲染）
                val alt = Regex("^!\\[([^]]*)]").find(tok)?.groupValues?.get(1).orEmpty().ifBlank { "图片" }
                sb.append(alt)
                sb.addStyle(SpanStyle(color = TextFaint), start, sb.length)
            }
            tok.startsWith("![[") -> { // 行内嵌入
                val inner = tok.removeSurrounding("[[", "]]").trim()
                sb.append("📎 " + inner.substringAfter('|', inner).trim().ifBlank { inner.substringBefore('|') })
                sb.addStyle(SpanStyle(color = TextFaint), start, sb.length)
            }
            tok.startsWith("[[") -> { // [[双链]]
                val inner = tok.removeSurrounding("[[", "]]")
                val name = inner.substringBefore('|').trim()
                val display = inner.substringAfter('|', name).trim().ifBlank { name }
                sb.append(display)
                sb.addStyle(SpanStyle(color = Color(0xFF8AB4FF), textDecoration = TextDecoration.Underline), start, sb.length)
                links += LinkSpan(start, sb.length, wiki = name)
            }
            tok.startsWith("~~") -> {
                sb.append(tok.removeSurrounding("~~"))
                sb.addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), start, sb.length)
            }
            tok.startsWith("==") -> {
                sb.append(tok.removeSurrounding("=="))
                sb.addStyle(SpanStyle(background = Color(0x55FFD76A), color = TextHi), start, sb.length)
            }
            tok.startsWith("**") || tok.startsWith("__") -> {
                sb.append(tok.removeSurrounding("**").ifEmpty { tok.removeSurrounding("__") })
                sb.addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, sb.length)
            }
            tok.startsWith("*") || tok.startsWith("_") -> {
                sb.append(tok.removeSurrounding("*").ifEmpty { tok.removeSurrounding("_") })
                sb.addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, sb.length)
            }
            tok.startsWith("$") -> {
                sb.append(tok.removeSurrounding("$"))
                sb.addStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = accent.soft), start, sb.length)
            }
            tok.startsWith("[") -> { // [text](url)
                val inner = Regex("^\\[([^]]+)]\\(([^)]+)\\)$").find(tok)
                val label = inner?.groupValues?.get(1) ?: tok
                val url = inner?.groupValues?.get(2).orEmpty()
                sb.append(label)
                sb.addStyle(SpanStyle(color = accent.soft, textDecoration = TextDecoration.Underline), start, sb.length)
                links += LinkSpan(start, sb.length, url = url)
            }
            tok.startsWith("http") -> { // 自动 URL
                sb.append(tok)
                sb.addStyle(SpanStyle(color = accent.soft, textDecoration = TextDecoration.Underline), start, sb.length)
                links += LinkSpan(start, sb.length, url = tok)
            }
            else -> { // #标签
                sb.append(tok)
                sb.addStyle(SpanStyle(color = accent.chipText, fontWeight = FontWeight.Bold), start, sb.length)
            }
        }
    }
    if (last < raw.length) sb.append(raw.substring(last))
    return InlineResult(sb.toAnnotatedString(), links)
}

// ────────────────────────── 渲染层（Compose） ──────────────────────────

private val CALLOUT_ICON = mapOf(
    "note" to "📄", "info" to "ℹ️", "tip" to "💡", "success" to "✅", "question" to "❓",
    "warning" to "⚠️", "danger" to "🔥", "error" to "🔥", "failure" to "❌", "bug" to "🐛",
    "example" to "✨", "quote" to "📝", "abstract" to "📋", "todo" to "📌",
)

@Composable
fun MarkdownView(
    md: String,
    originPath: String? = null,
    vaultRoot: File,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(md) { MdParse.parse(md) }
    val ctx = LocalContext.current
    val originDir = originPath?.substringBeforeLast('/') ?: ""
    val onWiki: (String) -> Unit = { name -> openWiki(ctx, vaultRoot, originDir, name) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (block in blocks) BlockView(block, originPath, vaultRoot, onWiki)
    }
}

@Composable
private fun BlockView(block: MdParse.Block, originPath: String?, vaultRoot: File, onWiki: (String) -> Unit) {
    when (block) {
        is MdParse.Block.Heading -> {
            val size = when (block.level) { 1 -> 17.sp; 2 -> 15.sp; else -> 14.sp }
            InlineText(block.text, TextStyle(color = TextHi, fontSize = size, fontWeight = FontWeight.Bold), onWiki = onWiki)
        }
        is MdParse.Block.Paragraph -> {
            // XHS Notes 插件同款视频内嵌标记（<video controls src="...">）：本端显示占位
            if (block.text.startsWith("<video")) {
                val src = Regex("""src="([^"]+)"""").find(block.text)?.groupValues?.get(1).orEmpty()
                Text(
                    "🎬 视频：${src.substringAfterLast('/')}",
                    color = TextFaint, fontSize = 12.sp,
                )
            } else {
                InlineText(block.text, TextStyle(color = TextMid, fontSize = 13.sp, lineHeight = 20.sp), onWiki = onWiki)
            }
        }
        is MdParse.Block.Quote -> QuoteView(block.lines, onWiki)
        is MdParse.Block.Fence -> FenceView(block.lang, block.code)
        is MdParse.Block.ListBlock -> for (item in block.items) ListItemView(item, onWiki)
        // ListItem 是 Block 直接子类型（解析时总被包进 ListBlock），兜底渲染
        is MdParse.Block.ListItem -> ListItemView(block, onWiki)
        is MdParse.Block.Table -> TableView(block.rows)
        is MdParse.Block.Image -> ImageView(block.ref, block.alt, block.embed, originPath, vaultRoot)
        is MdParse.Block.Rule -> HorizontalDivider(color = GlassStrokeSoft)
    }
}

/** 带行内样式 + 点击跳转的文本（链接开浏览器、[[双链]] 开本地 vault 文件） */
@Composable
private fun InlineText(raw: String, style: TextStyle, modifier: Modifier = Modifier, onWiki: (String) -> Unit = {}) {
    val accentNow = themeAccent.value
    val result = remember(raw, accentNow) { renderInline(raw, accentNow) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val uriHandler = LocalUriHandler.current
    Text(
        result.text,
        style = style,
        onTextLayout = { layout = it },
        modifier = modifier.pointerInput(result.links, result.text) {
            detectTapGestures { pos ->
                val lr = layout ?: return@detectTapGestures
                val off = lr.getOffsetForPosition(pos)
                val hit = result.links.firstOrNull { off in it.start until it.end } ?: return@detectTapGestures
                when {
                    hit.url != null -> uriHandler.openUri(hit.url)
                    hit.wiki != null -> onWiki(hit.wiki)
                }
            }
        },
    )
}

@Composable
private fun QuoteView(lines: List<String>, onWiki: (String) -> Unit) {
    val first = lines.firstOrNull().orEmpty().trim()
    val call = MdParse.CALLOUT.find(first)
    if (call != null) {
        val type = call.groupValues[1].lowercase()
        val title = call.groupValues[2].ifBlank { type }
        val color = when (type) {
            "tip", "success" -> Success; "warning", "caution" -> AccentOrange
            "danger", "error", "failure", "bug" -> AccentRed
            else -> Color(0xFF6FA8FF)
        }
        Column(
            Modifier.fillMaxWidth()
                .background(color.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                .padding(start = 10.dp, top = 8.dp, end = 10.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("${CALLOUT_ICON[type] ?: "ℹ️"} $title", color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            for (line in lines.drop(1)) {
                if (line.isNotBlank()) InlineText(line, TextStyle(color = TextMid, fontSize = 13.sp, lineHeight = 20.sp), onWiki = onWiki)
            }
        }
    } else {
        Column(
            Modifier.fillMaxWidth()
                .background(Color(0x0AFFFFFF), RoundedCornerShape(10.dp))
                .padding(vertical = 6.dp, horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (line in lines) {
                if (line.isNotBlank()) InlineText(line, TextStyle(color = TextMid, fontSize = 13.sp, lineHeight = 20.sp), onWiki = onWiki)
            }
        }
    }
}

@Composable
private fun FenceView(lang: String?, code: String) {
    Column(
        Modifier.fillMaxWidth()
            .background(Color(0x14000000), RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (lang != null) Text(lang, color = TextFaint, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Text(code, color = TextMid, fontSize = 12.sp, lineHeight = 17.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun ListItemView(item: MdParse.Block.ListItem, onWiki: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = (8 + item.level * 14).dp)) {
        val marker = when {
            item.task == true -> "☑"
            item.task == false -> "☐"
            item.ordered -> item.marker
            else -> "•"
        }
        Text(
            marker,
            color = when {
                item.task == true -> Success
                item.task == false -> TextFaint
                item.ordered -> AccentGreenSoft
                else -> TextMid
            },
            fontSize = 13.sp, fontWeight = FontWeight.Bold,
        )
        InlineText(
            item.text,
            TextStyle(color = TextMid, fontSize = 13.sp, lineHeight = 20.sp),
            modifier = Modifier.padding(start = 4.dp),
            onWiki = onWiki,
        )
    }
}

@Composable
private fun TableView(rows: List<List<String>>) {
    if (rows.isEmpty()) return
    val cols = rows.maxOf { it.size }
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x0AFFFFFF)),
    ) {
        for ((ri, row) in rows.withIndex()) {
            if (ri > 0) HorizontalDivider(color = GlassStrokeSoft)
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                for (c in 0 until cols) {
                    val cell = row.getOrElse(c) { "" }
                    val style = if (ri == 0) TextStyle(color = TextHi, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    else TextStyle(color = TextMid, fontSize = 12.sp)
                    Text(
                        renderInline(cell).text, style = style,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ImageView(ref: String, alt: String, embed: Boolean, originPath: String?, vaultRoot: File) {
    // 视频嵌入（![[media/x.mp4]]）：不能当图片渲染，显示占位（视频文件已随同步拉取）
    if (ref.substringAfterLast('.').lowercase() in setOf("mp4", "mov", "mkv", "webm")) {
        Text("🎬 视频：$ref", color = TextFaint, fontSize = 12.sp)
        return
    }
    val originDir = originPath?.substringBeforeLast('/').orEmpty()
    val local = resolveLocal(vaultRoot, originDir, ref)
    val model: Any? = when {
        local != null -> local
        ref.startsWith("http") -> ref
        else -> null
    }
    when {
        model != null -> AsyncImage(
            model = model, contentDescription = alt,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(10.dp)),
        )
        embed -> Text("📄 $ref（文件未同步到本机）", color = TextFaint, fontSize = 12.sp)
        else -> Text("🖼 $ref（媒体未同步到本机）", color = TextFaint, fontSize = 12.sp)
    }
}

/** 媒体引用 → 本机 vault 文件（md 所在目录优先，再退 vault 根） */
private fun resolveLocal(vaultRoot: File, originDir: String, ref: String): File? {
    if (ref.startsWith("http")) return null
    val candidates = listOf(
        File(vaultRoot, if (originDir.isBlank()) ref else "$originDir/$ref"),
        File(vaultRoot, ref),
    )
    return candidates.firstOrNull { it.exists() }
}

/** [[双链]] 点击：解析 vault 内同名 md（与 Obsidian 无扩展名规则一致），存在则打开查看器 */
private fun openWiki(ctx: Context, vaultRoot: File, originDir: String, name: String) {
    if (name.isBlank()) return
    val names = if (name.contains('.')) listOf(name) else listOf(name, "$name.md")
    val candidates = names.flatMap { n ->
        listOf(File(vaultRoot, if (originDir.isBlank()) n else "$originDir/$n"), File(vaultRoot, n))
    }
    val target = candidates.firstOrNull { it.exists() } ?: return
    ctx.startActivity(
        Intent(ctx, FileViewerActivity::class.java)
            .putExtra("path", target.relativeTo(vaultRoot).path),
    )
}
