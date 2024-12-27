package com.libremobileos.sidebar.utils

import android.app.Application
import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.util.Log

fun Application.isResizeableActivity(component: ComponentName): Boolean {
    return runCatching { packageManager.getActivityInfo(component, /* flags */ 0) }
        .onFailure { Log.e(MAIN_TAG, "failed to get activity info for $component", it) }
        .getOrNull()
        ?.let { ActivityInfo.isResizeableMode(it.resizeMode) }
        ?: false
}

fun Application.isResizeableActivity(packageName: String, activityName: String): Boolean =
    isResizeableActivity(ComponentName(packageName, activityName))
