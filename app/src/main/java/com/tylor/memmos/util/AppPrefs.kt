package com.tylor.memmos.util

import android.content.Context

/** App 级偏好（非同步配置） */
object AppPrefs {
    private const val FILE = "memmos_app"

    /** 抓取到视频笔记后自动下载视频（默认开） */
    fun autoDownloadVideo(ctx: Context): Boolean =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean("autoDownloadVideo", true)

    fun setAutoDownloadVideo(ctx: Context, v: Boolean) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putBoolean("autoDownloadVideo", v).apply()
    }

    /** 抓取评论（默认开；实际条数受页面内嵌量限制） */
    fun fetchComments(ctx: Context): Boolean =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean("fetchComments", true)

    fun setFetchComments(ctx: Context, v: Boolean) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putBoolean("fetchComments", v).apply()
    }

    /** 评论条数上限（默认 100） */
    fun maxComments(ctx: Context): Int =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt("maxComments", 100)

    fun setMaxComments(ctx: Context, v: Int) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putInt("maxComments", v.coerceIn(0, 500)).apply()
    }

    /** 用户是否启动过悬浮窗服务（App 启动时自动恢复用） */
    fun serviceWanted(ctx: Context): Boolean =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean("serviceWanted", false)

    fun setServiceWanted(ctx: Context, v: Boolean) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putBoolean("serviceWanted", v).apply()
    }

    /** 个人资料名字：Obsidian 同步笔记的作者显示用（空=不覆盖） */
    fun profileName(ctx: Context): String =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("profileName", "").orEmpty()

    fun setProfileName(ctx: Context, v: String) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString("profileName", v).apply()
    }

    /** 个人头像（filesDir/profile_avatar 的绝对路径） */
    fun profileAvatar(ctx: Context): String =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("profileAvatar", "").orEmpty()

    fun setProfileAvatar(ctx: Context, v: String) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString("profileAvatar", v).apply()
    }
}
