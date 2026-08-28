package com.tylor.memmos

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.tylor.memmos.overlay.FloatingService

/**
 * 全局 Application：统计前台 Activity 数——App 退到后台（按 Home/上滑回主界面）
 * 时自动收起悬浮面板（用户要求；浮条保留，面板不再盖住桌面）。
 * 延迟 100ms 复核避免旋转/重建 Activity 时的瞬时误判。
 */
class MemmosApp : Application() {
    private var refs = 0

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) { refs++ }

            override fun onActivityStopped(activity: Activity) {
                // 延迟为 0（用户要求：转屏等任何退后台都立即收起面板，无需误判保护）
                refs = (refs - 1).coerceAtLeast(0)
                if (refs == 0) FloatingService.collapseOnHome()
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}
