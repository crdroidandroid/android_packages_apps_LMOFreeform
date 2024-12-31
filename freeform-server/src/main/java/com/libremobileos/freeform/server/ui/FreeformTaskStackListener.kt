package com.libremobileos.freeform.server.ui

import android.app.ActivityManager
import android.app.ITaskStackListener
import android.content.ComponentName
import android.os.Build
import android.util.Slog
import android.view.Display
import android.view.Surface
import android.window.TaskSnapshot
import com.libremobileos.freeform.server.Debug.dlog
import com.libremobileos.freeform.server.LMOFreeformServiceHolder
import com.libremobileos.freeform.server.SystemServiceHolder
import kotlin.math.max
import kotlin.math.min

class FreeformTaskStackListener(
    private val displayId: Int,
    private val window: FreeformWindow
) : ITaskStackListener.Stub() {

    var taskId = -1

    companion object {
        private const val TAG = "LMOFreeform/FreeformTaskStackListener"

        const val PORTRAIT = 1
        const val LANDSCAPE_1 = 0
        const val LANDSCAPE_2 = 6
    }

    override fun onTaskStackChanged() {

    }

    override fun onActivityPinned(packageName: String, userId: Int, taskId: Int, stackId: Int) {

    }

    override fun onActivityUnpinned() {

    }

    override fun onActivityRestartAttempt(
        task: ActivityManager.RunningTaskInfo?,
        homeTaskVisible: Boolean,
        clearedTask: Boolean,
        wasVisible: Boolean
    ) {

    }

    override fun onActivityForcedResizable(packageName: String, taskId: Int, reason: Int) {

    }

    override fun onActivityDismissingDockedTask() {

    }

    override fun onActivityLaunchOnSecondaryDisplayFailed(
        taskInfo: ActivityManager.RunningTaskInfo?,
        requestedDisplayId: Int
    ) {

    }

    override fun onActivityLaunchOnSecondaryDisplayRerouted(
        taskInfo: ActivityManager.RunningTaskInfo?,
        requestedDisplayId: Int
    ) {

    }

    override fun onTaskCreated(taskId: Int, componentName: ComponentName?) {

    }

    override fun onTaskRemoved(taskId: Int) {
        if (this.taskId == taskId) {
            dlog(TAG, "onTaskRemoved $taskId")
            window.destroy("onTaskRemoved")
        }
    }

    override fun onRecentTaskRemovedForAddTask(taskId: Int) {
    }

    override fun onTaskMovedToFront(taskInfo: ActivityManager.RunningTaskInfo?) {
        val displayId = taskInfo?.displayId ?: return
        if (this.displayId == displayId) {
            // TODO: move to android.provider.Settings
            // if (FreeformWindowManager.settings.showImeInFreeform) {
            //     SystemServiceHolder.windowManager.setDisplayImePolicy(displayId, 0)
            // }
            taskId = taskInfo.taskId
            dlog(TAG, "onTaskMovedToFront $taskId")
        }
    }

    override fun onTaskDescriptionChanged(taskInfo: ActivityManager.RunningTaskInfo?) {
        val displayId = taskInfo?.displayId ?: return
        if (this.displayId == displayId) {
            taskId = taskInfo.taskId
            dlog(TAG, "onTaskDescriptionChanged $taskInfo")
        }
    }

    override fun onActivityRequestedOrientationChanged(taskId: Int, requestedOrientation: Int) {

    }

    override fun onTaskRemovalStarted(taskInfo: ActivityManager.RunningTaskInfo?) {
        val displayId = taskInfo?.displayId ?: return
        if (this.displayId == displayId) {
            taskId = taskInfo.taskId
            dlog(TAG, "onTaskRemovalStarted $taskId")
            // window.removeView()
        }
    }

    override fun onTaskProfileLocked(taskInfo: ActivityManager.RunningTaskInfo, userId: Int) {

    }

    override fun onTaskSnapshotChanged(taskId: Int, snapshot: TaskSnapshot) {

    }

    override fun onBackPressedOnTaskRoot(taskInfo: ActivityManager.RunningTaskInfo?) {

    }

    override fun onTaskDisplayChanged(taskId: Int, newDisplayId: Int) {
        if (taskId == this.taskId && newDisplayId == Display.DEFAULT_DISPLAY) {
            window.destroy("onTaskDisplayChanged")
        } else if (newDisplayId == displayId) {
            this.taskId = taskId
            dlog(TAG, "onTaskDisplayChanged: $taskId to freeform display")
        }
    }

    override fun onRecentTaskListUpdated() {

    }

    override fun onRecentTaskListFrozenChanged(frozen: Boolean) {

    }

    override fun onTaskFocusChanged(taskId: Int, focused: Boolean) {

    }

    override fun onTaskRequestedOrientationChanged(taskId: Int, requestedOrientation: Int) {
        if (taskId == this.taskId) {
            dlog(TAG, "onTaskRequestedOrientationChanged: $requestedOrientation")
            val max = max(window.freeformConfig.width, window.freeformConfig.height)
            val min = min(window.freeformConfig.width, window.freeformConfig.height)
            val maxHangUp = max(window.freeformConfig.hangUpWidth, window.freeformConfig.hangUpHeight)
            val minHangUp = min(window.freeformConfig.hangUpWidth, window.freeformConfig.hangUpHeight)
            when (requestedOrientation) {
                PORTRAIT -> {
                    dlog(TAG, "PORTRAIT")
                    window.freeformConfig.width = min
                    window.freeformConfig.height = max
                    window.freeformConfig.hangUpWidth = minHangUp
                    window.freeformConfig.hangUpHeight = maxHangUp
                }
                LANDSCAPE_1, LANDSCAPE_2 -> {
                    dlog(TAG, "LANDSCAPE")
                    window.freeformConfig.width = max
                    window.freeformConfig.height = min
                    window.freeformConfig.hangUpWidth = maxHangUp
                    window.freeformConfig.hangUpHeight = minHangUp
                }
            }
            window.handler.post { window.changeOrientation() }
        }
    }

    override fun onActivityRotation(displayId: Int) {

    }

    override fun onTaskMovedToBack(taskInfo: ActivityManager.RunningTaskInfo?) {

    }

    override fun onLockTaskModeChanged(mode: Int) {

    }

    override fun onTaskSnapshotInvalidated(taskId: Int) {

    }
}
