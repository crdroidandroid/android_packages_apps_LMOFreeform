package com.libremobileos.sidebar.utils

import android.content.pm.UserInfo
import android.os.UserHandle
import android.os.UserManager
import com.libremobileos.sidebar.bean.SidebarUserInfo

fun UserManager.getSidebarFilteredUsers(): List<SidebarUserInfo> {
    val myUserId = UserHandle.myUserId()
    return users
        .filter { isSidebarUserAllowed(it) }
        .map { userInfo ->
            SidebarUserInfo(
                userInfo.id,
                userInfo.userHandle,
                if (userInfo.id != myUserId) {
                    " (${userInfo.name})"
                } else {
                    ""
                }
            )
        }
}

fun UserManager.isSidebarUserAllowed(userInfo: UserInfo): Boolean {
    val myUserId = UserHandle.myUserId()
    // must be either current user, or current user's parallel space or unlocked profile
    return userInfo.id == myUserId ||
        (userInfo.profileGroupId == myUserId && !isQuietModeEnabled(userInfo.userHandle))
}

fun UserManager.isSidebarUserAllowed(userId: Int): Boolean =
    isSidebarUserAllowed(getUserInfo(userId))
