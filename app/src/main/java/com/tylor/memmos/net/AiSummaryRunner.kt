package com.tylor.memmos.net

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.tylor.memmos.R
import com.tylor.memmos.data.ClipStore
import com.tylor.memmos.sync.SyncEngine
import com.tylor.memmos.sync.SyncPrefs
import com.tylor.memmos.ui.clips.ClipDetailActivity
import com.tylor.memmos.util.AppPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * AI 总结后台任务：详情页「生成」只负责触发——卡片协程跟着 Activity 走，退出页面即会被取消；
 * 实际生成跑在进程级 [scope]，用户离开页面后继续完成，结果写回剪藏库并广播 + 通知，
 * 重新打开详情时卡面直接展示（[running] 供恢复「生成中」状态，[lastOutcome] 供原地刷新）。
 * 同一篇笔记去重（生成中重复点击/自动+手动并发时忽略），进程级单例。
 */
object AiSummaryRunner {

    data class Task(val noteId: String, val title: String)

    data class Outcome(val noteId: String, val summary: String?, val at: Long)

    private const val CHANNEL = "memmos_ai"
    private const val NOTIF_ID = 2002

    /** 正在生成的任务（null=空闲）；卡面据 noteId 判断是否本笔记正在生成 */
    val running = MutableStateFlow<Task?>(null)

    /** 最近一次完成的结果；summary==null 视为失败，卡面按 noteId 消费后展示 */
    val lastOutcome = MutableStateFlow<Outcome?>(null)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 触发后台生成；同一篇已在生成时忽略 */
    fun start(ctx: Context, noteId: String) {
        scope.launch {
            val app = ctx.applicationContext
            val store = ClipStore(app)
            val note = store.load().firstOrNull { it.id == noteId } ?: return@launch
            if (running.value?.noteId == noteId) return@launch
            val key = AppPrefs.aiApiKey(app)
            if (key.isBlank()) {
                lastOutcome.value = Outcome(noteId, null, System.currentTimeMillis())
                return@launch
            }
            running.value = Task(noteId, note.title)
            val level = AppPrefs.aiSummaryLevel(app)
            val custom = if (level == "custom") AppPrefs.aiCustomPrompt(app).takeIf { it.isNotBlank() } else null
            val sum = DotsAi.summarize(key, note, brief = level == "brief", customPrompt = custom)
            if (sum != null) {
                runCatching {
                    val l = store.load()
                    val i = l.indexOfFirst { it.id == noteId }
                    if (i >= 0) {
                        l[i] = l[i].copy(aiSummary = sum, aiSummaryTs = System.currentTimeMillis())
                        store.save(l)
                    }
                }
            }
            running.value = null
            lastOutcome.value = Outcome(noteId, sum, System.currentTimeMillis())
            notifyDone(app, noteId, note.title, sum != null)
            if (sum != null) syncAfterGenerated(app, noteId)
        }
    }

    /** 正在自动推送的笔记（去重：避免手动+剪藏两个入口并发重复推同一篇） */
    private val syncingIds = ConcurrentHashMap.newKeySet<String>()

    /**
     * 新总结生成后的自动同步（用户要求「生成了新的 AI 总结，该同步的也要同步过去」）：
     * 已配对时把该篇剪藏推送到 Obsidian（md 引用 + AI summary 文件一并上传）；
     * 未配对/失败均静默——下次手动「立即同步」会兜底，不打扰用户。
     */
    fun syncAfterGenerated(ctx: Context, noteId: String) {
        if (!syncingIds.add(noteId)) return
        scope.launch {
            try {
                val app = ctx.applicationContext
                val client = SyncPrefs.load(app) ?: return@launch
                val note = ClipStore(app).load().firstOrNull { it.id == noteId } ?: return@launch
                SyncEngine.uploadNotes(app, client, listOf(note))
                android.util.Log.d("MemmosDbg", "ai summary auto sync ok: $noteId")
            } catch (e: Exception) {
                android.util.Log.d("MemmosDbg", "ai summary auto sync fail: ${e.message}")
            } finally {
                syncingIds.remove(noteId)
            }
        }
    }

    private fun notifyDone(ctx: Context, noteId: String, title: String, ok: Boolean) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "AI 总结", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val pi = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, ClipDetailActivity::class.java)
                .putExtra("id", noteId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(ctx, CHANNEL)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(ctx)
        }
        b.setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(if (ok) "AI 总结已生成" else "AI 总结生成失败")
            .setContentText(
                if (ok) "「$title」已生成总结，点按查看"
                else "「$title」生成失败，点按重试",
            )
            .setContentIntent(pi)
            .setAutoCancel(true)
        nm.notify(NOTIF_ID, b.build())
    }
}
