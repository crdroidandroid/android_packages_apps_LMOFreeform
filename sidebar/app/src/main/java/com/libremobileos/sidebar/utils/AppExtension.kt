package com.libremobileos.sidebar.utils

import android.app.Application
import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import android.os.UserHandle
import android.util.Log

fun Application.isResizeableActivity(component: ComponentName): Boolean {
    return runCatching { packageManager.getActivityInfo(component, /* flags */ 0) }
        .onFailure { Log.e(MAIN_TAG, "failed to get activity info for $component: $it") }
        .getOrNull()
        ?.let { ActivityInfo.isResizeableMode(it.resizeMode) }
        ?: false
}

fun Application.isResizeableActivity(packageName: String, activityName: String): Boolean =
    isResizeableActivity(ComponentName(packageName, activityName))

fun Application.getBadgedIcon(appInfo: ApplicationInfo, userHandle: UserHandle): Drawable =
    packageManager.getUserBadgedIcon(
        appInfo.loadIcon(packageManager),
        userHandle
    )

fun Application.getBadgedIcon(appInfo: ApplicationInfo, userId: Int): Drawable =
    getBadgedIcon(appInfo, UserHandle.of(userId))

