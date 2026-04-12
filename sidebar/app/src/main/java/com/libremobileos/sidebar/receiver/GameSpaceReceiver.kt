/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.libremobileos.sidebar.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.UserHandle
import com.libremobileos.sidebar.service.SidebarService
import com.libremobileos.sidebar.utils.Logger

class GameSpaceReceiver : BroadcastReceiver() {
    private val logger = Logger(TAG)

    companion object {
        private const val TAG = "GameSpaceReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        logger.d("onReceive: ${intent.action}")
        val serviceIntent = Intent(context, SidebarService::class.java).apply {
            action = intent.action
        }
        context.startServiceAsUser(serviceIntent, UserHandle.CURRENT)
    }
}
