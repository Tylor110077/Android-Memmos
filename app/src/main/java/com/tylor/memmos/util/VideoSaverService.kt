package com.tylor.memmos.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.tylor.memmos.MainActivity
import com.tylor.memmos.R
import com.tylor.memmos.data.ClipStore
import com.tylor.memmos.net.MediaDownloader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * 视频后台保存前台服务：抓取/详情页只负责启动，下载+入相册+写回剪藏库全部在后台跑，
 * 通知栏显示实时进度条（用户要求「后台进行 + 进度条表示」）；
 * 用户切走 App/关掉页面都不影响，完成/失败以通知收尾。
 * [progress]/[activeNoteId] 供 UI（详情页封面、抓取页）观察：progress=null 表空闲。
 */
class VideoSaverService : Service() {

    companion object {
        private const val CHANNEL = "memmos_media"
        private const val NOTIF_ID = 2001

        /** 当前下载进度 0..1；null=空闲 */
        val progress = MutableStateFlow<Float?>(null)

        /** 正在下载的剪藏 id（详情页据此判断是否是当前笔记在下） */
        val activeNoteId = MutableStateFlow<String?>(null)

        /** 幂等：同一视频已在下载时重复启动会被忽略 */
        fun start(ctx: Context, noteId: String) {
            ctx.startForegroundService(
                Intent(ctx, VideoSaverService::class.java)
                    .putExtra(EXTRA_ID, noteId),
            )
        }

        private const val EXTRA_ID = "id"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val nm get() = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getStringExtra(EXTRA_ID) ?: run { stopSelf(); return START_NOT_STICKY }
        if (progress.value != null) return START_NOT_STICKY // 已有下载任务在跑

        val note = ClipStore(this).load()
            .firstOrNull { it.id == id && !it.videoUrl.isNullOrBlank() }
            ?: run { stopSelf(); return START_NOT_STICKY }

        ensureChannel()
        progress.value = 0f
        activeNoteId.value = id
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notif("正在保存视频…", 0), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, notif("正在保存视频…", 0))
        }

        scope.launch {
            var ok = false
            // 进度单调不减：失败重试会从 0 重新累积，直接显示会 30%→22% 回退（用户反馈）
            var lastP = 0f
            val failMsg = runCatching {
                val f = MediaDownloader.downloadVideo(this@VideoSaverService, note) { p ->
                    val mono = if (p >= lastP) p else lastP
                    lastP = mono
                    progress.value = mono
                    nm.notify(NOTIF_ID, notif("正在保存视频…", (mono * 100).toInt()))
                }
                runCatching { MediaSaver.saveVideoToGallery(this@VideoSaverService, f) }
                val store = ClipStore(this@VideoSaverService)
                val list = store.load()
                val idx = list.indexOfFirst { it.id == note.id }
                if (idx >= 0) {
                    list[idx] = list[idx].copy(localVideoPath = f.absolutePath)
                    store.save(list)
                }
                ok = true
                null
            }.exceptionOrNull()?.message
            progress.value = null
            activeNoteId.value = null
            if (ok) {
                stopForeground(false) // 保留完成通知
                nm.notify(NOTIF_ID, notif("视频已保存到相册", null, done = true))
            } else {
                stopForeground(true)
                nm.notify(NOTIF_ID, notif("视频保存失败：${failMsg ?: "未知错误"}", null, done = true))
            }
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun notif(text: String, pct: Int?, done: Boolean = false): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val b = Notification.Builder(this, CHANNEL)
            buildNotif(b, text, pct, done, pi)
        } else {
            @Suppress("DEPRECATION")
            buildNotif(Notification.Builder(this), text, pct, done, pi)
        }
    }

    private fun buildNotif(
        b: Notification.Builder, text: String, pct: Int?, done: Boolean, pi: PendingIntent,
    ): Notification {
        b.setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Memmos")
            .setContentText(text)
            .setContentIntent(pi)
            .setOngoing(!done)
            .setAutoCancel(done)
        if (pct != null) b.setProgress(100, pct, false)
        return b.build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "媒体保存", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
