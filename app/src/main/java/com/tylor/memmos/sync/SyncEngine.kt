package com.tylor.memmos.sync

import android.content.Context
import android.util.Base64
import com.tylor.memmos.data.ClipNote
import com.tylor.memmos.data.ClipStore
import com.tylor.memmos.net.MediaDownloader
import com.tylor.memmos.util.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 双向同步引擎：
 * - 上传：手机剪藏（xhs 来源）→ 带 frontmatter 的 md → POST 到 Obsidian；
 *   远端已有同路径/同内容（sha256 比对）则跳过，不重复
 * - 下载：Obsidian 清单里手机没有的 md → GET → 解析成剪藏条目（vault 来源）入本地库；
 *   手机已有同 origin=path 的条目且内容指纹一致则跳过
 */
object SyncEngine {

    data class Result(val uploaded: Int, val downloaded: Int, val skipped: Int)

    /** 帖子 md 判定：我们上传的剪藏（固定结构 origin content/{标题}/note.md 或带 memmos-id 的 md）。
     * 下载侧必须跳过它们——否则电脑端的帖子会被当作普通笔记下载回手机，形成重复传递。 */
    private fun isPostMd(path: String, md: String? = null): Boolean {
        if (path.contains("/origin content/")) return true
        if (md != null && md.contains("memmos-id:")) return true
        return false
    }

    /** 同步根目录：用户要求固定进 Memmos graph（子文件夹按分类自动创建，无需预建） */
    private fun rootDir(client: SyncClient): String = client.rootFolder.ifBlank { "Memmos graph" }

    /** 手机剪藏 → Obsidian md 路径（用户要求：二个顶层文件夹 + 每帖一夹，双向识别稳定）：
     * {根}/media/  ← 全部图片/视频；{根}/origin content/{标题文件夹}/note.md
     * 文件名固定 note.md；标题变化不影响 memo 内部 memmos-id 匹配
     */
    private fun mdPath(client: SyncClient, note: ClipNote): String {
        val root = rootDir(client)
        val folder = note.title.replace(Regex("""[\\/:*?"<>|]"""), "-").trim()
            .replace(Regex("""\s+"""), "-").take(45).ifBlank { "未命名" }
        return "$root/origin content/$folder/note.md"
    }

    /**
     * 剪藏 → Obsidian md（对齐 XHS Notes 插件的 md 结构，用户要求）：
     * frontmatter（title/source/url/author/avatar/tags/type/memmos-id/clippedAt/category）+
     * `# 标题` + 首图 `![Cover Image](...)`（视频为 `<video controls src=...>` 内嵌，
     * 本地媒体统一 {根}/media/，从分类子目录用 ../media/ 相对引用）+ 正文 +
     * tags 代码块 + 余图 `![Image](...)` + 评论（主/楼中楼）区块。
     */
    fun toMarkdown(note: ClipNote, imageRefs: List<String>? = null, videoRef: String? = null): String = buildString {
        appendLine("---")
        appendLine("title: ${note.title}")
        appendLine("source: ${note.pageUrl}")
        appendLine("url: ${note.pageUrl}")
        appendLine("author: ${note.author}")
        if (note.avatarUrl.isNotBlank()) appendLine("avatar: ${note.avatarUrl}")
        appendLine("tags: [${note.tags.distinct().joinToString(", ")}]")
        appendLine("type: ${note.type}")
        appendLine("memmos-id: ${note.id}")
        appendLine("clippedAt: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(note.clippedAt))}")
        appendLine("---")
        appendLine()
        appendLine("# ${note.title}")
        appendLine()
        val imgs = imageRefs ?: note.imageUrls
        val vid = videoRef ?: note.videoUrl
        // 图片全部先显示（用户要求：帖子的图片先显示完，再显示正文内容）
        if (imgs.isNotEmpty()) {
            appendLine("![Cover Image](${imgs.first()})")
            imgs.drop(1).forEach { appendLine(); appendLine("![Image]($it)") }
        }
        if (vid != null) {
            // 视频：Obsidian/插件同款 <video> 内嵌；无本地引用时直接嵌远程直链
            appendLine()
            appendLine("<video controls src=\"$vid\" width=\"100%\"></video>")
        }
        appendLine()
        appendLine(note.desc)
        if (note.tags.isNotEmpty()) {
            appendLine("```")
            appendLine(note.tags.distinct().joinToString(" ") { "#$it" })
            appendLine("```")
        }
        // 评论：主评论 + 楼中楼，名单行格式（内容换行拍平防止破坏列表结构）
        if (note.comments.isNotEmpty()) {
            appendLine()
            appendLine("## 评论")
            note.comments.forEach { c ->
                val body = c.content.replace('\n', ' ').trim()
                val like = if (c.likes > 0) "（♥${c.likes}）" else ""
                appendLine("- **${c.nickname.ifBlank { "匿名用户" }}**：$body$like")
                c.subComments.forEach { sc ->
                    val sBody = sc.content.replace('\n', ' ').trim()
                    appendLine("  - **${sc.nickname.ifBlank { "匿名用户" }}**：$sBody")
                }
            }
        }
    }

    /**
     * md → 手机剪藏（frontmatter 粗解析：title/author/url/tags + 正文）。
     * authorOverride/avatarOverride：个人资料（设置页），Obsidian 同步笔记的作者与头像
     * 自动设为该身份（md frontmatter 无作者时）。
     */
    fun fromMarkdown(
        path: String,
        md: String,
        authorOverride: String? = null,
        avatarOverride: String? = null,
    ): ClipNote {
        val fm = Regex("""^---\n([\s\S]*?)\n---\n?""").find(md)
        val meta = fm?.groupValues?.get(1) ?: ""
        val body = if (fm != null) md.substring(fm.range.last + 1).trim() else md
        fun field(name: String) = Regex("""^$name:\s*(.*)$""", RegexOption.MULTILINE).find(meta)?.groupValues?.get(1)?.trim().orEmpty()
        val tags = Regex("""^tags:\s*\[(.*)]$""", RegexOption.MULTILINE).find(meta)?.groupValues?.get(1)
            ?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
        return ClipNote(
            id = "vault:${sha16(md)}",
            title = field("title").ifBlank { path.substringAfterLast('/').removeSuffix(".md") },
            desc = body,
            author = field("author").ifBlank { authorOverride.orEmpty() },
            tags = tags,
            imageUrls = Regex("""!\[\]\((https?://[^)]+)\)""").findAll(md).map { it.groupValues[1] }.toList(),
            videoUrl = null,
            type = "md",
            pageUrl = field("url").ifBlank { field("source") }, // importer frontmatter 用 source 字段
            clippedAt = System.currentTimeMillis(),
            origin = "vault",
            originPath = path,
            rawMd = md,
            avatarUrl = avatarOverride.orEmpty(),
        )
    }

    /**
     * 同步进度（用户要求进度条）：done/total 按阶段推进，null=空闲。
     * SettingsPage 据此显示进度条；同步期间 UI 可轮询刷新。
     */
    data class SyncProgress(val phase: String, val done: Int, val total: Int) {
        val fraction: Float get() = if (total > 0) done.toFloat() / total else 0f
    }

    val progress = MutableStateFlow<SyncProgress?>(null)

    private fun sha16(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)

    /**
     * 批量上传指定剪藏（多选编辑用）：去重逻辑与 sync 相同，
     * 返回实际上传条数；originPath 写回本地库。
     */
    suspend fun uploadNotes(ctx: Context, client: SyncClient, notes: List<ClipNote>): Int = withContext(Dispatchers.IO) {
        val store = ClipStore(ctx)
        val local = store.load()
        val remote = client.inventory().associateBy { it.path }
        var up = 0
        for (note in notes.filter { it.origin != "vault" }) {
            if (pushNote(ctx, client, note, local, remote)) up++
        }
        store.save(local)
        return@withContext up
    }

    /**
     * 推一篇剪藏到 Obsidian：图片/视频（本地已下载）→ {目录}/小红书/media/ 并改相对引用，
     * md 用 toMarkdown 全内容格式（评论/作者/头像/标签/视频嵌入）；远端同指纹则跳过。
     */
    private suspend fun pushNote(
        ctx: Context,
        client: SyncClient,
        note: ClipNote,
        local: MutableList<ClipNote>,
        remote: Map<String, SyncClient.InvItem>,
    ): Boolean {
        val path = mdPath(client, note)
        val root = rootDir(client)
        // 结构：媒体统一 {根}/media/；md 在 {根}/origin content/{标题}/note.md → ../../media/
        val relPrefix = "../../media"
        // 媒体上传：图片/视频下载字节 → base64 推到 Obsidian {根}/media/（与插件同目录布局）
        val imgRefs = mutableListOf<String>()
        note.imageUrls.forEachIndexed { i, u ->
            val rel = runCatching {
                val bytes = MediaDownloader.downloadBytes(u)
                val name = "${note.id.take(12)}-${i + 1}.${if (u.contains(".png", true)) "png" else "jpg"}"
                client.postBinary("$root/media/$name", bytes)
                "$relPrefix/$name"
            }.getOrElse { u }
            imgRefs.add(rel)
        }
        var vidRef: String? = null
        note.localVideoPath?.let { p ->
            val f = java.io.File(p)
            if (f.exists()) {
                vidRef = runCatching {
                    val name = "${note.id.take(12)}.mp4"
                    client.postBinary("$root/media/$name", f.readBytes())
                    "$relPrefix/$name"
                }.getOrNull()
            }
        }
        val md = toMarkdown(note, imgRefs, vidRef)
        val hash = sha16(md)
        if (remote[path]?.sha256 == hash) return false // 内容一致（含媒体引用）跳过
        // 调试日志：上传失败静默跳过会成为"已是最新"假象，必须可见
        android.util.Log.d("MemmosDbg", "pushStart ${note.title.take(12)} -> $path (img=${imgRefs.size}, vid=${vidRef != null})")
        return runCatching {
            client.postFile(path, md)
            val idx = local.indexOfFirst { it.id == note.id }
            if (idx >= 0) local[idx] = note.copy(originPath = path)
            true
        }.getOrElse { e ->
            android.util.Log.d("MemmosDbg", "pushFail ${note.title.take(12)}: ${e.message}")
            false
        }
    }

    suspend fun sync(ctx: Context, client: SyncClient): Result = withContext(Dispatchers.IO) {
        val store = ClipStore(ctx)
        val local = store.load()
        val remote = client.inventory().associateBy { it.path }

        // ── 上传：xhs 剪藏（带评论/作者/标签/媒体，互相补全：手机有而电脑没有的内容推上去） ──
        var up = 0; var skip = 0
        val ups = local.filter { it.origin != "vault" }
        // 进度只统计「需要同步的项」：远端无此路径或从未上传过 → 需要（已最新不计入）
        val needUp = ups.count { n ->
            val p = mdPath(client, n)
            remote[p] == null || n.originPath != p
        }
        if (needUp > 0) {
            var doneU = 0
            progress.value = SyncProgress("上传到电脑", 0, needUp)
            for (note in ups) {
                val p = mdPath(client, note)
                val needed = remote[p] == null || note.originPath != p
                if (pushNote(ctx, client, note, local, remote)) up++
                else if (needed) skip++
                if (needed) {
                    doneU++
                    progress.value = SyncProgress("上传到电脑", doneU, needUp)
                }
            }
        }

        // ── 下载/更新：远端 md（按 originPath 对位，指纹不同才拉） ──
        var down = 0
        val vaultDir = java.io.File(ctx.filesDir, "vault").apply { mkdirs() }
        // 进度同样只统计需要下载的项（非帖子 md + 缺失附件；已最新不计入）
        fun isBinaryHead(f: java.io.File): Boolean {
            val head = runCatching {
                f.inputStream().use { it.readNBytes(16) }
            }.getOrNull() ?: return false
            val b = head
            return (b.size > 2 && b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte()) || // jpg
                (b.size > 4 && b[0] == 0x89.toByte() && b[1] == 0x50.toByte()) || // png
                (b.size > 4 && b[0] == 0x25.toByte() && b[1] == 0x50.toByte()) || // %PDF
                (b.size > 4 && b[0] == 0x50.toByte() && b[1] == 0x4B.toByte()) || // PK(zip/docx)
                (b.size > 12 && String(b, 4, 4) == "ftyp") || // mp4/mov
                (b.size > 3 && b[0] == 0x49.toByte() && b[1] == 0x44.toByte() && b[2] == 0x33.toByte()) // id3
        }

        fun needFile(path: String, item: SyncClient.InvItem): Boolean {
            if (isPostMd(path)) return false
            if (path.endsWith(".md", true)) {
                val ex = local.firstOrNull { it.originPath == path }
                return ex == null || sha16(ex.rawMd.orEmpty()) != item.sha256
            }
            // 附件（媒体/文档）：原样保存到 vault，Library「同步文件」区展示；
            // 已存在且为合法二进制内容才跳过（旧版把媒体当 md 下载过，留下的是乱码文本）
            val f = java.io.File(vaultDir, path)
            return !(f.exists() && f.length() > 0 && isBinaryHead(f))
        }
        val needDown = remote.count { (path, item) -> needFile(path, item) }
        android.util.Log.d(
            "MemmosDbg",
            "sync: inventory=${remote.size} (posts=${remote.count { isPostMd(it.key) }}) needUp=$needUp needDown=$needDown",
        )
        if (needDown > 0) progress.value = SyncProgress("下载到手机", 0, needDown)
        var doneD = 0
        for ((path, item) in remote) {
            if (!needFile(path, item)) continue // 帖子/已最新一致 → 跳过；Obsidian 侧改过（指纹不同）→ 更新手机
            doneD++
            progress.value = SyncProgress("下载到手机", doneD.coerceAtMost(needDown), needDown)
            if (!path.endsWith(".md", true)) {
                // 附件下载：原样写 vault（不做 md 解析——旧版把媒体当笔记下载污染剪藏库）
                val f = java.io.File(vaultDir, path)
                val b64 = runCatching { client.getBinary(path) }.getOrDefault("")
                if (b64.isNotBlank()) {
                    f.parentFile?.mkdirs()
                    f.writeBytes(Base64.decode(b64, Base64.NO_WRAP))
                }
                continue
            }
            android.util.Log.d("MemmosDbg", "down md: $path sha=${item.sha256.take(8)}")
            runCatching {
                val md = client.getFile(path)
                if (sha16(md) != item.sha256) return@runCatching // 内容变动中，跳过保一致
                // 个人资料兜底：Obsidian 同步笔记自动带上用户设置的名字/头像（空则不覆盖）
                val note = fromMarkdown(
                    path, md,
                    AppPrefs.profileName(ctx).ifBlank { null },
                    AppPrefs.profileAvatar(ctx).ifBlank { null },
                )
                val idx = local.indexOfFirst { it.originPath == path }
                if (idx >= 0) local[idx] = note else local.add(0, note)
                down++
                // md 引用的本地媒体一并拉取：![](media/x) / ![[media/x]] / <video src> / [x](media/x)
                // 支持 ../media/（分类子目录引用根级 media，同 XHS Notes 结构）
                val mdDir = path.substringBeforeLast('/')
                val mediaRefs = mutableSetOf<String>()
                Regex("""\(((?:\.\./)*)media/[^)]+\)""").findAll(md).forEach { mediaRefs += it.value.trim('(', ')') }
                Regex("""!\[\[((?:\.\./)*)media/[^\]]+)\]\]""").findAll(md).forEach { mediaRefs += it.groupValues[1] }
                Regex("""src="((?:\.\./)*)media/[^"]+)"""").findAll(md).forEach { mediaRefs += it.groupValues[1] }
                mediaRefs.forEach { rel ->
                    val target = java.io.File(vaultDir, "$mdDir/$rel")
                    if (target.exists()) return@forEach
                    val b64 = runCatching { client.getBinary("$mdDir/$rel") }.getOrDefault("")
                    if (b64.isNotBlank()) {
                        target.parentFile?.mkdirs()
                        target.writeBytes(Base64.decode(b64, Base64.NO_WRAP))
                    }
                }
                // md 本身也存一份到 vault 目录（阅读器可读）
                val mdFile = java.io.File(vaultDir, path)
                mdFile.parentFile?.mkdirs()
                mdFile.writeText(md)
            }
        }

        // 帖子副本清理（用户要求：防止帖子两端重复）：
        // 凡「内容含 memmos-id 标记」的 vault 条目 = 手机上传帖子的误下载副本（新旧路径结构都覆盖），
        // 连同 filesDir/vault 下副本文件一并删除；电脑端帖子 md 在下载循环中已被 isPostMd 跳过
        runCatching { java.io.File(ctx.filesDir, "vault/origin content").deleteRecursively() }
        val dupes = local.filter { it.origin == "vault" && (it.rawMd ?: "").contains("memmos-id:") }
        dupes.forEach { d ->
            d.originPath?.takeIf { it.isNotBlank() }?.let { p ->
                runCatching { java.io.File(ctx.filesDir, "vault/$p").delete() }
            }
        }
        local.removeAll(dupes)
        if (dupes.isNotEmpty()) android.util.Log.d("MemmosDbg", "清理帖子 vault 重复条目 ${dupes.size} 条")
        progress.value = null
        store.save(local)
        return@withContext Result(up, down, skip)
    }
}
