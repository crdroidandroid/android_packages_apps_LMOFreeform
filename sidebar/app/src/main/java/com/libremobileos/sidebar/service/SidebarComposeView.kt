package com.libremobileos.sidebar.service

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.settingslib.spa.framework.compose.rememberDrawablePainter
import com.libremobileos.sidebar.R
import com.libremobileos.sidebar.app.SidebarApplication
import com.libremobileos.sidebar.bean.AppInfo

@Composable
fun SidebarComposeView(
    viewModel: ServiceViewModel,
    launchApp: (AppInfo) -> Unit,
    closeSidebar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sharedPrefs = context.getSharedPreferences(SidebarApplication.CONFIG, Context.MODE_PRIVATE)
    
    // Get customization settings
    val columnCount = sharedPrefs.getInt("sidebar_columns", 1)
    val iconSize = sharedPrefs.getInt("sidebar_icon_size", 50)
    val iconPadding = sharedPrefs.getInt("sidebar_icon_padding", 8)
    val columnSpacing = sharedPrefs.getInt("sidebar_column_spacing", 4)
    val cornerRadius = sharedPrefs.getFloat("sidebar_corner_radius", 16f)
    val backgroundTransparency = sharedPrefs.getFloat("sidebar_background_transparency", 1.0f)
    val showShadow = sharedPrefs.getBoolean("sidebar_show_shadow", true)
    
    val sidebarAppList by viewModel.sidebarAppListFlow.collectAsState()
    
    // Create all items list
    val allItems = buildList {
        add(SidebarItem.AllApps(viewModel.allAppActivity))
        addAll(sidebarAppList.map { SidebarItem.App(it) })
        add(SidebarItem.Settings)
    }
    
    var cardModifier = modifier
    
    if (showShadow) {
        cardModifier = cardModifier.shadow(
            elevation = 8.dp,
            shape = RoundedCornerShape(cornerRadius.dp)
        )
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(
                alpha = backgroundTransparency
            )
        ),
        shape = RoundedCornerShape(cornerRadius.dp),
        modifier = cardModifier
    ) {
        if (columnCount == 1) {
            // Single column
            LazyColumn(
                contentPadding = PaddingValues(4.dp)
            ) {
                items(allItems) { item ->
                    SidebarItemView(
                        item = item,
                        iconSize = iconSize,
                        iconPadding = iconPadding,
                        viewModel = viewModel,
                        launchApp = launchApp,
                        closeSidebar = closeSidebar
                    )
                }
            }
        } else {
            // Multi-column
            LazyVerticalGrid(
                columns = GridCells.Fixed(columnCount),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(columnSpacing.dp),
                verticalArrangement = Arrangement.spacedBy(columnSpacing.dp),
                modifier = Modifier.width((
                    (iconSize + iconPadding * 2) * columnCount +
                    columnSpacing * (columnCount - 1) +
                    8
                ).dp)
            ) {
                items(allItems) { item ->
                    SidebarItemView(
                        item = item,
                        iconSize = iconSize,
                        iconPadding = iconPadding,
                        viewModel = viewModel,
                        launchApp = launchApp,
                        closeSidebar = closeSidebar
                    )
                }
            }
        }
    }
}

@Composable
private fun SidebarItemView(
    item: SidebarItem,
    iconSize: Int,
    iconPadding: Int,
    viewModel: ServiceViewModel,
    launchApp: (AppInfo) -> Unit,
    closeSidebar: () -> Unit
) {
    Box(
        modifier = Modifier.padding(iconPadding.dp),
        contentAlignment = Alignment.Center
    ) {
        when (item) {
            is SidebarItem.AllApps -> {
                Icon(
                    painter = rememberDrawablePainter(
                        drawable = item.appInfo.icon
                    ),
                    contentDescription = item.appInfo.label,
                    modifier = Modifier
                        .size(iconSize.dp)
                        .clickable {
                            launchApp(item.appInfo)
                        }
                )
            }
            is SidebarItem.App -> {
                Image(
                    painter = rememberDrawablePainter(item.appInfo.icon),
                    contentDescription = item.appInfo.label,
                    modifier = Modifier
                        .size(iconSize.dp)
                        .clickable {
                            launchApp(item.appInfo)
                        }
                )
            }
            is SidebarItem.Settings -> {
                Icon(
                    painter = painterResource(R.drawable.edit_24px),
                    contentDescription = stringResource(R.string.sidebar_settings_description),
                    modifier = Modifier
                        .size((iconSize - 4).dp)
                        .clickable {
                            viewModel.openSidebarSettings()
                            closeSidebar()
                        }
                )
            }
        }
    }
}

private sealed class SidebarItem {
    data class AllApps(val appInfo: AppInfo) : SidebarItem()
    data class App(val appInfo: AppInfo) : SidebarItem()
    data object Settings : SidebarItem()
}
