package com.tylor.memmos.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.AnimationUtils
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import android.animation.ValueAnimator
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import com.tylor.memmos.R
import com.tylor.memmos.ui.EdgeTab
import com.tylor.memmos.ui.TabEdge
import com.tylor.memmos.ui.clips.ClipDetailActivity
import com.tylor.memmos.ui.fetch.ClipboardBridgeActivity
import com.tylor.memmos.ui.fetch.XhsCaptureService
import com.tylor.memmos.MainActivity
import com.tylor.memmos.net.XhsFetcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 悬浮窗前台服务：滑块窗口 + 面板窗口，均用 TYPE_APPLICATION_OVERLAY 叠在任意 App 之上。
 *
 * 触摸约定（README §3）：单击滑块展开面板；位移超过系统 touch slop 进入拖拽，
 * 松手吸附最近边缘。滑块窗口只占自身热区大小——窗口外的触摸天然穿透到下层 App。
 */
class FloatingService : Service() {

    companion object {
        /** MainActivity 观察启停状态 */
        val running = MutableStateFlow(false)
        private const val CHANNEL_ID = "memmos_overlay"
        private const val NOTIF_ID = 1001
        /** 面板占屏宽比例（其余穿透给宿主 App） */
        const val PANEL_WIDTH_RATIO = 0.86f
    }

    private lateinit var wm: WindowManager
    private val main = Handler(Looper.getMainLooper())
    private val model = OverlayModel()

    private var tabView: ComposeView? = null
    private var tabOwner: OverlayLifecycleOwner? = null
    private lateinit var tabLp: WindowManager.LayoutParams

    private var panelView: ComposeView? = null
    private var panelOwner: OverlayLifecycleOwner? = null
    private lateinit var panelLp: WindowManager.LayoutParams
    /** 面板收放动画/拖动中：防止重入与误触发 */
    private var panelBusy = false
    /** 面板拖拽手势起点（窗口 x）：松手判定据此区分「从展开位往回拖」vs「从隐藏位拉出」 */
    private var dragStartX = 0f

    /**
     * 返回拦截窗：面板打开时存在的全屏透明可聚焦窗口（FLAG_NOT_TOUCHABLE → 触摸全穿透）。
     * 作用：系统返回键派发给焦点窗口（本窗），捕获 BACK 关闭面板。
     * 限制：手势导航的边缘返回是系统手势不走按键，那种情况用左滑/点滑块关闭。
     */
    private var backInterceptor: View? = null

    private var anim: ValueAnimator? = null

    /** onChange 节流器：快速拖滑杆时窗口几何（尺寸/位置）更新降到 25fps——
     *  每帧一次 updateViewLayout 在 ColorOS 等系统上仍会高频重排=屏闪（用户反馈） */
    private var applyPending = false
    private val applyOnChange = Runnable {
        applyPending = false
        applyScaleToTabWindow()
        applyRestingPosition(animated = false)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        // 位置/大小变化都走这里：重算热区尺寸（scale 影响视觉与触控）+ 停靠位置。
        // 拖动是连续变化：40ms 合并一次（执行时读最新值），仍跟手且大幅降低窗口重排频率
        model.onChange = {
            if (!applyPending) {
                applyPending = true
                main.postDelayed(applyOnChange, 40L)
            }
        }
    }

    /** 触控热区（用户要求判定范围比显示更大、更容易触发）：可视条 5-36dp，
     *  宽度侧另留 ≥40dp、长度侧 ≥24dp，且整体 ≥56dp，余量全部留在屏幕内侧 */
    private fun hotW(v: Float) = maxOf(56f, v + 40f)
    private fun hotH(v: Float) = maxOf(56f, v + 24f)

    /** 宽/长滑块生效：按当前 barWidth/barLength 重算滑块窗口热区（视觉+触控同步） */
    private fun applyScaleToTabWindow() {
        if (tabView == null || !::tabLp.isInitialized) return
        val w = model.barWidth.value
        val l = model.barLength.value
        val newW = dp(hotW(w)).roundToInt()
        val newH = dp(hotH(l)).roundToInt()
        if (tabLp.width == newW && tabLp.height == newH) return // 尺寸没变跳过，减少无谓重排
        tabLp.width = newW
        tabLp.height = newH
        runCatching { wm.updateViewLayout(tabView, tabLp) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundCompat()
        showTab()
        running.value = true
        return START_STICKY
    }

    override fun onDestroy() {
        removePanel(animated = false)
        removeTab()
        removeBackInterceptor()
        anim?.cancel()
        running.value = false
        super.onDestroy()
    }

    /* ───────────── 滑块窗口 ───────────── */

    private fun showTab() {
        if (tabView != null) return
        // 视觉宽/长独立设定（默认 20×88dp）；触控余量（≥40/24dp、整体 ≥56dp）全部留在屏幕内侧
        val hotWdp = hotW(model.barWidth.value)
        val hotHdp = hotH(model.barLength.value)
        tabLp = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            gravity = Gravity.TOP or Gravity.START
            width = dp(hotWdp).roundToInt()
            height = dp(hotHdp).roundToInt()
        }
        // addView 前先落位（此时 tabView 尚未挂上，restingCoords 直接读 lp 尺寸）
        val (ix, iy) = restingCoords()
        tabLp.x = ix; tabLp.y = iy
        tabView = ComposeView(this).apply {
            tabOwner = setOverlayContent { TabContent(model) }
            setOnTouchListener(TabTouch())
        }
        wm.addView(tabView, tabLp)
    }

    private fun removeTab() {
        tabView?.let { v ->
            try { wm.removeView(v) } catch (_: Exception) {}
            v.disposeComposition()
        }
        tabOwner?.destroy()
        tabView = null; tabOwner = null
    }

    @Composable
    private fun TabContent(model: OverlayModel) {
        val bw by model.barWidth
        val bl by model.barLength
        val a by model.opacity
        val d by model.dragging
        val e by model.edge
        val c by model.barColor
        // 视觉贴向屏幕边一侧，触控余量留在内侧
        val align = when (e) {
            TabEdge.LEFT -> Alignment.CenterStart
            TabEdge.RIGHT -> Alignment.CenterEnd
            TabEdge.TOP -> Alignment.TopCenter
            TabEdge.BOTTOM -> Alignment.BottomCenter
        }
        Box(
            Modifier
                .fillMaxSize()
                .size(hotW(bw).dp, hotH(bl).dp),
            contentAlignment = align,
        ) {
            EdgeTab(
                edge = e, barWidth = bw, barLength = bl, alpha = a, color = c, dragging = d,
                active = panelView != null, // 面板开着时浮条全透明可见，静止态更低调
            )
        }
    }

    /** 滑块停靠坐标：由 edge+frac 推出窗口 x/y（全为屏幕像素） */
    /** 浮条拖拽换边跟手更新节流（同面板策略：坐标即时写、窗口更新 32ms 合并） */
    private var tabXPending = false
    private val tabXApply = Runnable {
        tabXPending = false
        val v = tabView ?: return@Runnable
        runCatching { wm.updateViewLayout(v, tabLp) }
    }

    private fun throttledTabApply() {
        if (!tabXPending) {
            tabXPending = true
            main.postDelayed(tabXApply, 32L)
        }
    }

    private fun restingCoords(): Pair<Int, Int> {
        val (w, h) = screen()
        val tw = tabLp.width; val th = tabLp.height
        return when (model.edge.value) {
            TabEdge.LEFT -> 0 to ((h - th) * model.frac.value).roundToInt()
            TabEdge.RIGHT -> (w - tw) to ((h - th) * model.frac.value).roundToInt()
            TabEdge.TOP -> ((w - tw) * model.frac.value).roundToInt() to 0
            TabEdge.BOTTOM -> ((w - tw) * model.frac.value).roundToInt() to (h - th)
        }
    }

    private fun applyRestingPosition(animated: Boolean) {
        if (!::tabLp.isInitialized || tabView == null) return
        val (tx, ty) = restingCoords()
        animateTabTo(tx, ty, animated)
    }

    private fun animateTabTo(tx: Int, ty: Int, animated: Boolean) {
        if (tabView == null) return
        if (!animated) {
            if (tabLp.x == tx && tabLp.y == ty) return // 位置没变跳过
            tabLp.x = tx; tabLp.y = ty
            wm.updateViewLayout(tabView, tabLp)
            return
        }
        anim?.cancel()
        val sx = tabLp.x; val sy = tabLp.y
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 220
            interpolator = AnimationUtils.loadInterpolator(this@FloatingService, android.R.interpolator.fast_out_slow_in)
            addUpdateListener {
                val f = it.animatedValue as Float
                tabLp.x = (sx + (tx - sx) * f).roundToInt()
                tabLp.y = (sy + (ty - sy) * f).roundToInt()
                runCatching { wm.updateViewLayout(tabView, tabLp) }
            }
            start()
        }
    }

    /* ───────────── 滑块手势 ───────────── */

    /**
     * 滑块手势状态机（三手势，互斥判定）：
     * - 点击（位移 ≤ slop）→ 开/关面板
     * - 向屏幕内滑（左缘向右 / 右缘向左，|dx| > 80dp 且横向主导）→ 跟手打开面板，松手中点判定
     *   （该方向与系统返回手势冲突，滑块可视区已通过 systemGestureExclusion 排除系统手势）
     * - 长按（380ms 内未超 slop，触发时震动）→ 拖拽换边模式，松手吸附
     * 其余位移（向外滑/纵向大位移）→ 拖拽换边兜底
     */
    private inner class TabTouch : View.OnTouchListener {
        private var downX = 0f; private var downY = 0f
        private var baseX = 0; private var baseY = 0
        private var moved = false              // 拖拽换边中
        private var longPressFired = false     // 长按已触发（进入换边模式）
        private var panelDrag = false          // 向内滑开面板跟手中
        private var lastX = 0f
        private var lastT = 0L
        private val slop = ViewConfiguration.get(this@FloatingService).scaledTouchSlop
        // 用户反馈触发/关闭要更灵敏：12dp 已贴近系统 touch slop(~8dp)下限，
        // 再低会误触点击（点击/长按判定仍以 slop 为准，不受影响）
        private val openThreshold = dp(12f)
        private val longPressTimeout = 380L

        private val longPressRunnable = Runnable {
            if (!moved && !panelDrag) {
                longPressFired = true
                model.dragging.value = true
                tabView?.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            }
        }

        override fun onTouch(v: View, ev: MotionEvent): Boolean {
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    anim?.cancel()
                    downX = ev.rawX; downY = ev.rawY
                    baseX = tabLp.x; baseY = tabLp.y
                    moved = false; longPressFired = false; panelDrag = false
                    model.dragging.value = false
                    lastX = ev.rawX; lastT = System.currentTimeMillis()
                    tabXPending = false; main.removeCallbacks(tabXApply) // 新手势：清旧合并任务
                    v.handler.postDelayed(longPressRunnable, longPressTimeout)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - downX
                    val dy = ev.rawY - downY
                    if (!longPressFired && !panelDrag && (abs(dx) > slop || abs(dy) > slop)) {
                        v.handler.removeCallbacks(longPressRunnable)
                        val inward = (model.edge.value == TabEdge.LEFT && dx > 0) || (model.edge.value == TabEdge.RIGHT && dx < 0)
                        when {
                            inward && abs(dx) > abs(dy) && abs(dx) > openThreshold -> {
                                // 向屏幕内滑 → 跟手打开面板（触发瞬间震动反馈，用户要求）
                                panelDrag = true
                                model.dragging.value = false
                                if (panelView == null) showPanel(fromEdge = true)
                                v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                dragStartX = panelLp.x.toFloat() // 手势起点=隐藏位，松手按拉出比例判定
                                lastX = ev.rawX; lastT = System.currentTimeMillis()
                            }
                            else -> moved = true // 拖拽换边（含向外滑/纵向）
                        }
                    }
                    when {
                        longPressFired -> {
                            model.dragging.value = true
                            val (w, h) = screen()
                            tabLp.x = (baseX + dx).roundToInt().coerceIn(0, w - tabLp.width)
                            tabLp.y = (baseY + dy).roundToInt().coerceIn(0, h - tabLp.height)
                            throttledTabApply() // 同面板：32ms 合并窗口更新
                        }
                        panelDrag -> {
                            updatePanelX(ev.rawX - lastX)
                            lastX = ev.rawX
                            lastT = System.currentTimeMillis()
                        }                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.handler.removeCallbacks(longPressRunnable)
                    val totalDx = abs(ev.rawX - downX)
                    val totalDy = abs(ev.rawY - downY)
                    when {
                        longPressFired -> snapFromCurrent()
                        panelDrag -> {
                            val dt = (System.currentTimeMillis() - lastT).coerceAtLeast(16L)
                            val vel = (ev.rawX - lastX) / dt
                            releasePanel(vel)
                        }
                        totalDx <= slop && totalDy <= slop -> {
                            if (ev.action == MotionEvent.ACTION_UP) togglePanel()
                        }
                        moved -> snapFromCurrent()
                    }
                    model.dragging.value = false
                }
            }
            return true
        }
    }

    /** 松手：按当前中心点找最近边缘，回写模型并吸附过去 */
    private fun snapFromCurrent() {
        val (w, h) = screen()
        val cx = tabLp.x + tabLp.width / 2f
        val cy = tabLp.y + tabLp.height / 2f
        // 只保留左右贴边（用户要求）：拖拽换边仅在左右缘间吸附，忽略上下
        val nearest = if (cx < w - cx) TabEdge.LEFT else TabEdge.RIGHT
        model.setEdge(nearest)
        model.setFrac((cy - tabLp.height / 2f) / (h - tabLp.height))
    }

    /* ───────────── 面板内真实抓取入口 ───────────── */

    /**
     * 「抓取当前笔记」（用户要求：后台完成+不跳转+面板进度条）：
     * 剪贴板有链接 → 直接启动 XhsCaptureService（隐藏 WebView 全管线，面板/通知栏显示进度）；
     * 后台读剪贴板被系统拦截（Android 10+ 非前台 App）→ 拉起透明桥接页前台读取后即退出，
     * 用户无感。面板保持打开：进度条显示在面板上。
     */
    private fun tryCaptureCurrent() {
        val clip = readClipboard()
        if (XhsFetcher.extractUrl(clip) != null) {
            XhsCaptureService.start(this, clip)
        } else {
            runCatching {
                startActivity(
                    Intent(this, ClipboardBridgeActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.onFailure {
                android.widget.Toast.makeText(
                    this, "请先在小红书复制链接（分享→复制链接），再点抓取",
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    private fun readClipboard(): String = runCatching {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
    }.getOrDefault("")

    /* ───────────── 右缘手势条 ───────────── */


    /* ───────────── 面板窗口 ───────────── */

    private fun togglePanel() {
        if (panelView == null) showPanel(fromEdge = false) else removePanel(animated = true)
    }

    private fun showPanel(fromEdge: Boolean) {
        if (panelView != null || panelBusy) return
        val (w, _) = screen()
        val fromLeft = model.edge.value != TabEdge.RIGHT
        // 隐藏位必须按侧别：原来写死 -w，右缘跟手打开时面板落在左侧隐藏位、
        // 手指再向左拖 x 继续变负——面板永远不出现（右缘滑开失效的根因）
        val hiddenX = if (model.edge.value == TabEdge.RIGHT) w else -w
        if (!::panelLp.isInitialized || panelLp.width != w) {
            panelLp = WindowManager.LayoutParams().apply {
                type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                format = PixelFormat.TRANSLUCENT
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                gravity = Gravity.TOP or Gravity.START
                width = w
                height = WindowManager.LayoutParams.MATCH_PARENT
            }
        }
        panelLp.x = hiddenX
        panelView = ComposeView(this).apply {
            panelOwner = setOverlayContent {
                PanelHost(
                    model,
                    panelOnLeft = fromLeft,
                    onClose = { removePanel(animated = true) },
                    onCaptureCurrent = { tryCaptureCurrent() },
                    onOpenNote = { id ->
                        startActivity(
                            Intent(this@FloatingService, ClipDetailActivity::class.java)
                                .putExtra("id", id)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                    onDragStart = { panelDragBegin() },
                    onDrag = { updatePanelX(it) },
                    onRelease = { releasePanel(it) },
                )
            }
        }
        wm.addView(panelView, panelLp)
        showBackInterceptor()
        // 非跟手打开（点滑块）：从隐藏位滑入；跟手打开时坐标由手势驱动
        if (!fromEdge) animatePanelTo(0f, 320L)
        // 跟手模式：面板停在隐藏位，等手势 dx 拉出来（中间状态）
    }

    /** 全屏透明可聚焦窗口：只收返回键（NOT_TOUCHABLE → 触摸全穿透） */
    private fun showBackInterceptor() {
        if (backInterceptor != null) return
        val v = View(this).apply {
            focusable = View.FOCUSABLE
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == MotionEvent.ACTION_UP) {
                    removePanel(animated = true)
                    true
                } else false
            }
        }
        val lp = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            format = PixelFormat.TRANSLUCENT
            // 关键组合：可聚焦（收 BACK）+ 触摸全穿透（面板与宿主操作不受影响）
            flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            gravity = Gravity.TOP or Gravity.START
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
        }
        backInterceptor = v
        wm.addView(v, lp)
        v.post { v.requestFocus() }
    }

    private fun removeBackInterceptor() {
        backInterceptor?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        backInterceptor = null
    }

    /** 单手易用：记录面板拖拽手势起始位置（Compose 面板拖动与滑块跟手打开共用） */
    private fun panelDragBegin() {
        if (panelView != null) dragStartX = panelLp.x.toFloat()
    }

    /** 拖动跟手：增量移动面板窗口 x */
    /** 跟手移动节流：坐标即时写入 lp（松手判定读最新值不受影响），
     *  窗口 updateViewLayout 合并到 32ms 一次——ColorOS 上每帧移动 overlay 窗口=屏闪（用户反馈） */
    private var panelXPending = false
    private val panelXApply = Runnable {
        panelXPending = false
        val v = panelView ?: return@Runnable
        runCatching { wm.updateViewLayout(v, panelLp) }
    }

    private fun throttledPanelApply() {
        if (!panelXPending) {
            panelXPending = true
            main.postDelayed(panelXApply, 32L)
        }
    }

    private fun updatePanelX(delta: Float) {
        val v = panelView ?: return
        val (w, _) = screen()
        anim?.cancel()
        // 两侧对称的弹性余量：旧钳制 [-w-60, +60] 让右缘面板物理上拖不回（只能回弹 60dp）
        panelLp.x = (panelLp.x + delta).roundToInt().coerceIn(-w - dp(60f).toInt(), w + dp(60f).toInt())
        throttledPanelApply()
    }

    /**
     * 松手判定（用户反馈触发/关闭仍太难，再放宽）：
     * - 朝展开方向 >350px/s 轻甩 → 完成（更灵敏）
     * - 朝隐藏方向 >250px/s 轻甩 → 收回（更灵敏）
     * - 从展开位出发（收起）：拖回 ~10% 屏宽（至少 48dp）即收回
     * - 从隐藏位拉出（打开）：**触发（震动）即必开**（用户要求；仅明甩回才收回）
     */
    private fun releasePanel(vel: Float) {
        val v = panelView ?: return
        // 幂等防抖：收/放动画进行中忽略后续 release（Compose 手势取消与窗口移出会补发 release）
        if (panelBusy || anim?.isRunning == true) return
        val (w, _) = screen()
        val fromLeft = model.edge.value != TabEdge.RIGHT
        val hiddenX = if (fromLeft) -w.toFloat() else w.toFloat()
        // 速度方向按面板滑入方向归一：朝展开为正（px/ms）
        val velOpen = if (fromLeft) vel else -vel
        val closeDist = maxOf(w * 0.10f, dp(48f))
        // 打开判定：不再要求距离（用户要求"只要触发震动就一定打开"，openDist 已废弃）
        val dest: Float = when {
            velOpen > 0.35f -> 0f                      // 轻甩朝展开方向 → 完成
            velOpen < -0.25f -> hiddenX                // 轻甩朝隐藏方向 → 收回
            abs(dragStartX) < dp(2f) ->                // 手势从展开位出发 = 收起手势
                if (abs(panelLp.x - dragStartX) > closeDist) hiddenX else 0f
            else -> 0f // 从隐藏位拉出：触发过（有震动）就必打开，不弹回
        }
        if (dest == 0f) {
            animatePanelTo(0f, 260L)
        } else {
            animatePanelTo(hiddenX, 200L, thenRemove = true)
        }
    }

    /** 面板窗口 x 动画收尾；thenRemove=true 时动画结束移除窗口 */
    private fun animatePanelTo(target: Float, duration: Long, thenRemove: Boolean = false) {
        val v = panelView ?: return
        anim?.cancel()
        panelBusy = thenRemove
        val sx = panelLp.x.toFloat()
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            interpolator = AnimationUtils.loadInterpolator(this@FloatingService, android.R.interpolator.fast_out_slow_in)
            addUpdateListener {
                val f = it.animatedValue as Float
                panelLp.x = (sx + (target - sx) * f).roundToInt()
                runCatching { wm.updateViewLayout(v, panelLp) }
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: android.animation.Animator) {
                    if (thenRemove) {
                        removePanelWindow()
                    }
                }
            })
            start()
        }
    }

    private fun removePanel(animated: Boolean) {
        if (animated && panelView != null) {
            val (w, _) = screen()
            // 按面板所在侧决定滑出方向（返回键/点击关闭也沿本侧滑出）
            val hiddenX = if (model.edge.value == TabEdge.RIGHT) w.toFloat() else -w.toFloat()
            animatePanelTo(hiddenX, 240L, thenRemove = true)
            return
        }
        removePanelWindow()
    }

    private fun removePanelWindow() {
        android.util.Log.d("MemmosDbg", "removePanelWindow")
        panelView?.let { v ->
            try { wm.removeView(v) } catch (_: Exception) {}
            v.disposeComposition()
        }
        panelOwner?.destroy()
        panelView = null; panelOwner = null
        panelBusy = false
        removeBackInterceptor() // 焦点归还宿主
    }

    /* ───────────── 杂项 ───────────── */

    private fun dp(v: Float): Float = v * resources.displayMetrics.density

    private fun screen(): Pair<Int, Int> =
        resources.displayMetrics.let { it.widthPixels to it.heightPixels }

    private fun startForegroundCompat() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "悬浮窗", NotificationManager.IMPORTANCE_MIN),
            )
        }
        val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION") Notification.Builder(this)
        }
        b.setContentTitle("Memmos 悬浮窗运行中")
            .setContentText("轻点屏幕边缘的滑块开始捕捉")
            .setSmallIcon(R.drawable.ic_notif)
            .setOngoing(true)
        val n = b.build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }
}
