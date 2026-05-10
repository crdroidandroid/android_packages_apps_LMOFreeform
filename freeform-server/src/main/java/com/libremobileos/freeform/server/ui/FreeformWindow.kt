package com.libremobileos.freeform.server.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.PixelFormat
import android.graphics.SurfaceTexture
import android.os.Build
import android.os.Handler
import android.util.Slog
import android.view.Display
import android.view.DisplayInfo
import android.graphics.Matrix
import android.view.InputDevice
import android.view.GestureDetector
import android.view.IRotationWatcher
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.android.server.LocalServices
import com.android.server.wm.WindowManagerInternal
import com.libremobileos.freeform.ILMOFreeformDisplayCallback
import com.libremobileos.freeform.server.Debug.dlog
import com.libremobileos.freeform.server.LMOFreeformServiceHolder
import com.libremobileos.freeform.server.SystemServiceHolder
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class FreeformWindow(
    val handler: Handler,
    val context: Context,
    private val appConfig: AppConfig,
    val freeformConfig: FreeformConfig
): TextureView.SurfaceTextureListener, ILMOFreeformDisplayCallback.Stub(), View.OnTouchListener,
    WindowManagerInternal.DisplaySecureContentListener {

    var freeformTaskStackListener: FreeformTaskStackListener? = null
    val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val windowManagerInt = LocalServices.getService(WindowManagerInternal::class.java)
    val windowParams = WindowManager.LayoutParams()
    private val resourceHolder = RemoteResourceHolder(context, FREEFORM_PACKAGE)
    var freeformLayout: ViewGroup? = null
    var freeformRootView: ViewGroup? = null
    var freeformView: TextureView? = null
    private var topBarView: View? = null
    private var bottomBarView: View? = null
    var veilView: ViewGroup? = null
    private var displayId = Display.INVALID_DISPLAY
    var defaultDisplayWidth = context.resources.displayMetrics.widthPixels
    var defaultDisplayHeight = context.resources.displayMetrics.heightPixels
    var defaultDisplayRotation = context.display.rotation
    private val hangUpGestureListener = HangUpGestureListener(this)
    private val defaultDisplayInfo = DisplayInfo()
    private val destroyRunnable = Runnable { destroy("destroyRunnable", true) }
    
    private var appPackageName: String = ""
    private var appIcon: Drawable? = null
    private var isInitialized = false

    private val rotationWatcher = object : IRotationWatcher.Stub() {
        override fun onRotationChanged(rotation: Int) {
            dlog(TAG, "onRotationChanged($rotation)")
            defaultDisplayWidth = context.resources.displayMetrics.widthPixels
            defaultDisplayHeight = context.resources.displayMetrics.heightPixels
            defaultDisplayRotation = context.display.rotation
            measureSize()
            handler.post {
                changeOrientation()
                if (freeformConfig.isHangUp) toHangUp()
                else makeSureFreeformInScreen()
            }
            measureScale()
            LMOFreeformServiceHolder.resizeFreeform(
                this@FreeformWindow,
                freeformConfig.freeformWidth,
                freeformConfig.freeformHeight,
                freeformConfig.densityDpi
            )
            freeformView?.surfaceTexture?.setDefaultBufferSize(
                freeformConfig.freeformWidth,
                freeformConfig.freeformHeight
            )
        }
    }

    companion object {
        private const val TAG = "LMOFreeform/FreeformWindow"
        private const val FREEFORM_PACKAGE = "com.libremobileos.freeform"
        private const val FREEFORM_LAYOUT = "view_freeform"
        private const val WINDOW_DESTROY_WAIT_MS = 10000L
    }

    init {
        if (LMOFreeformServiceHolder.ping()) {
            Slog.i(TAG, "FreeformWindow init")
            extractPackageInfo()
            populateFreeformConfig()
            handler.post {
                if (addFreeformView()) {
                    isInitialized = true
                } else {
                    destroy("init:addFreeform failed")
                }
            }
        } else {
            Slog.e(TAG, "FreeformWindow init failed: service not running")
            destroy("init:service not running")
        }
    }

    override fun onDisplayPaused() {
        //NOT USED
    }

    override fun onDisplayResumed() {
        //NOT USED
    }

    override fun onDisplayStopped() {
        //NOT USED
    }

    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        dlog(TAG, "onSurfaceTextureAvailable width:$width height:$height")
        if (displayId < 0) {
            LMOFreeformServiceHolder.createDisplay(freeformConfig, appConfig, Surface(surfaceTexture), this)
        }
        surfaceTexture.setDefaultBufferSize(freeformConfig.freeformWidth, freeformConfig.freeformHeight)
    }

    override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        surfaceTexture.setDefaultBufferSize(freeformConfig.freeformWidth, freeformConfig.freeformHeight)
    }

    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
        return true
    }

    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {
        //NOT USED
    }

    override fun onDisplayAdd(displayId: Int) {
        Slog.i(TAG, "onDisplayAdd displayId=$displayId, $appConfig")
        handler.post {
            this.displayId = displayId
            freeformTaskStackListener = FreeformTaskStackListener(displayId, this)
            SystemServiceHolder.activityTaskManager.registerTaskStackListener(freeformTaskStackListener)
            if (appConfig.taskId != -1) {
                dlog(TAG, "moving taskId=${appConfig.taskId} to freeform display")
                freeformTaskStackListener!!.taskId = appConfig.taskId
                runCatching {
                    // TODO: find a new way for this since getTaskDescription was removed in fwb commit a7cae90a991e
                    // if (SystemServiceHolder.activityTaskManager.getTaskDescription(appConfig.taskId) == null) {
                    //     throw Exception("stale task")
                    // }
                    SystemServiceHolder.activityTaskManager.moveRootTaskToDisplay(appConfig.taskId, displayId)
                }
                .onFailure { e ->
                    Slog.e(TAG, "failed to move task ${appConfig.taskId}: $e, fallback to startApp")
                    startApp()
                }
            } else if (appConfig.userId == -100) {
                if (appConfig.pendingIntent == null) destroy("onDisplayAdd:userId=-100, but pendingIntent is null")
                else {
                    LMOFreeformServiceHolder.startPendingIntent(appConfig.pendingIntent, displayId)
                }
            } else {
                startApp()
            }

            val layout = freeformLayout
            if (layout == null) {
                Slog.e(TAG, "freeformLayout is null")
                destroy("onDisplayAdd:freeformLayout is null")
                return@post
            }
            val arrowBack = resourceHolder.getLayoutChildViewByTag<View>(layout, "arrowBack")
            if (null == arrowBack) {
                Slog.e(TAG, "right&rightScale view is null")
                destroy("onDisplayAdd:backView is null")
                return@post
            }
            arrowBack.setOnClickListener(RightViewClickListener(displayId))
        }
    }

    private fun startApp() {
        if (displayId == Display.INVALID_DISPLAY) {
            Slog.e(TAG, "cannot startApp: displayId not yet set!")
            return
        }
        if (LMOFreeformServiceHolder.startApp(context, appConfig, displayId).not())
            destroy("startApp failed")
    }

    override fun onDisplayHasSecureWindowOnScreenChanged(displayId: Int, hasSecureWindowOnScreen: Boolean) {
        if (displayId != this.displayId) return;
        dlog(TAG, "onDisplayHasSecureWindowOnScreenChanged: $hasSecureWindowOnScreen")
        windowParams.apply {
            flags = if (hasSecureWindowOnScreen) {
                flags or WindowManager.LayoutParams.FLAG_SECURE
            } else {
                flags xor WindowManager.LayoutParams.FLAG_SECURE
            }
        }
        handler.post {
            runCatching { windowManager.updateViewLayout(freeformLayout, windowParams) }
                .onFailure { Slog.e(TAG, "updateViewLayout failed: $it") }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(view: View, event: MotionEvent): Boolean {
        val newEvent = MotionEvent.obtain(event)
        val scaleMatrix = Matrix().apply {
            setScale(freeformConfig.scale, freeformConfig.scale)
        }
        newEvent.transform(scaleMatrix)
        newEvent.setSource(InputDevice.SOURCE_TOUCHSCREEN)
        LMOFreeformServiceHolder.touch(newEvent, displayId)
        newEvent.recycle()
        return true
    }

    /**
     * get freeform screen dimen / freeform view dimen
     */
    private fun populateFreeformConfig() {
        measureSize()
        measureScale()
        context.display.getDisplayInfo(defaultDisplayInfo)
        val maxRefreshRate = context.display.supportedModes
            .maxOfOrNull { it.refreshRate } ?: defaultDisplayInfo.refreshRate
        freeformConfig.apply {
            refreshRate = maxRefreshRate
            presentationDeadlineNanos = if (maxRefreshRate > 0f) {
                (1_000_000_000L / maxRefreshRate).toLong()
            } else {
                defaultDisplayInfo.presentationDeadlineNanos
            }
            dlog(TAG, "populateFreeformConfig: $this")
        }
    }

    fun measureSize() {
        val isPortrait = defaultDisplayRotation == Surface.ROTATION_0 ||
                defaultDisplayRotation == Surface.ROTATION_180
        freeformConfig.apply {
            height = (defaultDisplayHeight * (if (isPortrait) 0.4 else 0.7)).roundToInt()
            width = if (isPortrait) {
                (defaultDisplayWidth * 0.7).roundToInt()
            } else {
                // Landscape: increase width by 1.6x while preserving aspect ratio base
                val baseWidth = defaultDisplayHeight * defaultDisplayHeight / defaultDisplayWidth
                (baseWidth * 1.6).roundToInt()
            }
            dlog(TAG, "measureSize: isPortrait=$isPortrait width=$width height=$height")
        }
    }

    fun measureScale() {
        freeformConfig.apply {
            val widthScale = min(defaultDisplayWidth, defaultDisplayHeight) * 1.0f / min(width, height)
            val heightScale = max(defaultDisplayWidth, defaultDisplayHeight) * 1.0f / max(width, height)
            scale = min(widthScale, heightScale)
            freeformWidth = (width * scale).roundToInt()
            freeformHeight = (height * scale).roundToInt()
            dlog(TAG, "measureScale: $scale freeformWidth=$freeformWidth freeformHeight=$freeformHeight")
        }
    }

    /**
     * Called in system handler
     */
    @SuppressLint("WrongConstant")
    private fun addFreeformView(): Boolean {
        dlog(TAG, "addFreeformView")
        val tmpFreeformLayout = resourceHolder.getLayout(FREEFORM_LAYOUT)!! ?: return false
        freeformLayout = tmpFreeformLayout
        freeformRootView = resourceHolder.getLayoutChildViewByTag<FrameLayout>(tmpFreeformLayout, "freeform_root") ?: return false
        veilView = resourceHolder.getLayoutChildViewByTag<FrameLayout>(tmpFreeformLayout, "veilView") ?: return false
        topBarView = resourceHolder.getLayoutChildViewByTag(tmpFreeformLayout, "topBarView") ?: return false
        bottomBarView = resourceHolder.getLayoutChildViewByTag(tmpFreeformLayout, "bottomBarView") ?: return false
        val moveTouchListener = MoveTouchListener(this)
        topBarView?.setOnTouchListener(moveTouchListener)
        bottomBarView?.setOnTouchListener(moveTouchListener)
        val appIconView = resourceHolder.getLayoutChildViewByTag<ImageView>(tmpFreeformLayout, "appIcon")
        val packageNameView = resourceHolder.getLayoutChildViewByTag<TextView>(tmpFreeformLayout, "packageName")
        val maximizeView = resourceHolder.getLayoutChildViewByTag<View>(tmpFreeformLayout, "maximizeView")
        val minimizeView = resourceHolder.getLayoutChildViewByTag<View>(tmpFreeformLayout, "minimizeView")
        val pinView = resourceHolder.getLayoutChildViewByTag<View>(tmpFreeformLayout, "pinView")
        val leftScaleView = resourceHolder.getLayoutChildViewByTag<View>(tmpFreeformLayout, "leftScaleView")
        val rightScaleView = resourceHolder.getLayoutChildViewByTag<View>(tmpFreeformLayout, "rightScaleView")
        val veilAppIconView = resourceHolder.getLayoutChildViewByTag<ImageView>(tmpFreeformLayout, "veilAppIcon")
        if (null == minimizeView || null == leftScaleView || null == rightScaleView 
                || null == maximizeView || null == pinView || null == appIconView || null == packageNameView
                || null == veilAppIconView) {
            Slog.e(TAG, "left&leftScale&rightScale view is null")
            destroy("addFreeformView:left&leftScale&rightScale view is null")
            return false
        }
        veilAppIconView.setImageDrawable(appIcon)
        appIconView.setImageDrawable(appIcon)
        packageNameView.text = appPackageName
        minimizeView.setOnClickListener(LeftViewClickListener(this))
        maximizeView.setOnClickListener(MaximizeClickListener(this))
        pinView.setOnClickListener(PinClickListener(this))
        leftScaleView.setOnTouchListener(ScaleTouchListener(this, false))
        rightScaleView.setOnTouchListener(ScaleTouchListener(this))

        freeformView = FreeformTextureView(context).apply {
            setOnTouchListener(this@FreeformWindow)
            surfaceTextureListener = this@FreeformWindow
        }
        val rootView = freeformRootView ?: return false
        val view = freeformView ?: return false
        rootView.layoutParams = rootView.layoutParams.apply {
            width = freeformConfig.width
            height = freeformConfig.height
        }
        rootView.addView(view, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        windowParams.apply {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            flags = WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                    WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
            format = PixelFormat.RGBA_8888
            windowAnimations = android.R.style.Animation_Dialog
        }
        // Set initial positioning based on sidebar position
        setSidebarAwarePosition()
        
        runCatching {
            windowManager.addView(freeformLayout, windowParams)
            SystemServiceHolder.windowManager.watchRotation(rotationWatcher, Display.DEFAULT_DISPLAY)
            windowManagerInt.registerDisplaySecureContentListener(this)
        }.onFailure {
            Slog.e(TAG, "addView failed: $it")
            return false
        }
        return true
    }

    /**
     * Called in system handler
     */
    @SuppressLint("ClickableViewAccessibility")
    fun handleHangUp() {
        val rootView = freeformRootView ?: return
        val layout = freeformLayout ?: return
        val topBar = topBarView ?: return
        val bottomBar = bottomBarView ?: return
        val view = freeformView ?: return
        
        if (freeformConfig.isHangUp) {
            windowParams.apply {
                x = freeformConfig.notInHangUpX
                y = freeformConfig.notInHangUpY
                flags = flags or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
            }
            rootView.layoutParams.apply {
                width = freeformConfig.width
                height = freeformConfig.height
            }
            windowManager.updateViewLayout(layout, windowParams)
            topBar.visibility = View.VISIBLE
            bottomBar.visibility = View.VISIBLE
            freeformConfig.isHangUp = false
            view.setOnTouchListener(this)
        } else {
            freeformConfig.notInHangUpX = windowParams.x
            freeformConfig.notInHangUpY = windowParams.y
            toHangUp()
            topBar.visibility = View.GONE
            bottomBar.visibility = View.GONE
            freeformConfig.isHangUp = true
            val gestureDetector = GestureDetector(context, hangUpGestureListener)
            view.setOnTouchListener { _, event ->
                gestureDetector.onTouchEvent(event)
                if (event.action == MotionEvent.ACTION_UP) makeSureFreeformInScreen()
                true
            }
        }
    }

    /**
     * Called in system handler
     */
    fun toHangUp() {
        val rootView = freeformRootView ?: return
        val layout = freeformLayout ?: return
        
        windowParams.apply {
            x = (defaultDisplayWidth / 2 - freeformConfig.hangUpWidth / 2)
            y = -(defaultDisplayHeight / 2 - freeformConfig.hangUpHeight / 2)
            flags = flags xor WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
        }
        rootView.layoutParams = rootView.layoutParams.apply {
            width = freeformConfig.hangUpWidth
            height = freeformConfig.hangUpHeight
        }
        runCatching { windowManager.updateViewLayout(layout, windowParams) }.onFailure { Slog.e(TAG, "$it") }
    }

    /**
     * Called in uiHandler
     */
    fun makeSureFreeformInScreen() {
        val rootView = freeformRootView ?: return
        
        if (!freeformConfig.isHangUp) {
            val maxWidth = defaultDisplayWidth
            val maxHeight = (defaultDisplayHeight * 0.9).roundToInt()
            if (rootView.layoutParams.width > maxWidth || rootView.layoutParams.height > maxHeight) {
                rootView.layoutParams = rootView.layoutParams.apply {
                    width = min(rootView.width, maxWidth)
                    height = min(rootView.height, maxHeight)
                }
            }
        }
        if (windowParams.x < -(defaultDisplayWidth / 2)) FreeformAnimation.moveInScreenAnimator(windowParams.x, -(defaultDisplayWidth / 2), 300, true, this)
        else if (windowParams.x > (defaultDisplayWidth / 2)) FreeformAnimation.moveInScreenAnimator(windowParams.x, (defaultDisplayWidth / 2), 300, true, this)
        if (windowParams.y < -(defaultDisplayHeight / 2)) FreeformAnimation.moveInScreenAnimator(windowParams.y, -(defaultDisplayHeight / 2), 300, false, this)
        else if (windowParams.y > (defaultDisplayHeight / 2)) FreeformAnimation.moveInScreenAnimator(windowParams.y, (defaultDisplayHeight / 2), 300, false, this)
    }

    /**
     * Change freeform orientation
     * Called in system handler
     */
    fun changeOrientation() {
        val rootView = freeformRootView ?: return
        
        rootView.layoutParams = rootView.layoutParams.apply {
            width = if (freeformConfig.isHangUp) freeformConfig.hangUpWidth else freeformConfig.width
            height = if (freeformConfig.isHangUp) freeformConfig.hangUpHeight else freeformConfig.height
        }
    }

    fun getFreeformId(): String {
        return "${appConfig.packageName},${appConfig.activityName},${appConfig.userId}"
    }

    fun close() {
        dlog(TAG, "close()")
        runCatching {
            SystemServiceHolder.activityTaskManager.removeTask(freeformTaskStackListener!!.taskId)
            removeView()
        }.onFailure { exception ->
            Slog.e(TAG, "removeTask failed: ", exception)
            destroy("window.close() fallback")
        }
    }

    fun removeView(runDestroy: Boolean = true) {
        dlog(TAG, "removeView($runDestroy)")
        handler.removeCallbacks(destroyRunnable)
        
        freeformLayout?.let { layout ->
            handler.post {
                runCatching {
                    windowManager.removeViewImmediate(layout)
                    dlog(TAG, "removeView success")
                }.onFailure { exception ->
                    Slog.e(TAG, "removeView failed $exception")
                }
            }
        }
        
        // wait for onTaskRemoved(), but take it into our own hands in case its never triggered.
        if (runDestroy)
            handler.postDelayed(destroyRunnable, WINDOW_DESTROY_WAIT_MS)
    }

    fun destroy(callReason: String, shouldRemoveTask: Boolean = false) {
        Slog.i(TAG, "destroy ${getFreeformId()}, displayId=$displayId callReason: $callReason")
        
        if (!isInitialized) {
            Slog.w(TAG, "destroy called on uninitialized window")
            return
        }
        
        removeView(false)
        handler.removeCallbacks(destroyRunnable)
        
        freeformTaskStackListener?.let {
            SystemServiceHolder.activityTaskManager.unregisterTaskStackListener(it)
        }
        
        runCatching {
            SystemServiceHolder.windowManager.removeRotationWatcher(rotationWatcher)
        }.onFailure {
            Slog.e(TAG, "Failed to remove rotation watcher", it)
        }
        
        LMOFreeformServiceHolder.releaseFreeform(this)
        FreeformWindowManager.removeWindow(getFreeformId())
        windowManagerInt.unregisterDisplaySecureContentListener(this)
        
        freeformTaskStackListener?.taskId?.let {
            if (it != -1 && shouldRemoveTask) {
                Slog.i(TAG, "destroy: remove taskId $it again")
                runCatching { SystemServiceHolder.activityTaskManager.removeTask(it) }
            }
        }
        
        isInitialized = false
    }
    
    private fun extractPackageInfo() {
        try {
            val pm = context.packageManager
            val ai = pm.getApplicationInfo(appConfig.packageName, 0)
            appPackageName = pm.getApplicationLabel(ai).toString()
            appIcon = pm.getApplicationIcon(ai)
        } catch (e: Exception) {
            Slog.e(TAG, "Failed to retrieve app info: ${e.message}")
            appPackageName = ""
            appIcon = null
        }
    }
    
    private fun setSidebarAwarePosition() {
        val isLandscape = defaultDisplayWidth > defaultDisplayHeight
        val windowWidth = freeformConfig.width
        val margin = 80 // pixels from edge
        
        windowParams.apply {
            if (isLandscape) {
                // Landscape: position on left side of screen
                x = -(defaultDisplayWidth / 2) + (windowWidth / 2) + margin
                y = 0 // center vertically
            } else {
                // Portrait: center on screen
                x = 0 // center horizontally
                y = -(defaultDisplayHeight / 8) // slightly above center
            }
        }
        
        val mode = if (isLandscape) "landscape (left side)" else "portrait (center)"
        dlog(TAG, "Orientation-aware positioning: $mode, windowX=${windowParams.x}, windowY=${windowParams.y}, screenSize=${defaultDisplayWidth}x${defaultDisplayHeight}")
    }
    
    private fun setDefaultPosition() {
        // Use the same left side positioning as default
        setSidebarAwarePosition()
    }
}
