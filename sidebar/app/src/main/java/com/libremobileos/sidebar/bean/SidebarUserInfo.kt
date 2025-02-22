package com.libremobileos.sidebar.bean

import android.os.UserHandle

data class SidebarUserInfo(
    val userId: Int,
    val userHandle: UserHandle,
    val suffix: String
)
