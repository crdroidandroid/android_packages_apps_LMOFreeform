package com.libremobileos.sidebar.service

import android.graphics.drawable.AdaptiveIconDrawable
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.android.settingslib.spa.framework.compose.rememberDrawablePainter
import com.libremobileos.sidebar.bean.AppInfo

@Composable
fun SidebarComposeView(
    viewModel: ServiceViewModel,
    onClick: (AppInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    val sidebarAppList by viewModel.sidebarAppListFlow.collectAsState()
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val isThemedIconsEnabled by remember {
        mutableStateOf(
            Settings.System.getInt(
                context.contentResolver,
                "sidebar_themed_icons",
                0
            ) == 1
        )
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        LazyColumn {
            item {
                Icon(
                    painter = rememberDrawablePainter(
                        drawable = viewModel.allAppActivity.icon
                    ),
                    contentDescription = viewModel.allAppActivity.label,
                    modifier = Modifier
                        .size(50.dp)
                        .padding(8.dp)
                        .clickable {
                            onClick(viewModel.allAppActivity)
                        }
                )
            }
            items(sidebarAppList) { appInfo ->
                val adaptiveIconDrawable = if (appInfo.icon is AdaptiveIconDrawable) {
                    appInfo.icon as AdaptiveIconDrawable
                } else null
                Image(
                    painter = rememberDrawablePainter(
                        drawable = if (isThemedIconsEnabled &&
                            adaptiveIconDrawable?.monochrome != null
                        )
                            adaptiveIconDrawable.monochrome
                        else appInfo.icon
                    ),
                    colorFilter = if (isThemedIconsEnabled &&
                            adaptiveIconDrawable?.monochrome != null
                        ) {
                        if (isDark) ColorFilter.tint(Color.White)
                        else ColorFilter.tint(Color.Black)
                    } else null,
                    contentDescription = appInfo.label,
                    modifier = Modifier
                        .size(50.dp)
                        .padding(8.dp)
                        .clickable {
                            onClick(appInfo)
                        }
                )
            }
        }
    }
}
