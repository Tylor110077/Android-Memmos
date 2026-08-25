package com.tylor.memmos.overlay

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * 让 Compose 内容能跑在 WindowManager 悬浮窗里：ComposeView 需要 Lifecycle/SavedState 宿主，
 * 普通服务窗口没有，这里提供一个最小桩（悬浮窗场景不需要 onSaveInstanceState）。
 */
class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    fun create() {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    fun resume() = lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

    fun destroy() = lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
}

/** 给 ComposeView 挂上生命周期并注入内容；返回 owner 以便移除窗口时销毁组合 */
fun ComposeView.setOverlayContent(content: @Composable () -> Unit): OverlayLifecycleOwner {
    val owner = OverlayLifecycleOwner()
    setViewTreeLifecycleOwner(owner)
    setViewTreeSavedStateRegistryOwner(owner)
    setContent(content)
    owner.create()
    owner.resume()
    return owner
}
