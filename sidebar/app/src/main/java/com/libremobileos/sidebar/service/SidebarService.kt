package com.libremobileos.sidebar.service

import android.annotation.SuppressLint
import android.app.IActivityManager
import android.app.Service
import android.app.UserSwitchObserver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.IBinder
import android.os.ServiceManager
import android.os.UserHandle
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import com.android.internal.policy.SystemBarUtils
import com.libremobileos.sidebar.R
import com.libremobileos.sidebar.app.SidebarApplication
import com.libremobileos.sidebar.utils.Logger

class SidebarService : Service(), SharedPreferences.OnSharedPreferenceChangeListener,
    GestureListener.Callback {
    private val logger = Logger(TAG)
    private lateinit var viewModel: ServiceViewModel
    private lateinit var windowManager: WindowManager
    private lateinit var iActivityManager: IActivityManager
    private lateinit var sidebarView: SidebarView
    private lateinit var sharedPrefs: SharedPreferences
    private var userId = 0
    private var serviceStarted = false
    private var showSideline = false
    private var isShowingSidebar = false
    private var isShowingSideline = false
    private var sidelinePositionX = 0
    private var sidelinePositionY = 0
    private var screenWidth = 0
    private var screenHeight = 0
    private val layoutParams = LayoutParams()
    private val handler = Handler()
    private val sideLineView by lazy {
        val gestureManager = MGestureManager(this@SidebarService, GestureListener(this@SidebarService))
        View(this).apply {
            background = AppCompatResources.getDrawable(this@SidebarService, R.drawable.ic_line)
            setOnTouchListener { _, event ->
                gestureManager.onTouchEvent(event)
                true
            }
        }
    }

    private val userSwitchObserver = object : UserSwitchObserver() {
        override fun onUserSwitchComplete(newUserId: Int) {
            logger.d("onUserSwitchComplete($userId)")
            if (!showSideline) return
            if (newUserId != userId) {
                removeView(force = true)
            } else {
                showView()
            }
        }
    }

    private val isPortrait: Boolean
        get() = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    private val offset: Int
        get() = if (isPortrait) OFFSET_PORTRAIT else OFFSET_LANDSCAPE

    companion object {
        private const val TAG = "SidebarService"
        private const val SIDELINE_WIDTH = 100
        //侧边条移动时的宽度
        private const val SIDELINE_MOVE_WIDTH = 200
        private const val DEFAULT_SIDELINE_HEIGHT = 200
        private const val OFFSET_PORTRAIT = 20
        private const val OFFSET_LANDSCAPE = 0

        const val SLIDER_TRANSPARENCY = "slider_transparency"
        const val SLIDER_LENGTH = "slider_length"

        //是否展示侧边条
        const val SIDELINE = "sideline"
        const val SIDELINE_POSITION_X = "sideline_position_x"
        const val SIDELINE_POSITION_Y_PORTRAIT = "sideline_position_y_portrait"
        const val SIDELINE_POSITION_Y_LANDSCAPE = "sideline_position_y_landscape"
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        userId = UserHandle.myUserId()
        if (userId != 0) {
            logger.d("not starting for non-system user $userId")
            stopSelf()
            return START_STICKY // this is just to skip the rest of the code
        }

        logger.d("starting service for user $userId")
        viewModel = ServiceViewModel(application)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        iActivityManager = IActivityManager.Stub.asInterface(ServiceManager.getService(Context.ACTIVITY_SERVICE))
        screenWidth = resources.displayMetrics.widthPixels
        screenHeight = resources.displayMetrics.heightPixels
        sharedPrefs = application.applicationContext.getSharedPreferences(SidebarApplication.CONFIG, Context.MODE_PRIVATE)
        sharedPrefs.registerOnSharedPreferenceChangeListener(this)
        iActivityManager.registerUserSwitchObserver(userSwitchObserver, TAG)
        serviceStarted = true

        sidebarView = SidebarView(this@SidebarService, viewModel, object : SidebarView.Callback {
            override fun onRemove() {
                logger.d("sidebar view removed")
                isShowingSidebar = false
                val masterEnabled = sharedPrefs.getBoolean(SIDELINE, false)
                val autoEnabled = sharedPrefs.getBoolean(SidebarMonitorService.KEY_AUTO_ENABLED_TEMP, false)
                val shouldShowService = masterEnabled || autoEnabled
                
                logger.d("onRemove - masterEnabled: $masterEnabled, autoEnabled: $autoEnabled, shouldShowService: $shouldShowService, isShowingSideline: $isShowingSideline")
                
                if (shouldShowService && isShowingSideline) {
                    sideLineView.animate().cancel()
                    animateShowSideline()
                }
            }
        })
        isShowingSidebar = false
        showSideline = sharedPrefs.getBoolean(SIDELINE, false)
        val autoEnabled = sharedPrefs.getBoolean(SidebarMonitorService.KEY_AUTO_ENABLED_TEMP, false)
        val shouldShow = showSideline || autoEnabled
        
        logger.d("screenWidth=$screenWidth screenHeight=$screenHeight showSideline=$showSideline autoEnabled=$autoEnabled")
        if (shouldShow) showView()
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        val newWidth = resources.displayMetrics.widthPixels
        val newHeight = resources.displayMetrics.heightPixels
        if (newWidth == screenWidth && newHeight == screenHeight) {
            return
        }
        screenWidth = newWidth
        screenHeight = newHeight
        logger.d("onConfigChanged: screenWidth=$screenWidth height=$screenHeight" +
                " isShowingSideline=$isShowingSideline isShowingSidebar=$isShowingSidebar")

        val masterEnabled = sharedPrefs.getBoolean(SIDELINE, false)
        val autoEnabled = sharedPrefs.getBoolean(SidebarMonitorService.KEY_AUTO_ENABLED_TEMP, false)
        val shouldShow = masterEnabled || autoEnabled
        
        if (shouldShow && isShowingSideline) {
            updateSidelinePosition()
        }
        if (isShowingSidebar) {
            sidebarView.updateSidebarPosition()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!serviceStarted) return
        sharedPrefs.unregisterOnSharedPreferenceChangeListener(this)
        iActivityManager.unregisterUserSwitchObserver(userSwitchObserver)
        removeView(force = true)
        viewModel.destroy()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        when (key) {
            SIDELINE -> {
                showSideline = sharedPrefs.getBoolean(SIDELINE, false)
                val autoEnabled = sharedPrefs.getBoolean(SidebarMonitorService.KEY_AUTO_ENABLED_TEMP, false)
                val shouldShow = showSideline || autoEnabled
                
                if (shouldShow) {
                    showView()
                } else {
                    removeView(force = true)
                }
            }
            SidebarMonitorService.KEY_AUTO_ENABLED_TEMP -> {
                val masterEnabled = sharedPrefs.getBoolean(SIDELINE, false)
                val autoEnabled = sharedPrefs.getBoolean(SidebarMonitorService.KEY_AUTO_ENABLED_TEMP, false)
                val shouldShow = masterEnabled || autoEnabled
                
                if (shouldShow) {
                    showView()
                } else {
                    removeView(force = true)
                }
            }
            SLIDER_TRANSPARENCY -> {
                if (isShowingSideline) {
                    val transparency = sharedPrefs.getFloat(SLIDER_TRANSPARENCY, 1.0f)
                    sideLineView.alpha = transparency
                }
            }
            SLIDER_LENGTH, SIDELINE_POSITION_X -> {
                if (isShowingSideline) {
                    layoutParams.height = getSliderLength()
                    updateSidelinePosition()
                    updateViewLayout()
                }
            }
        }
    }

    override fun showSidebar() {
        logger.d("showSidebar")
        sidebarView.showView()
        isShowingSidebar = true
        animateHideSideline()
    }

    override fun beginMoveSideline() {
        logger.d("beginMoveSideline")
        layoutParams.apply {
            width = SIDELINE_MOVE_WIDTH
        }
        updateViewLayout()
    }

    /**
     * @param xChanged x轴变化
     * @param yChanged y轴变化
     * @param positionX 触摸的x轴绝对位置。用来判断是否需要变化侧边条展示位置
     * @param positionY 触摸的y轴绝对位置
     */
    override fun moveSideline(xChanged: Int, yChanged: Int, positionX: Int, positionY: Int) {
        logger.d("moveSideline xChanged=$xChanged yChanged=$yChanged x=$positionX y=$positionY")
        sidelinePositionX = if (positionX > screenWidth / 2) 1 else -1
        layoutParams.apply {
            x = sidelinePositionX * (screenWidth / 2 - offset)
            y = layoutParams.y + yChanged
        }
        updateViewLayout()
    }

    override fun endMoveSideline() {
        logger.d("endMoveSideline")
        layoutParams.apply {
            width = SIDELINE_WIDTH
            y = constrainY(y)
        }
        updateViewLayout()
        setIntSp(SIDELINE_POSITION_X, sidelinePositionX)
        if (isPortrait) {
            setIntSp(SIDELINE_POSITION_Y_PORTRAIT, layoutParams.y)
        } else {
            setIntSp(SIDELINE_POSITION_Y_LANDSCAPE, layoutParams.y)
        }
    }

    private fun constrainY(y: Int): Int {
        // Avoid moving sideline into statusbar or navbar region
        val sbHeight = SystemBarUtils.getStatusBarHeight(this)
        val navbarHeight = if (isPortrait) {
            resources.getDimensionPixelSize(com.android.internal.R.dimen.navigation_bar_height)
        } else 0

        val half = getSliderLength() / 2
        val minVal = -(screenHeight / 2 - sbHeight - half)
        val maxVal = screenHeight / 2 - navbarHeight - half
        val newY = y.coerceIn(minVal, maxVal)
        logger.d("constrainY: $y -> $newY")
        return newY
    }

    private fun getSliderLength(): Int {
        return sharedPrefs.getInt(SLIDER_LENGTH, DEFAULT_SIDELINE_HEIGHT)
    }

    /**
     * 启动侧边条
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun showView() {
        if (isShowingSideline) return

        logger.d("showView")

        layoutParams.apply {
            type = LayoutParams.TYPE_APPLICATION_OVERLAY
            width = SIDELINE_WIDTH
            height = getSliderLength()
            flags = LayoutParams.FLAG_NOT_FOCUSABLE or
                    LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    LayoutParams.FLAG_HARDWARE_ACCELERATED
            privateFlags = LayoutParams.SYSTEM_FLAG_SHOW_FOR_ALL_USERS or
                    LayoutParams.PRIVATE_FLAG_TRUSTED_OVERLAY or
                    LayoutParams.PRIVATE_FLAG_SYSTEM_APPLICATION_OVERLAY
            format = PixelFormat.RGBA_8888
            windowAnimations = android.R.style.Animation_Dialog
        }

        val transparency = sharedPrefs.getFloat(SLIDER_TRANSPARENCY, 1.0f)
        sideLineView.alpha = transparency

        sideLineView.setSystemGestureExclusionRects(
            listOf(Rect(0, 0, SIDELINE_WIDTH, getSliderLength()))
        )

        updateSidelinePosition()

        handler.post {
            runCatching {
                windowManager.addView(sideLineView, layoutParams)
                viewModel.registerCallbacks()
                isShowingSideline = true
            }.onFailure { e ->
                logger.e("failed to add sideline view: ", e)
            }
        }
    }

    private fun updateSidelinePosition() {
        sidelinePositionX = sharedPrefs.getInt(SIDELINE_POSITION_X, 1)
        sidelinePositionY =
            if (isPortrait)
                sharedPrefs.getInt(SIDELINE_POSITION_Y_PORTRAIT, -screenHeight / 6)
            else
                sharedPrefs.getInt(SIDELINE_POSITION_Y_LANDSCAPE, -screenHeight / 6)

        layoutParams.apply {
            x = sidelinePositionX * (screenWidth / 2 - offset)
            y = constrainY(sidelinePositionY)
            logger.d("updateSidelinePosition: ($x,$y)")

            if (isPortrait) {
                layoutInDisplayCutoutMode = LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                flags = (flags and LayoutParams.FLAG_LAYOUT_IN_SCREEN.inv()) or
                    LayoutParams.FLAG_LAYOUT_NO_LIMITS
            } else {
                // avoid going into navbar in landscape
                layoutInDisplayCutoutMode = LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                flags = (flags and LayoutParams.FLAG_LAYOUT_NO_LIMITS.inv()) or
                    LayoutParams.FLAG_LAYOUT_IN_SCREEN
            }
        }

        if (isShowingSideline) {
            updateViewLayout()
        }
    }

    private fun updateViewLayout() {
        handler.post {
            runCatching {
                windowManager.updateViewLayout(sideLineView, layoutParams)
            }.onFailure { e ->
                logger.e("failed to updateViewLayout: ", e)
            }
        }
    }

    private fun removeView(force: Boolean = false) {
        if (!isShowingSideline && !force) return

        logger.d("removeView")
        viewModel.unregisterCallbacks()

        handler.post {
            runCatching {
                windowManager.removeViewImmediate(sideLineView)
            }.onFailure { e ->
                logger.e("failed to remove sideline view: $e")
            }
        }

        sidebarView.removeView(force)
        isShowingSideline = false
    }

    private fun animateHideSideline() {
        logger.d("animateHideSideline")
        sideLineView.animate().translationX(sidelinePositionX * 1.0f * SIDELINE_WIDTH).setDuration(300).start()
    }

    private fun animateShowSideline() {
        logger.d("animateShowSideline")
        sideLineView.animate().translationX(0f).setDuration(300).start()
    }

    private fun setIntSp(name: String, value: Int) {
        sharedPrefs.edit().apply {
            putInt(name, value)
            apply()
        }
    }
}
