/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */
 
package com.libremobileos.sidebar.utils

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Utility class to detect the currently running foreground app
 */
object ForegroundAppDetector {
    private const val TAG = "ForegroundAppDetector"
    private var lastKnownPackage = "Unknown"
    private var lastUpdateTime = 0L
    private const val CACHE_TIMEOUT = 500L // 500ms cache timeout
    
    /**
     * Get the package name of the currently running foreground app
     */
    fun getForegroundPackageName(context: Context): String {
        // Use cached result if still valid
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastUpdateTime < CACHE_TIMEOUT && lastKnownPackage != "Unknown") {
            return lastKnownPackage
        }

        val pkg = tryGetRunningTasks(context) ?: tryUsageStats(context)
        
        if (pkg != null) {
            lastKnownPackage = pkg
            lastUpdateTime = currentTime
            return pkg
        }
        
        // Return cached value if available, otherwise "Unknown"
        return lastKnownPackage
    }

    private fun tryGetRunningTasks(context: Context): String? {
        return try {
            if (context.checkSelfPermission("android.permission.GET_TASKS") == PackageManager.PERMISSION_GRANTED) {
                val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val tasks = activityManager.getRunningTasks(1)
                if (tasks.isNotEmpty()) {
                    tasks[0].topActivity?.packageName
                } else null
            } else {
                Logger("ForegroundAppDetector").w("GET_TASKS permission not granted")
                null
            }
        } catch (e: Exception) {
            Logger("ForegroundAppDetector").e("Error getting running tasks", e)
            null
        }
    }

    private fun tryUsageStats(context: Context): String? {
        return try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            if (usageStatsManager != null) {
                val currentTime = System.currentTimeMillis()
                val stats = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    currentTime - 1000 * 60, // Last minute
                    currentTime
                )
                
                if (stats.isNotEmpty()) {
                    // Get the most recently used app
                    val sortedStats = stats.sortedByDescending { it.lastTimeUsed }
                    sortedStats.firstOrNull()?.packageName
                } else null
            } else null
        } catch (e: Exception) {
            Logger("ForegroundAppDetector").e("Error getting usage stats", e)
            null
        }
    }
}
