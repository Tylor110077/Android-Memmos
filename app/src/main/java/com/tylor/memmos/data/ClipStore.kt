package com.tylor.memmos.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 一条评论（主评论可带子评论） */
data class ClipComment(
    val nickname: String,
    val avatar: String,
    val content: String,
    val likes: Int = 0,
    val subComments: List<ClipComment> = emptyList(),
)

/** 一条剪藏的小红书笔记（字段对齐 xiaohongshu-importer 的解析结果） */
data class ClipNote(
    val id: String,
    val title: String,
    val desc: String,
    val author: String,
    val tags: List<String>,
    val imageUrls: List<String>,
    val videoUrl: String?,
    val type: String,
    val pageUrl: String,
    val clippedAt: Long,
    /** 来源：xhs=链接抓取，vault=从 Obsidian 同步下来的 md */
    val origin: String = "xhs",
    /** 同步锚点：上传后在 Obsidian 的 md 路径 / 下载条目对应的源路径 */
    val originPath: String? = null,
    /** vault 来源保留 md 原文，详情页可完整回看 */
    val rawMd: String? = null,
    /** 视频已下载到本地的路径（filesDir/media/xxx.mp4） */
    val localVideoPath: String? = null,
    /** 作者头像 URL */
    val avatarUrl: String = "",
    /** 评论（页面内嵌的前 N 条，N 受设置限制） */
    val comments: List<ClipComment> = emptyList(),
    /** 视频已保存到系统相册 */
    val inGallery: Boolean = false,
    /** AI 总结（Dots 多模态生成，内容/评论/图/视频理解） */
    val aiSummary: String? = null,
    /** AI 总结生成时间（0=未生成） */
    val aiSummaryTs: Long = 0L,
)

/**
 * 本地剪藏仓库：filesDir/clippings.json，最新在前。
 * P1 用 org.json 手写序列化（零额外依赖）；量大后再迁 Room。
 */
class ClipStore(context: Context) {
    private val file = context.filesDir.resolve("clippings.json")

    fun load(): MutableList<ClipNote> {
        if (!file.exists()) return mutableListOf()
        val text = runCatching { file.readText() }.getOrNull() ?: return mutableListOf()
        if (text.isBlank()) return mutableListOf()
        // 整体解析失败 = 文件损坏：备份后返回空（绝不静默覆盖）；逐条失败只丢该条
        val arr = runCatching { JSONArray(text) }.getOrNull() ?: return backupCorrupt()
        val out = mutableListOf<ClipNote>()
        for (i in 0 until arr.length()) {
            runCatching { parseNote(arr.getJSONObject(i)) }.getOrNull()?.let { out += it }
        }
        return out
    }

    /** 损坏保护：坏文件挪成 .corrupt-时间戳 供抢救，返回空但不覆盖 */
    private fun backupCorrupt(): MutableList<ClipNote> {
        runCatching {
            val bak = File(file.path + ".corrupt-" + System.currentTimeMillis())
            file.renameTo(bak)
        }
        return mutableListOf()
    }

    private fun parseNote(o: JSONObject): ClipNote = ClipNote(
        id = o.getString("id"),
        title = o.getString("title"),
        desc = o.getString("desc"),
        author = o.optString("author"),
        tags = List(o.getJSONArray("tags").length()) { o.getJSONArray("tags").getString(it) },
        imageUrls = List(o.getJSONArray("images").length()) { o.getJSONArray("images").getString(it) },
        videoUrl = o.optString("video").takeIf { it.isNotEmpty() },
        type = o.optString("type"),
        pageUrl = o.getString("pageUrl"),
        clippedAt = o.getLong("clippedAt"),
        origin = o.optString("origin").ifBlank { "xhs" },
        originPath = o.optString("originPath").takeIf { it.isNotEmpty() },
        rawMd = o.optString("rawMd").takeIf { it.isNotEmpty() },
        localVideoPath = o.optString("localVideo").takeIf { it.isNotEmpty() },
        avatarUrl = o.optString("avatarUrl"),
        inGallery = o.optBoolean("inGallery"),
        aiSummary = o.optString("aiSummary").takeIf { it.isNotEmpty() },
        aiSummaryTs = o.optLong("aiSummaryTs"),
        comments = runCatching {
            val arr = o.getJSONArray("comments")
            val cs = List(arr.length()) { i ->
                val c = arr.getJSONObject(i)
                ClipComment(
                    nickname = c.optString("nickname"),
                    avatar = c.optString("avatar"),
                    content = c.optString("content"),
                    likes = c.optInt("likes"),
                    subComments = runCatching {
                        val sa = c.getJSONArray("subs")
                        List(sa.length()) { j ->
                            val sc = sa.getJSONObject(j)
                            ClipComment(
                                nickname = sc.optString("nickname"),
                                avatar = sc.optString("avatar"),
                                content = sc.optString("content"),
                                likes = sc.optInt("likes"),
                            )
                        }
                    }.getOrDefault(emptyList()),
                )
            }
            // 旧数据兜底清洗：早期 EXTRACT_JS 曾把同一条评论按
            // .parent-comment/.comment-item 嵌套结构抓 2~5 遍，同 昵称+内容 只留首条
            val seen = mutableSetOf<String>()
            cs.filter { seen.add(it.nickname + "\u0000" + it.content) }
        }.getOrDefault(emptyList()),
    )

    /**
     * 原子写：先写 .tmp 再 rename 覆盖，避免写入中途进程被杀留下截断 JSON
     * （截断文件会让 load() 解析失败并返回空列表，等于整库数据全丢）。
     */
    fun save(list: List<ClipNote>) {
        val arr = JSONArray()
        list.forEach { n ->
            arr.put(
                JSONObject()
                    .put("id", n.id)
                    .put("title", n.title)
                    .put("desc", n.desc)
                    .put("author", n.author)
                    .put("tags", JSONArray(n.tags))
                    .put("images", JSONArray(n.imageUrls))
                    .put("video", n.videoUrl ?: "")
                    .put("type", n.type)
                    .put("pageUrl", n.pageUrl)
                    .put("clippedAt", n.clippedAt)
                    .put("origin", n.origin)
                    .put("originPath", n.originPath ?: "")
                    .put("rawMd", n.rawMd ?: "")
                    .put("localVideo", n.localVideoPath ?: "")
                    .put("avatarUrl", n.avatarUrl)
                    .put("inGallery", n.inGallery)
                    .put("aiSummary", n.aiSummary ?: "")
                    .put("aiSummaryTs", n.aiSummaryTs)
                    .put("comments", JSONArray().apply {
                        n.comments.forEach { c ->
                            put(JSONObject().apply {
                                put("nickname", c.nickname)
                                put("avatar", c.avatar)
                                put("content", c.content)
                                put("likes", c.likes)
                                put("subs", JSONArray().apply {
                                    c.subComments.forEach { sc ->
                                        put(JSONObject().apply {
                                            put("nickname", sc.nickname)
                                            put("avatar", sc.avatar)
                                            put("content", sc.content)
                                            put("likes", sc.likes)
                                        })
                                    }
                                })
                            })
                        }
                    }),
            )
        }
        val tmp = java.io.File(file.path + ".tmp")
        tmp.writeText(arr.toString())
        // renameTo 在同一目录内是原子覆盖；失败兜底直写（极端情况下至少不丢数据）
        if (!tmp.renameTo(file)) {
            file.writeText(arr.toString())
            tmp.delete()
        }
    }

    companion object {
        fun fmtTime(ts: Long): String =
            SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(ts))

        /** 文件大小智能显示：不足 1MB 用 KB，否则一位小数 MB */
        fun fmtSize(bytes: Long): String =
            if (bytes >= 1024 * 1024)
                String.format(Locale.US, "%.1fMB", bytes / 1024f / 1024f)
            else "${bytes / 1024}KB"
    }
}
