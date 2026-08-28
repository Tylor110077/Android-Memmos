package com.tylor.memmos

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.tylor.memmos.overlay.FloatingService

/**
 * 全局 Application：统计前台 Activity 数——App 退到后台（按 Home/上滑回主界面）
 * 时自动收起悬浮面板（用户要求；浮条保留，面板不再盖住桌面）。
 * 延迟复核避免旋转/重建 Activity 时的瞬时误判。
 */
class MemmosApp : Application() {
    private val main = Handler(Looper.getMainLooper())
    private var refs = 0
    private val checkBackground = Runnable {
        if (refs == 0) FloatingService.collapseOnHome()
    }

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                refs++
                main.removeCallbacks(checkBackground)
            }

            override fun onActivityStopped(activity: Activity) {
                refs = (refs - 1).coerceAtLeast(0)
                main.postDelayed(checkBackground, 300)
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}
