package com.libremobileos.sidebar.ui.all_app

import android.app.ActivityManager
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.libremobileos.sidebar.bean.AppInfo
import com.libremobileos.sidebar.ui.theme.SidebarTheme
import com.libremobileos.sidebar.utils.Logger

/**
 * @author KindBrave
 * @since 2023/10/25
 */
class AllAppActivity: ComponentActivity() {
    private val logger = Logger(TAG)
    private val viewModel: AllAppViewModel by viewModels { AllAppViewModel.Factory }

    companion object {
        private const val PACKAGE = "com.libremobileos.freeform"
        private const val ACTION = "com.libremobileos.freeform.START_FREEFORM"
        private const val TAG = "AllAppActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logger.d("onCreate")
		
        val isNativeFreeformEnabled = Settings.System.getInt(
            contentResolver,
            "freeform_launch_mode",
            0
        ) != 0

        setContent {
            SidebarTheme {
                AllAppGridView(
                    viewModel = viewModel,
                    onClick = ::onClick,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (!ActivityManager.isHighEndGfx() || isNativeFreeformEnabled)
                                Modifier.padding(top = 24.dp)
                            else Modifier.padding(top = 0.dp)
                        )
                )
            }
        }
    }

    override fun onDestroy() {
        logger.d("onDestroy")
        super.onDestroy()
    }

    private fun onClick(appInfo: AppInfo) {
        val intent = Intent(ACTION).apply {
            setPackage(PACKAGE)
            putExtra("packageName", appInfo.packageName)
            putExtra("activityName", appInfo.activityName)
            putExtra("userId", appInfo.userId)
        }
        sendBroadcast(intent)
        finish()
    }
}
