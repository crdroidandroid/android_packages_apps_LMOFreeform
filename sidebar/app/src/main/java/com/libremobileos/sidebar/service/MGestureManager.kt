package com.libremobileos.sidebar.service

import android.content.Context
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import kotlin.math.abs

class MGestureManager(context: Context, private val mListener: MGestureListener) {
    private val mGestureDetector: GestureDetector
    private val minVelocity = 50

    companion object {
        private const val TAG = "MGestureManager"
    }

    interface MGestureListener {
        fun singleFingerSlipAction(
            gestureEvent: GestureEvent?,
            startEvent: MotionEvent?,
            endEvent: MotionEvent?,
            velocity: Float
        ): Boolean

        fun onTouchEvent(event: MotionEvent)
        fun onSingleTapUp(e: MotionEvent): Boolean
    }

    enum class GestureEvent {
        SINGLE_GINGER_LEFT_SLIP, SINGLE_GINGER_RIGHT_SLIP, SINGLE_GINGER_UP_SLIP, SINGLE_GINGER_DOWN_SLIP
    }

    fun onTouchEvent(event: MotionEvent): Boolean {
        mListener.onTouchEvent(event)
        return mGestureDetector.onTouchEvent(event)
    }

    private inner class SimpleGesture : SimpleOnGestureListener() {

        @Suppress("NOTHING_TO_OVERRIDE", "ACCIDENTAL_OVERRIDE")
        override fun onFling(
            e1: MotionEvent, e2: MotionEvent, velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (e1.x - e2.x > 0 && abs((e1.x - e2.x).toInt()) > abs((e1.y - e2.y).toInt()) && abs(velocityX) > minVelocity) {
                return mListener.singleFingerSlipAction(
                    GestureEvent.SINGLE_GINGER_LEFT_SLIP,
                    e1,
                    e2,
                    abs(velocityX)
                )
            }
            else if (e1.x - e2.x < 0 && abs((e1.x - e2.x).toInt()) > abs((e1.y - e2.y).toInt()) && abs(velocityX) > minVelocity) {
                return mListener.singleFingerSlipAction(
                    GestureEvent.SINGLE_GINGER_RIGHT_SLIP,
                    e1,
                    e2,
                    abs(velocityX)
                )
            } else if (e1.y - e2.y > 0 && abs((e1.y - e2.y).toInt()) > abs((e1.x - e2.x).toInt()) && abs(
                    velocityY
                ) > minVelocity
            ) {
                return mListener.singleFingerSlipAction(
                    GestureEvent.SINGLE_GINGER_UP_SLIP,
                    e1,
                    e2,
                    abs(velocityY)
                )
            } else if (e1.y - e2.y < 0 && abs((e1.y - e2.y).toInt()) > abs((e1.x - e2.x).toInt()) && abs(
                    velocityY
                ) > minVelocity
            ) {
                return mListener.singleFingerSlipAction(
                    GestureEvent.SINGLE_GINGER_DOWN_SLIP,
                    e1,
                    e2,
                    abs(velocityY)
                )
            } else return true
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            return mListener.onSingleTapUp(e)
        }
    }

    init {
        mGestureDetector =
            GestureDetector(context, SimpleGesture(), null, true)
    }
}
