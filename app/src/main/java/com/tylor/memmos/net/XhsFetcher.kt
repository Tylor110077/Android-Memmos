package com.tylor.memmos.net

import android.webkit.CookieManager
import com.tylor.memmos.data.ClipComment
import com.tylor.memmos.data.ClipNote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 小红书链接抓取：逻辑移植自 bnchiang96/xiaohongshu-importer（Obsidian 插件）——
 * 免登录方案：直接 GET 笔记页（短链由 OkHttp 自动跟随 302），
 * 从 HTML 的 window.__INITIAL_STATE__ 提取 noteDetailMap。
 * 页面无该数据时通常意味着触发风控或需要登录，报给上层提示。
 */
object XhsFetcher {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    // 桌面浏览器 UA：与 importer 的运行环境等价，页面会返回含 __INITIAL_STATE__ 的完整 HTML；
    // 同时是「模拟电脑」的公共入口（登录/抓取 WebView 与此保持一致，三处共用一份）
    const val DESKTOP_UA =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    /** 模拟真实浏览器请求头：只有 UA 的 OkHttp 请求易被风控（实测短链带错 Cookie 即 403） */
    private fun noteRequest(url: String, cookie: String): Request = Request.Builder().url(url)
        .header("User-Agent", DESKTOP_UA)
        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
        .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
        .header("sec-ch-ua", "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"120\", \"Google Chrome\";v=\"120\"")
        .header("sec-ch-ua-mobile", "?0")
        .header("sec-ch-ua-platform", "\"macOS\"")
        .header("Sec-Fetch-Site", "none")
        .header("Sec-Fetch-Mode", "navigate")
        .header("Sec-Fetch-User", "?1")
        .header("Sec-Fetch-Dest", "document")
        .header("Upgrade-Insecure-Requests", "1")
        .apply { if (cookie.isNotBlank()) header("Cookie", cookie) }
        .build()

    /**
     * 从 HTML 提取 window.__INITIAL_STATE__= 后的 JSON：从第一个 { 开始做**括号配平**，
     * 字符串内转义感知，遇到 } 配平即收——不依赖 </script> 边界。
     * 旧实现 (.*?)</script> 在登录态页面变体上会把 JSON 从字符串中间切断（实测）。
     */
    /** 字符串感知的方括号配平：从 JSON 文本中按 key 提取数组原文（绕过整段解析失败）。
     *  从 fromKey 之后开始找——避免命中推荐流里同名字段（imageList 在推荐数据里也存在，
     *  取第一个会拿到"别的帖子的图"，正是图序错乱来源） */
    private fun extractJsonArrayRaw(text: String, key: String, fromKey: String? = null): String {
        val from = if (fromKey != null) text.indexOf("\"$fromKey\"").let { if (it < 0) 0 else it } else 0
        val k = text.indexOf("\"$key\"", from)
        if (k < 0) return ""
        val start = text.indexOf('[', k)
        if (start < 0) return ""
        var depth = 0; var inStr = false; var esc = false
        for (i in start until text.length) {
            val c = text[i]
            when {
                inStr -> when {
                    esc -> esc = false
                    c == '\\' -> esc = true
                    c == '"' -> inStr = false
                }
                c == '"' -> inStr = true
                c == '[' -> depth++
                c == ']' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        return ""
    }

    private fun extractInitialState(html: String): String? {
        val marker = html.indexOf("window.__INITIAL_STATE__=")
        if (marker < 0) return null
        val start = html.indexOf('{', marker)
        if (start < 0) return null
        var depth = 0; var inStr = false; var esc = false
        for (i in start until html.length) {
            val c = html[i]
            when {
                inStr -> when {
                    esc -> esc = false
                    c == '\\' -> esc = true
                    c == '"' -> inStr = false
                }
                c == '"' -> inStr = true
                c == '{' -> depth++
                c == '}' -> {
                    depth--
                    if (depth == 0) return html.substring(start, i + 1)
                }
            }
        }
        return null
    }

    /** 短链预检：302 展开短链，返回最终 URL（非 xhslink 原样返回）；失败返回 null */
    suspend fun resolveShort(url: String): String? = withContext(Dispatchers.IO) {
        if (!url.contains("xhslink")) return@withContext url
        runCatching {
            client.newCall(noteRequest(url, "")).execute().use { it.request.url.toString() }
        }.getOrNull()
    }

    /** 从任意分享文本里提取笔记链接（短链或 discovery/explore 长链）
     *  短链实测两种形态：http://xhslink.com/aXXX 与 https://xhslink.cn/o/xxx（2026 手机 App 分享格式） */
    fun extractUrl(text: String): String? {
        Regex("""https?://xhslink\.(?:com|cn)/[^\s,，]+""").find(text)?.let { return it.value }
        Regex("""https://www\.xiaohongshu\.com/(?:discovery/item|explore)/[a-zA-Z0-9]+(?:\?[^\s,，]*)?""")
            .find(text)?.let { return it.value.replace("/explore/", "/discovery/item/") }
        return null
    }

    suspend fun fetch(shareText: String, maxComments: Int = 100): ClipNote = withContext(Dispatchers.IO) {
        val url = extractUrl(shareText) ?: error("未识别到小红书链接")
        // 登录态：WebView 登录后 Cookie 全局持久化，OkHttp 路线也带上。
        // 踩坑：Cookie 只发给 www.xiaohongshu.com 域——发给 xhslink.cn 短链域会被判
        // 为跨域 Cookie 异常，短链直接 403（实测 2026-08：视频字段全部丢失）。
        val cookie = runCatching {
            CookieManager.getInstance().getCookie("https://www.xiaohongshu.com")
        }.getOrNull().orEmpty()
        var finalUrl = url
        val html = if (url.contains("xhslink")) {
            // 1) 无 Cookie 跟随 302 到最终页（短链域与最终域分离，跨域请求浏览器行为）
            val r1 = client.newCall(noteRequest(url, "")).execute()
            finalUrl = r1.request.url.toString()
            val body1 = r1.body?.string() ?: ""
            r1.close()
            // 2) 最终页在小红书域且有 Cookie → 带 Cookie + 浏览器头重取登录态页面；
            //    失败回退首轮结果（首轮已含 __INITIAL_STATE__，视频等字段不依赖登录）
            if (cookie.isNotBlank() && finalUrl.contains("xiaohongshu.com")) {
                runCatching {
                    client.newCall(noteRequest(finalUrl, cookie)).execute().use { resp ->
                        if (resp.isSuccessful) resp.body?.string() ?: body1 else body1
                    }
                }.getOrDefault(body1)
            } else body1
        } else {
            client.newCall(noteRequest(url, cookie)).execute().use { resp ->
                if (!resp.isSuccessful) error("请求失败 HTTP ${resp.code}")
                resp.body?.string() ?: error("空响应")
            }
        }
        // 短链/失效防呆（用户实测：分享短链过期后会被重定向到别的帖子，抓回错误的
        // 标题/封面/内容）：最终页 URL 必须带 noteId；与 __INITIAL_STATE__ 的 note key
        // 不一致 = 已跳转到其它内容 → 明确报错，绝不保存错帖。
        val finalNoteId = Regex("""(?:discovery/item|explore)/([a-zA-Z0-9]+)""")
            .find(finalUrl)?.groupValues?.get(1)
        if (finalUrl.contains("xiaohongshu.com") && finalNoteId == null) {
            error("分享链接已失效：请在内侧「复制链接」获取原贴完整链接后再抓取")
        }

        android.util.Log.d(
            "MemmosDbg",
            "fetch page: url=${finalUrl.take(60)} len=${html.length} hasState=${extractInitialState(html)?.let { it.length }}",
        )
        // 页面里 JSON 字符串可能含 </script> 字面量（实测登录态页面变体在 50731 字符处截断），
        // 「.*?</script>」懒惰匹配会从字符串中间切开——改用花括号配平提取（字符串感知）
        val stateJson = extractInitialState(html)
        // 站点 JSON 里有裸 undefined，先洗成 null 才能进 org.json
        val root = stateJson?.let {
            runCatching { JSONObject(it.replace(Regex("""\bundefined\b"""), "null")) }.getOrNull()
        }
        if (root == null && stateJson != null) {
            android.util.Log.d("MemmosDbg", "state parse fail at len=${stateJson.length}") 
        }

        // 无 xsec_token 的裸链接：noteDetailMap 为空 → 走 HTML 兜底（importer 同款）；仍无内容则明确提示
        val noteObj = runCatching {
            val noteMap = root!!.getJSONObject("note").getJSONObject("noteDetailMap")
            val key = noteMap.keys().next()
            key to noteMap.getJSONObject(key).getJSONObject("note")
        }.getOrNull()

        if (noteObj?.first != null && finalNoteId != null && noteObj.first != finalNoteId) {
            error("链接已失效：分享链接过期并跳转到其它内容，请复制原贴完整链接重试")
        }
        // 冗余清理：最终页校验已保证 noteId 非空（非笔记页在上方报错），hash 兜底/url 正则回退不再可达
        val noteId = finalNoteId!!

        // 整段 stateJson 解析失败（部分变体页）：绕过整段，直接按 key 配平提取 imageList
        // —— 图集顺序以 imageList 为准（DOM 轮播预加载顺序是 2..10,1，会造成"首尾错位"）
        val stateForImages = stateJson ?: ""
        val fallbackImgs = if (noteObj == null) runCatching {
            val raw = extractJsonArrayRaw(stateForImages, "imageList", fromKey = "noteDetailMap")
            if (raw.isBlank()) emptyList()
            else JSONArray(raw).let { a ->
                List(a.length()) { i ->
                    val o = a.optJSONObject(i) ?: return@let emptyList<String>()
                    o.optString("urlDefault").takeIf { it.startsWith("http") }
                        ?.let { if (it.startsWith("http://")) "https://" + it.substring(7) else it }
                        ?: ""
                }.filter { it.isNotEmpty() }
            }
        }.getOrDefault(emptyList())
            .also { if (it.isNotEmpty()) android.util.Log.d("MemmosDbg", "imgList fallback: n=${it.size} first=${it.first().substringAfterLast('/').take(20)}") }
        } else emptyList()

        if (noteObj == null) {
            val descFallback = Regex("""<div id="detail-desc" class="desc">([\s\S]*?)</div>""").find(html)
                ?.groupValues?.get(1)?.replace(Regex("<[^>]+>"), "")?.replace("[话题]", "")?.trim().orEmpty()
            val ogImage = Regex("""<meta name="og:image" content="([^"]+)"""").find(html)?.groupValues?.get(1).orEmpty()
            if (descFallback.isBlank() && ogImage.isBlank()) {
                error("链接缺少访问令牌(xsec_token)，请从小红书 App「分享」复制完整链接")
            }
            return@withContext ClipNote(
                id = noteId,
                title = Regex("""<title>(.*?)</title>""").find(html)?.groupValues?.get(1)
                    ?.replace(" - 小红书", "").orEmpty().ifBlank { "未命名笔记" },
                desc = descFallback,
                author = "",
                tags = emptyList(),
                imageUrls = when {
                    fallbackImgs.isNotEmpty() -> fallbackImgs // imageList 顺序（权威）
                    ogImage.startsWith("http") -> listOf(ogImage)
                    else -> emptyList()
                },
                videoUrl = null,
                type = "normal",
                pageUrl = finalUrl, // 短链已展开：落库/上传一律用长链（短链会失效）
                clippedAt = System.currentTimeMillis(),
            )
        }

        val note = noteObj.second

        val title = note.optString("title").ifBlank {
            Regex("""<title>(.*?)</title>""").find(html)?.groupValues?.get(1)
                ?.replace(" - 小红书", "").orEmpty().ifBlank { "未命名笔记" }
        }
        // 正文清洗：移除话题标记（#xxx# 成对与 #xxx 单格式），标签已在 tags 字段单独展示
        val desc = note.optString("desc")
            .replace("[话题]", "")
            .replace(Regex("#[^#\\s]{1,40}#"), " ")
            .replace(Regex("#[^#\\s]{1,40}(?=\\s|$)"), " ")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
        // 图片/视频封面：urlDefault 直接给（图文笔记）；视频笔记 imageList[].url 常为空，
        // 真图在 infoList[]（scene=WB_DFT/WB_PRV）——按 WB_DFT 优先；再无则用 video.image
        val images = runCatching {
            val arr = note.optJSONArray("imageList") ?: JSONArray()
            val imgs = List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                val dft = o.optString("urlDefault").takeIf { it.startsWith("http") }
                if (dft != null) return@List dft
                runCatching {
                    val info = o.optJSONArray("infoList") ?: JSONArray()
                    val urls = List(info.length()) { j -> info.getJSONObject(j) }
                    urls.sortedByDescending { it.optString("imageScene") == "WB_DFT" }
                        .firstOrNull { it.optString("url").startsWith("http") }
                        ?.optString("url").orEmpty()
                }.getOrDefault("")
            }.filter { it.startsWith("http") }
            if (imgs.isNotEmpty()) imgs else {
                // 视频笔记封面兜底：video.image（og 图/封面直链）
                val cover = note.optJSONObject("video")?.optString("image").orEmpty()
                if (cover.startsWith("http")) listOf(cover) else emptyList()
            }
        }.getOrDefault(emptyList())
        // 视频流字段为驼峰命名（h264[0].masterUrl，失败回退 h265，同 importer）
        val video = runCatching {
            val streams = note.optJSONObject("video")?.optJSONObject("media")?.optJSONObject("stream")
            val pick = (streams?.optJSONArray("h264") ?: streams?.optJSONArray("h265"))?.optJSONObject(0)
            pick?.optString("masterUrl")
        }.getOrNull()?.takeIf { it.startsWith("http") }
        val tags = runCatching {
            val arr = note.optJSONArray("tagList") ?: JSONArray()
            List(arr.length()) { arr.getJSONObject(it).optString("name") }.filter { it.isNotBlank() }
        }.getOrDefault(emptyList())
        val user = runCatching { note.optJSONObject("user") }.getOrNull()
        val author = user?.optString("nickname").orEmpty()
        // 头像：实测页面字段为 user.avatar（sns-avatar-qc.xhscdn.com）；imageb/image 为旧版兜底
        val avatar = user?.optString("avatar").orEmpty()
            .ifBlank { user?.optString("imageb").orEmpty().ifBlank { user?.optString("image").orEmpty() } }
            .takeIf { it.startsWith("http") } ?: ""

        // 评论：页面 __INITIAL_STATE__ 自带前若干条（免签名）；更多页需签名接口（Phase 3）
        val comments = if (maxComments > 0) parseComments(note, maxComments) else emptyList()

        ClipNote(
            id = noteId,
            title = title,
            desc = desc,
            author = author,
            avatarUrl = avatar,
            comments = comments,
            tags = tags,
            imageUrls = images,
            videoUrl = video,
            type = note.optString("type"),
            pageUrl = finalUrl, // 短链已展开：落库/上传一律用长链（短链会失效）
            clippedAt = System.currentTimeMillis(),
        )
    }

    /**
     * 解析页面内嵌评论（commentData 为主，comments 兜底），主/子评论合并计数，
     * 总条数超 limit 截断。字段按小红书页面实际结构宽松解析。
     */
    private fun parseComments(note: JSONObject, limit: Int): List<ClipComment> {
        val out = mutableListOf<ClipComment>()
        var count = 0
        val arr = note.optJSONArray("commentData")
            ?: note.optJSONArray("comments")
            ?: return out
        fun readOne(c: JSONObject): ClipComment {
            val u = c.optJSONObject("userInfo")
            return ClipComment(
                nickname = u?.optString("nickname").orEmpty(),
                avatar = (u?.optString("imageb").orEmpty().ifBlank { u?.optString("image").orEmpty() }),
                content = c.optString("content"),
                likes = c.optInt("likeCount"),
            )
        }
        for (i in 0 until arr.length()) {
            if (count >= limit) break
            val c = arr.optJSONObject(i) ?: continue
            val main = readOne(c)
            val subs = mutableListOf<ClipComment>()
            val subArr = c.optJSONObject("subComments")?.optJSONArray("comments")
            if (subArr != null) {
                for (j in 0 until subArr.length()) {
                    if (count >= limit) break
                    val sc = subArr.optJSONObject(j) ?: continue
                    subs.add(readOne(sc))
                    count++
                }
            }
            out.add(main.copy(subComments = subs))
            count++
        }
        return out
    }
}