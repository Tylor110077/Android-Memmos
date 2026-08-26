package com.tylor.memmos.sync

import android.content.Context
import android.util.Base64
import com.tylor.memmos.data.ClipComment
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

    /** 同步结果：uploaded=上传篇数 · deleted=清理的远端已删帖子 · skipped=上传失败篇数 */
    data class Result(val uploaded: Int, val deleted: Int, val skipped: Int)

    /** 帖子 md 里的 memmos-id（远端联删/归属识别用） */
    private val POST_ID_RE = Regex("""^memmos-id:\s*(\S+)""", RegexOption.MULTILINE)

    private fun rootDir(client: SyncClient): String = client.rootFolder.ifBlank { "Memmos graph" }

    /** 内容源 → Obsidian 大文件夹（英文名；按源隔离，后续源自动归入）
     * xhs=xiaohongshu；bilibili=bilibili；未知兜底 xiaohongshu */
    private fun sourceFolder(note: ClipNote): String =
        if (note.origin == "bilibili") "bilibili" else "xiaohongshu"

    /** 手机剪藏 → Obsidian md 路径（用户要求：{根}/{源}/origin content/{标题}/note.md）：
     * {根}/{源}/media/ ← 该源全部图片/视频；{根}/{源}/AI summary/{标题}/总结.md
     * 文件名固定 note.md；标题变化不影响 memo 内部 memmos-id 匹配 */
    private fun mdPath(client: SyncClient, note: ClipNote): String {
        val root = rootDir(client)
        val src = sourceFolder(note)
        val folder = note.title.replace(Regex("""[\\/:*?"<>|]"""), "-").trim()
            .replace(Regex("""\s+"""), "-").take(45).ifBlank { "未命名" }
        return "$root/$src/origin content/$folder/note.md"
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
     * 同步进度（用户要求进度条）：done/total 按阶段推进，null=空闲。
     * SettingsPage 据此显示进度条；同步期间 UI 可轮询刷新。
     */
    data class SyncProgress(val phase: String, val done: Int, val total: Int) {
        val fraction: Float get() = if (total > 0) done.toFloat() / total else 0f
    }

    val progress = MutableStateFlow<SyncProgress?>(null)

    /** 最近一次同步结果消息（显示在同步进度位置；null=还没有同步过） */
    val lastSyncMsg = MutableStateFlow<String?>(null)

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
        val src = sourceFolder(note)
        // 结构：媒体统一 {根}/{源}/media/；md 在 {根}/{源}/origin content/{标题}/note.md → ../../media/
        val relPrefix = "../../media"
        // 媒体上传：图片/视频下载字节 → base64 推到 Obsidian {根}/{源}/media/
        val imgRefs = mutableListOf<String>()
        note.imageUrls.forEachIndexed { i, u ->
            val rel = runCatching {
                val bytes = MediaDownloader.downloadBytes(u)
                val name = "${note.id.take(12)}-${i + 1}.${if (u.contains(".png", true)) "png" else "jpg"}"
                client.postBinary("$root/$src/media/$name", bytes)
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
                    client.postBinary("$root/$src/media/$name", f.readBytes())
                    "$relPrefix/$name"
                }.getOrNull()
            }
        }
        var md = toMarkdown(note, imgRefs, vidRef)
        val folder = path.substringAfter("/origin content/").substringBeforeLast("/note.md")
        // AI 总结（用户要求）：note 在特定位置引用独立 "AI summary" 文件（Obsidian wiki link）
        if (!note.aiSummary.isNullOrBlank()) {
            md = md.replaceFirst(
                "# ${note.title}",
                "# ${note.title}\n\n## AI 总结\n\n[[$src/AI summary/$folder/summary.md]]\n",
            )
        }
        val hash = sha16(md)
        // AI summary 文件（note 之外独立生成；指纹不变跳过）
        if (!note.aiSummary.isNullOrBlank()) {
            val summaryPath = "$root/$src/AI summary/$folder/summary.md"
            val summaryMd = buildString {
                appendLine("---")
                appendLine("memmos-id: ${note.id}")
                appendLine("source: ${note.pageUrl}")
                appendLine("---")
                appendLine()
                append(note.aiSummary)
            }
            if (remote[summaryPath]?.sha256 != sha16(summaryMd)) {
                client.postFile(summaryPath, summaryMd)
            }
        }
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

        // ── 远端联删（手机唯一真源，用户 2026-08-26 决定单向同步）：
        //   手机已删除的帖子 → 连带删除 Obsidian 上的 note.md 与媒体附件（media/{id12}-*）；
        //   「Obsidian 有而手机没有」目前不处理（浏览器插件阶段再说）。 ──
        var deletedRemote = 0
        val myPostIds = local.filter { it.origin != "vault" }.map { it.id }.toSet()
        val root = rootDir(client)
        var remotePosts = 0
        for ((path, item) in remote) {
            if (!path.endsWith(".md", true) || !path.contains("/origin content/")) continue
            remotePosts++
            val md = runCatching { client.getFileRaw(path) }.getOrNull() ?: continue
            val postId = POST_ID_RE.find(md)?.groupValues?.get(1) ?: continue
            if (postId in myPostIds) continue
            val prefix = "${postId.take(12)}"
            val srcDir = path.substringBefore("/origin content/") // {根}/{源}
            runCatching { client.deleteFile(path) }
                .onFailure { android.util.Log.d("MemmosDbg", "remote delete md fail $path: ${it.message}") }
            remote.keys.filter { it.startsWith("$srcDir/media/$prefix") }.forEach { a ->
                runCatching { client.deleteFile(a) }
                    .onFailure { android.util.Log.d("MemmosDbg", "remote delete media fail $a: ${it.message}") }
            }
            android.util.Log.d("MemmosDbg", "remote delete post: $path (id=$postId) attachments removed")
            deletedRemote++
        }
        android.util.Log.d(
            "MemmosDbg",
            "sync: inventory=${remote.size} posts=$remotePosts needUp=$needUp orphanToDelete=$deletedRemote",
        )
        // 历史残留清理（早期误下载的帖子 vault 副本，防止重复条目）
        runCatching { java.io.File(ctx.filesDir, "vault/origin content").deleteRecursively() }
        val dupes = local.filter { it.origin == "vault" && (it.rawMd ?: "").contains("memmos-id:") }
        dupes.forEach { d ->
            d.originPath?.takeIf { it.isNotBlank() }?.let { pth ->
                runCatching { java.io.File(ctx.filesDir, "vault/$pth").delete() }
            }
        }
        local.removeAll(dupes)
        if (dupes.isNotEmpty()) android.util.Log.d("MemmosDbg", "清理帖子 vault 重复条目 ${dupes.size} 条")
        lastSyncMsg.value = when {
            up == 0 && deletedRemote == 0 ->
                "两端一致：手机内容已全部同步到 Obsidian（无变化）"
            else -> "同步完成：上传 $up 篇 · 清理已删帖子 $deletedRemote 个 · 失败 $skip"
        }
        progress.value = null
        store.save(local)
        return@withContext Result(up, deletedRemote, skip)
    }
}
