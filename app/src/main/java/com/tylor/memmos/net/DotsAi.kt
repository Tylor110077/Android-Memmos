package com.tylor.memmos.net

import com.tylor.memmos.data.ClipNote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Dots AI（dots3-note-prev）接入：OpenAI 兼容 /v1/chat/completions，
 * 认证用 `api-key` 请求头（文档明确：Key 不得进入前端代码/日志/公开仓库——只存本机偏好）。
 * 多模态：content 内容数组可混合 text / image_url / video_url（模型侧访问公网 URL，
 * 小红书 CDN 直链可直接食用）。非流式一次返回，控制 max_tokens 与关闭深度思考以提速。
 */
object DotsAi {

    private const val BASE = "https://note3-prev-api.askdiandian.com"
    private const val MODEL = "dots3-note-prev"
    private const val MAX_IMAGES = 4 // 图集只取前 N 张（封面+前几张），控制耗时与成本

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS) // 视频输入的 TTFT 可能很长（官方提醒）
        .build()

    /** 总结提示词：覆盖正文/评论区/图片/视频，输出结构化中文 Markdown */
    private const val SYSTEM_PROMPT = """你是一个专业的内容总结助手。用户会提供一条小红书笔记的完整素材：
正文、评论区、图片（可能多张）与视频。请综合所有素材，用中文输出一份 300 字以内的总结，包含：
1. 核心主题（一句话）
2. 作者的主要观点/内容要点（列表）
3. 图片与视频的内容简述（如果提供了）
4. 评论区有代表性的观点（如果提供了；无评论则说明）
输出格式：第一行 `## AI 总结`，然后按小段落与要点列表组织；只输出总结本身，不要额外解释。"""

    /** 生成笔记 AI 总结；失败返回 null（不抛给 UI，UI 有手动重试） */
    suspend fun summarize(apiKey: String, note: ClipNote): String? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext null
        val blocks = JSONArray()
        fun text(t: String) {
            blocks.put(JSONObject().put("type", "text").put("text", t))
        }

        val imgUrls = note.imageUrls.take(MAX_IMAGES).filter { it.startsWith("http") }
        text("标题：${note.title}")
        text("正文：\n${note.desc.take(4000)}")
        imgUrls.forEach { u ->
            blocks.put(
                JSONObject().put("type", "image_url")
                    .put("image_url", JSONObject().put("url", u).put("detail", "medium")),
            )
        }
        if (note.localVideoPath != null && note.videoUrl != null) {
            blocks.put(
                JSONObject().put("type", "video_url")
                    .put("video_url", JSONObject().put("url", note.videoUrl)),
            )
        } else if (note.videoUrl != null) {
            blocks.put(
                JSONObject().put("type", "video_url")
                    .put("video_url", JSONObject().put("url", note.videoUrl)),
            )
        }
        val commentsText = buildString {
            note.comments.forEach { c ->
                append("· ${c.nickname}：${c.content}")
                c.subComments.forEach { sc -> append("\n  ↳ ${sc.nickname}：${sc.content}") }
                append("\n")
            }
        }
        text("评论区（共 ${note.comments.size} 条）：\n${commentsText.take(3000)}")

        val body = JSONObject()
            .put("model", MODEL)
            .put("stream", false)
            .put("max_tokens", 2048)
            .put("chat_template_kwargs", JSONObject().put("enable_thinking", false))
            .put(
                "messages",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("role", "system")
                            .put("content", JSONArray().put(JSONObject().put("type", "text").put("text", SYSTEM_PROMPT))),
                    )
                    .put(JSONObject().put("role", "user").put("content", blocks)),
            )

        runCatching {
            val req = Request.Builder()
                .url("$BASE/v1/chat/completions")
                .header("Content-Type", "application/json")
                .header("api-key", apiKey)
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    android.util.Log.d("MemmosDbg", "dots ai http ${resp.code}")
                    return@withContext null
                }
                val root = JSONObject(resp.body?.string() ?: return@withContext null)
                root.getJSONArray("choices").optJSONObject(0)
                    ?.getJSONObject("message")?.optString("content")
                    ?.takeIf { it.isNotBlank() }
            }
        }.getOrNull()
    }
}
