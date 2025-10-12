package com.libremobileos.freeform.server.ui

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ValueAnimator

object FreeformAnimation {
    fun moveInScreenAnimator(start: Int, end: Int, dur: Long, moveX: Boolean, window: FreeformWindow) {
        val layout = window.freeformLayout ?: return
        
        AnimatorSet().apply {
            play(
                ValueAnimator.ofInt(start, end).apply {
                    addUpdateListener {
                        window.windowManager.updateViewLayout(
                            layout,
                            window.windowParams.apply {
                                if (moveX) x = it.animatedValue as Int
                                else y = it.animatedValue as Int
                            }
                        )
                    }
                }
            )
            duration = dur
            start()
        }
    }

    fun toFullScreen(window: FreeformWindow, dur: Long, listener: Animator.AnimatorListener) {
        val layout = window.freeformLayout ?: return
        val rootView = window.freeformRootView ?: return
        
        AnimatorSet().apply {
            play(
                ValueAnimator.ofInt(window.windowParams.x, 0).apply {
                    addUpdateListener {
                        window.windowManager.updateViewLayout(
                            layout,
                            window.windowParams.apply {
                                x = it.animatedValue as Int
                            }
                        )
                    }
                }
            )
            duration = dur
            start()
        }
        AnimatorSet().apply {
            play(
                ValueAnimator.ofInt(window.windowParams.y, 0).apply {
                    addUpdateListener {
                        window.windowManager.updateViewLayout(
                            layout,
                            window.windowParams.apply {
                                y = it.animatedValue as Int
                            }
                        )
                    }
                }
            )
            duration = dur
            start()
        }
        AnimatorSet().apply {
            play(
                ValueAnimator.ofInt(window.freeformConfig.width, window.defaultDisplayWidth).apply {
                    addUpdateListener {
                        rootView.layoutParams = rootView.layoutParams.apply {
                            width = it.animatedValue as Int
                        }
                    }
                }
            )
            duration = dur
            start()
        }
        AnimatorSet().apply {
            play(
                ValueAnimator.ofInt(window.freeformConfig.height, window.defaultDisplayHeight).apply {
                    addUpdateListener {
                        rootView.layoutParams = rootView.layoutParams.apply {
                            height = it.animatedValue as Int
                        }
                    }
                }
            )
            duration = dur
            start()
        }.addListener(listener)
    }
}
