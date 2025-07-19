package com.libremobileos.sidebar.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.libremobileos.sidebar.app.SidebarApplication
import com.libremobileos.sidebar.service.SidebarService
import com.libremobileos.sidebar.service.SidebarMonitorService
import com.libremobileos.sidebar.utils.Logger
import java.util.logging.Handler

/**
 * @author KindBrave
 * @since 2023/9/19
 */
class BootReceiver : BroadcastReceiver() {
    private val logger = Logger(TAG)
    companion object {
        private const val BOOT = "android.intent.action.BOOT_COMPLETED"
        private const val TAG = "BootReceiver"
    }
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == BOOT) {
            logger.d("Boot Completed")
            context.startServiceAsUser(Intent(context, SidebarService::class.java), android.os.UserHandle(android.os.UserHandle.USER_CURRENT))
            
            val sharedPrefs = context.getSharedPreferences(SidebarApplication.CONFIG, Context.MODE_PRIVATE)
            val autoEnableEnabled = sharedPrefs.getBoolean(SidebarMonitorService.KEY_AUTO_ENABLE_SELECTED_APPS, false)
            val mainEnabled = sharedPrefs.getBoolean(SidebarService.SIDELINE, false)
            
            if (autoEnableEnabled || mainEnabled) {
                context.startServiceAsUser(Intent(context, SidebarMonitorService::class.java), android.os.UserHandle(android.os.UserHandle.USER_CURRENT))
                logger.d("Started SidebarMonitorService")
            }
        }
    }
}
