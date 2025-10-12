package com.libremobileos.freeform

import android.app.PendingIntent
import android.os.Build
import android.os.IBinder
import android.os.ServiceManager
import android.util.Log
import com.libremobileos.freeform.ILMOFreeformUIService
import java.util.Date

object LMOFreeformServiceManager {
    private const val TAG = "LMOFreeformServiceManager"
    private var iLMOFreeformService: ILMOFreeformUIService? = null

    fun init() {
        try {
            val r = ServiceManager.getService("lmo_freeform")
            if (r == null) {
                Log.e(TAG, "Failed to get lmo_freeform service")
                return
            }
            iLMOFreeformService = ILMOFreeformUIService.Stub.asInterface(r)
            iLMOFreeformService?.ping()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize LMOFreeform service", e)
        }
    }

    fun ping(): Boolean {
        return try {
            iLMOFreeformService?.ping() ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Ping failed", e)
            false
        }
    }

    fun createWindow(packageName: String, activityName: String, userId: Int, taskId: Int,
            width: Int, height: Int, densityDpi: Int) {
        try {
            iLMOFreeformService?.startAppInFreeform(
                packageName,
                activityName,
                userId,
                taskId,
                null,
                width,
                height,
                densityDpi
            ) ?: Log.e(TAG, "Cannot create window: service not initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create window for $packageName/$activityName", e)
        }
    }

    fun createWindow(pendingIntent: PendingIntent?, width: Int, height: Int, densityDpi: Int) {
        try {
            iLMOFreeformService?.startAppInFreeform(
                pendingIntent?.creatorPackage ?: "pendingIntentCreatorPackage",
                "unknownActivity-${Date().time}",
                -100,
                -1,
                pendingIntent,
                width,
                height,
                densityDpi
            ) ?: Log.e(TAG, "Cannot create window: service not initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create window from PendingIntent", e)
        }
    }

    fun removeFreeform(freeformId: String) {
        try {
            iLMOFreeformService?.removeFreeform(freeformId)
                ?: Log.e(TAG, "Cannot remove freeform: service not initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove freeform: $freeformId", e)
        }
    }
}
