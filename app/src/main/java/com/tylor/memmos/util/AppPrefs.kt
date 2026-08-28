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

    /** AI 总结生成时机：0=剪藏完成后后台自动 1=点开帖子时生成 2=不生成（详情显示提醒） */
    fun aiSummaryMode(ctx: Context): Int =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt("aiSummaryMode", 0)

    fun setAiSummaryMode(ctx: Context, v: Int) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putInt("aiSummaryMode", v.coerceIn(0, 2)).apply()
    }

    /** AI 总结档位：full=原始总结 brief=极简 custom=自定义提示词（空则回退 full） */
    fun aiSummaryLevel(ctx: Context): String =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("aiSummaryLevel", "full").orEmpty()

    fun setAiSummaryLevel(ctx: Context, v: String) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString("aiSummaryLevel", if (v == "brief" || v == "custom") v else "full").apply()
    }

    /** 自定义总结提示词（档位=custom 时生效；只存本机） */
    fun aiCustomPrompt(ctx: Context): String =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("aiCustomPrompt", "").orEmpty()

    fun setAiCustomPrompt(ctx: Context, v: String) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString("aiCustomPrompt", v).apply()
    }

    /** 主题强调色：green=品牌绿（默认） purple=Obsidian 紫；全 App 主界面跟随 */
    fun themeColor(ctx: Context): String =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("themeColor", "green").orEmpty()

    fun setThemeColor(ctx: Context, v: String) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString("themeColor", if (v == "purple") "purple" else "green").apply()
    }

    /** Dots API Key（仅存本机；切勿写入日志/仓库） */
    fun aiApiKey(ctx: Context): String =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("aiApiKey", "").orEmpty()

    fun setAiApiKey(ctx: Context, v: String) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString("aiApiKey", v.trim()).apply()
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
