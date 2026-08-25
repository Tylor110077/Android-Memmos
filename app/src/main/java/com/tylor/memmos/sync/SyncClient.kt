package com.tylor.memmos.sync

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Obsidian memos-graph 同步服务的 HTTP 客户端。
 * 协议见插件端 src/sync/SyncServer.ts：/pair 换 token，其余带 X-Memmos-Token。
 */
class SyncClient(
    private val host: String,
    private val port: Int,
    private val token: String,
    /** 配对时返回的同步根目录（空 = 对端全库模式），上传路径计算用 */
    var rootFolder: String = "",
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    data class InvItem(val path: String, val sha256: String, val mtime: Long)

    private fun url(path: String) = "http://$host:$port$path"

    /** 配对：6 位码换长效 token（静态方法：尚未持有 token） */
    companion object {
        suspend fun pair(host: String, port: Int, code: String): PairResult = withContext(Dispatchers.IO) {
            val client = OkHttpClient()
            val resp = client.newCall(
                Request.Builder().url("http://$host:$port/pair?code=$code").build(),
            ).execute().use { r ->
                val body = r.body?.string().orEmpty()
                if (!r.isSuccessful) error(JSONObject(body).optString("error", "HTTP ${r.code}"))
                body
            }
            val o = JSONObject(resp)
            PairResult(token = o.getString("token"), folder = o.optString("folder"))
        }
    }

    data class PairResult(val token: String, val folder: String)

    private suspend fun call(path: String, method: String = "GET", body: String? = null): String =
        withContext(Dispatchers.IO) {
            android.util.Log.d("MemmosDbg", "call $method ${url(path)} bodyLen=${body?.length ?: 0}")
            val req = Request.Builder()
                .url(url(path))
                .header("X-Memmos-Token", token)
                .apply {
                    if (method == "POST") {
                        post((body ?: "").toRequestBody("application/json".toMediaType()))
                    }
                }
                .build()
            client.newCall(req).execute().use { r ->
                val text = r.body?.string().orEmpty()
                if (!r.isSuccessful) error(JSONObject(text).optString("error", "HTTP ${r.code}"))
                text
            }
        }

    suspend fun inventory(): List<InvItem> {
        val arr = JSONObject(call("/api/inventory")).getJSONArray("files")
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            InvItem(o.getString("path"), o.getString("sha256"), o.optLong("mtime"))
        }
    }

    suspend fun getFile(path: String): String = JSONObject(call("/api/file?path=$path")).getString("content")

    /** 原始响应（md 走 content 字段，二进制走 base64 字段，由调用方区分） */
    suspend fun getFileRaw(path: String): String = call("/api/file?path=$path")

    suspend fun postFile(path: String, content: String) {
        call("/api/file", "POST", JSONObject().put("path", path).put("content", content).toString())
    }

    /** 二进制上传（图片/视频等媒体文件，base64 传输） */
    suspend fun postBinary(path: String, bytes: ByteArray) {
        call("/api/binary", "POST", JSONObject().put("path", path).put("base64", Base64.encodeToString(bytes, Base64.NO_WRAP)).toString())
    }

    /** 二进制下载：返回 base64 字符串（空串=不存在） */
    suspend fun getBinary(path: String): String =
        JSONObject(call("/api/binary?path=$path")).optString("base64")
}

/** 配对信息与剪藏转换用的路径规则，统一放这里避免两处漂移 */
object SyncPrefs {
    private const val FILE = "memmos_sync"

    fun save(ctx: Context, host: String, port: Int, token: String, folder: String) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString("host", host)
            .putInt("port", port)
            .putString("token", token)
            .putString("folder", folder)
            .apply()
    }

    fun load(ctx: Context): SyncClient? {
        val p = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val host = p.getString("host", null) ?: return null
        val token = p.getString("token", null) ?: return null
        return SyncClient(host, p.getInt("port", 28422), token, p.getString("folder", "") ?: "")
    }

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().clear().apply()
    }

    data class PairInfo(val host: String, val port: Int, val folder: String)

    /** 配对信息（设置页状态展示用）；未配对返回 null */
    fun loadInfo(ctx: Context): PairInfo? {
        val p = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val host = p.getString("host", null) ?: return null
        return PairInfo(host, p.getInt("port", 28422), p.getString("folder", "") ?: "")
    }
}
