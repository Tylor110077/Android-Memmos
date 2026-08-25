package com.tylor.memmos.net

import android.content.Context
import com.tylor.memmos.data.ClipNote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/** 媒体下载（视频落盘） */
object MediaDownloader {

    private val client = OkHttpClient()

    fun localVideoFile(ctx: Context, note: ClipNote): File =
        File(File(ctx.filesDir, "media").apply { mkdirs() }, "${note.id}.mp4")

    /** 下载任意 URL 的字节（图片等，带 Referer 防盗链头） */
    suspend fun downloadBytes(url: String): ByteArray = withContext(Dispatchers.IO) {
        client.newCall(
            Request.Builder().url(url).header("Referer", "https://www.xiaohongshu.com/").build(),
        ).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}")
            r.body!!.bytes()
        }
    }

    /**
     * 视频流式落盘到 filesDir/media/{id}.mp4（带 Referer 防盗链头）。
     * onProgress 0..1（contentLength 未知时可能不回调），供 UI 进度条/通知用。
     */
    suspend fun downloadVideo(
        ctx: Context,
        note: ClipNote,
        onProgress: ((Float) -> Unit)? = null,
    ): File = withContext(Dispatchers.IO) {
        val out = localVideoFile(ctx, note)
        client.newCall(
            Request.Builder().url(note.videoUrl!!).header("Referer", "https://www.xiaohongshu.com/").build(),
        ).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            val body = resp.body!!
            val len = body.contentLength()
            body.byteStream().use { input ->
                out.outputStream().use { fos ->
                    val buf = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buf)
                        if (read <= 0) break
                        fos.write(buf, 0, read)
                        total += read
                        if (len > 0) onProgress?.invoke((total.toFloat() / len).coerceIn(0f, 1f))
                    }
                }
            }
        }
        out
    }
}
