/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.libremobileos.sidebar.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.libremobileos.sidebar.app.SidebarApplication
import com.libremobileos.sidebar.ui.sidebar.SidebarPerAppConfigFragment
import com.libremobileos.sidebar.utils.ForegroundAppDetector
import com.libremobileos.sidebar.utils.Logger

/**
 * Service that monitors foreground apps and automatically enables/disables sidebar
 * based on per-app configuration
 */
class SidebarMonitorService : Service() {
    private val logger = Logger("SidebarMonitorService")
    
    private var handler: Handler? = null
    private var monitorRunnable: Runnable? = null
    private val monitorInterval = 2000L // 2 seconds
    private var isRunning = false
    
    private lateinit var sharedPrefs: SharedPreferences
    
    private var lastForegroundApp = ""
    private var lastSidebarState = false

    override fun onCreate() {
        super.onCreate()
        logger.d("onCreate")
        
        sharedPrefs = applicationContext.getSharedPreferences(SidebarApplication.CONFIG, Context.MODE_PRIVATE)
        handler = Handler(Looper.getMainLooper())
        
        monitorRunnable = Runnable {
            if (isRunning) {
                monitorForegroundApp()
                handler?.postDelayed(monitorRunnable!!, monitorInterval)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        logger.d("onStartCommand")
        if (!isRunning) {
            isRunning = true
            handler?.post(monitorRunnable!!)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        logger.d("onDestroy")
        isRunning = false
        
        handler?.removeCallbacks(monitorRunnable!!)
        handler?.removeCallbacksAndMessages(null)
        
        lastForegroundApp = ""
        lastSidebarState = false
        handler = null
        monitorRunnable = null
    }

    private fun monitorForegroundApp() {
        try {
            if (!isRunning) return

            val masterEnabled = sharedPrefs.getBoolean(SidebarService.SIDELINE, false)
            
            if (masterEnabled) {
                return
            }

            val autoEnableEnabled = sharedPrefs.getBoolean(KEY_AUTO_ENABLE_SELECTED_APPS, false)
            if (!autoEnableEnabled) {
                if (lastSidebarState) {
                    stopSidebarService()
                    lastSidebarState = false
                }
                return
            }

            val foregroundPackage = ForegroundAppDetector.getForegroundPackageName(this)
            logger.d("Foreground app: $foregroundPackage")

            if (foregroundPackage != lastForegroundApp) {
                val autoApps = sharedPrefs.getStringSet(SidebarPerAppConfigFragment.PREF_AUTO_APPS, emptySet()) ?: emptySet()
                val shouldShow = autoApps.contains(foregroundPackage)

                logger.d("Should show sidebar for $foregroundPackage: $shouldShow")

                if (shouldShow && !lastSidebarState) {
                    startSidebarService()
                    lastSidebarState = true
                } else if (!shouldShow && lastSidebarState) {
                    stopSidebarService()
                    lastSidebarState = false
                }

                lastForegroundApp = foregroundPackage
            }
        } catch (e: Exception) {
            logger.e("Error in monitorForegroundApp", e)
        }
    }

    private fun startSidebarService() {
        logger.d("Starting sidebar service for auto-enable")
        try {
            sharedPrefs.edit().putBoolean(KEY_AUTO_ENABLED_TEMP, true).apply()
        } catch (e: Exception) {
            logger.e("Error starting sidebar service", e)
        }
    }

    private fun stopSidebarService() {
        logger.d("Stopping sidebar service for auto-enable")
        try {
            sharedPrefs.edit().putBoolean(KEY_AUTO_ENABLED_TEMP, false).apply()
        } catch (e: Exception) {
            logger.e("Error stopping sidebar service", e)
        }
    }

    companion object {
        const val KEY_AUTO_ENABLE_SELECTED_APPS = "sidebar_auto_enable_selected_apps"
        const val KEY_AUTO_ENABLED_TEMP = "sidebar_auto_enabled_temp"
    }
}
