package com.libremobileos.sidebar.service

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import com.libremobileos.sidebar.utils.Logger

/**
 * @author KindBrave
 * @since 2023/9/27
 */
class GestureListener(private val callback: Callback) : MGestureManager.MGestureListener {
    private val logger = Logger(TAG)
    private var initialTouchX = 0.0f
    private var initialTouchY = 0.0f
    private var isLongPress = false
    private val longPressHandler = Handler(Looper.getMainLooper())
    private val longPressRunnable = Runnable {
        isLongPress = true
        callback.beginMoveSideline()
    }

    companion object {
        private const val TAG = "GestureListener"
    }

    override fun singleFingerSlipAction(
        gestureEvent: MGestureManager.GestureEvent?,
        startEvent: MotionEvent?,
        endEvent: MotionEvent?,
        velocity: Float
    ): Boolean {
        if (null != gestureEvent) {
            if (gestureEvent == MGestureManager.GestureEvent.SINGLE_GINGER_LEFT_SLIP || gestureEvent == MGestureManager.GestureEvent.SINGLE_GINGER_RIGHT_SLIP) {
                callback.onSwipeToOpen()
            }
            return true
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent) {
        logger.d("onTouchEvent action=${event.action} x=${event.rawX} y=${event.rawY}")
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialTouchX = event.rawX
                initialTouchY = event.rawY

                isLongPress = false
                longPressHandler.postDelayed(longPressRunnable, 500)
            }
            MotionEvent.ACTION_MOVE -> {
                if (isLongPress) {
                    callback.moveSideline((event.rawX - initialTouchX).toInt(), (event.rawY - initialTouchY).toInt(), event.rawX.toInt(), event.rawY.toInt())
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                longPressHandler.removeCallbacks(longPressRunnable)
                isLongPress = false
                callback.endMoveSideline()
            }
        }
    }

    override fun onSingleTapUp(e: MotionEvent): Boolean {
        callback.onSingleTapUp()
        return true
    }

    interface Callback {
        fun onSwipeToOpen()
        fun showSidebar()
        fun beginMoveSideline()
        fun moveSideline(xChanged: Int, yChanged: Int, touchX: Int, touchY: Int)
        fun endMoveSideline()
        fun onSingleTapUp()
    }
}
