package com.tylor.memmos.util

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * 保存媒体到系统相册：
 * - API 29+：MediaStore 插入（Movies/Memmos、Pictures/Memmos），无需存储权限
 * - API 28-：公共目录 + MediaScanner 扫描（需 WRITE_EXTERNAL_STORAGE，manifest 已声明 maxSdk 28）
 * - 去重：同名文件已存在时跳过插入，SaveResult.existed=true 供 UI 提示「已在相册」
 */
object MediaSaver {

    private val client = OkHttpClient()

    /** uri=null 表示保存失败；existed=true 表示相册中已有同内容文件（未重复插入） */
    data class SaveResult(val uri: Uri?, val existed: Boolean)

    /** 已下载的视频文件 → 相册 Movies/Memmos */
    suspend fun saveVideoToGallery(ctx: Context, file: File): SaveResult = withContext(Dispatchers.IO) {
        val resolver = ctx.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val existing = queryExisting(
                resolver, file.name, "Movies/Memmos",
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            )
            if (existing != null) return@withContext SaveResult(existing, true)

            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Memmos")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values,
            ) ?: return@withContext SaveResult(null, false)
            resolver.openOutputStream(uri)?.use { fos -> file.inputStream().use { it.copyTo(fos) } }
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            SaveResult(uri, false)
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "Memmos")
            dir.mkdirs()
            val dest = File(dir, file.name)
            val existed = dest.exists()
            if (!existed) file.inputStream().use { input -> dest.outputStream().use { input.copyTo(it) } }
            MediaScannerConnection.scanFile(ctx, arrayOf(dest.absolutePath), arrayOf("video/mp4"), null)
            SaveResult(Uri.fromFile(dest), existed)
        }
    }

    /** 图片 URL → 下载 → 相册 Pictures/Memmos；uri=null 表示下载失败 */
    suspend fun saveImageToGallery(ctx: Context, url: String, baseName: String): SaveResult = withContext(Dispatchers.IO) {
        val bytes = runCatching {
            client.newCall(
                Request.Builder().url(url).header("Referer", "https://www.xiaohongshu.com/").build(),
            ).execute().use { r ->
                if (!r.isSuccessful) error("HTTP ${r.code}")
                r.body!!.bytes()
            }
        }.getOrNull() ?: return@withContext SaveResult(null, false)

        val ext = if (url.contains(".png", true)) "png" else "jpg"
        val name = "$baseName-${(url.hashCode().toLong() and 0xffffff).toString(16)}.$ext"
        val mime = if (ext == "png") "image/png" else "image/jpeg"

        val resolver = ctx.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val existing = queryExisting(
                resolver, name, "Pictures/Memmos",
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            )
            if (existing != null) return@withContext SaveResult(existing, true)

            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, mime)
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Memmos")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values,
            ) ?: return@withContext SaveResult(null, false)
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            SaveResult(uri, false)
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Memmos")
            dir.mkdirs()
            val dest = File(dir, name)
            val existed = dest.exists()
            if (!existed) dest.writeBytes(bytes)
            MediaScannerConnection.scanFile(ctx, arrayOf(dest.absolutePath), arrayOf(mime), null)
            SaveResult(Uri.fromFile(dest), existed)
        }
    }

    /** 按 显示名+相对路径 查询已存在的媒体条目 */
    private fun queryExisting(resolver: android.content.ContentResolver, name: String, relPath: String, uri: Uri): Uri? {
        resolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?",
            arrayOf(name, "$relPath/"),
            null,
        )?.use { c ->
            if (c.moveToFirst()) {
                val id = c.getLong(0)
                return ContentUris.withAppendedId(uri, id)
            }
        }
        return null
    }
}
