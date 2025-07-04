package com.libremobileos.sidebar.ui.sidebar

import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.rememberNavController
import com.android.settingslib.spa.framework.compose.localNavController
import com.android.settingslib.spa.framework.compose.rememberDrawablePainter
import com.android.settingslib.spa.framework.theme.SettingsDimension
import com.android.settingslib.spa.widget.preference.MainSwitchPreference
import com.android.settingslib.spa.widget.preference.SwitchPreference
import com.android.settingslib.spa.widget.preference.SwitchPreferenceModel
import com.android.settingslib.spa.widget.scaffold.SettingsScaffold
import com.android.settingslib.spa.widget.ui.Category
import com.libremobileos.sidebar.R
import com.libremobileos.sidebar.bean.SidebarAppInfo

@Composable
fun SidebarSettingsPage(
    viewModel: SidebarSettingsViewModel
) {
    val navController = rememberNavController()
    var mainChecked = rememberSaveable { mutableStateOf(viewModel.getSidebarEnabled()) }
    val context = LocalContext.current
    val themedIconsProp = "sidebar_themed_icons"
    var isThemedIconsEnabled by remember {
        mutableStateOf(
            Settings.System.getInt(
                context.contentResolver,
                themedIconsProp,
                0
            ) == 1
        )
    }

    CompositionLocalProvider(navController.localNavController()) {
        SettingsScaffold(
            title = stringResource(R.string.sidebar_label)
        ) { paddingValues ->
            Column(
                modifier = Modifier.padding(paddingValues)
            ) {
                MainSwitchPreference(object : SwitchPreferenceModel {
                    override val title = stringResource(R.string.enable_sideline)
                    override val checked = { mainChecked.value }
                    override val changeable = { viewModel.isEnabled }
                    override val onCheckedChange: (Boolean) -> Unit = {
                        mainChecked.value = it
                        viewModel.setSidebarEnabled(it)
                        if(!it) {
                            viewModel.deleteAllSidebarApps()
                        }
                    }
                })

                SwitchPreference(
                    model = object : SwitchPreferenceModel {
                        override val title = stringResource(R.string.sidebar_themed_icons_label)
                        override val checked = { isThemedIconsEnabled }
                        override val onCheckedChange: (Boolean) -> Unit = {
                            isThemedIconsEnabled = it
                            Settings.System.putInt(
                                context.contentResolver,
                                themedIconsProp,
                                if (it) 1 else 0
                            )
                        }
                    },
                )

                if (mainChecked.value) {
                    SidebarAppList(viewModel)
                }
            }
        }
    }
}

@Composable
fun SidebarAppList(
    viewModel: SidebarSettingsViewModel
) {
    val sidebarApps by viewModel.appListFlow.collectAsState()
    Category(
        title = stringResource(R.string.sidebar_app_setting_label)
    ) {
        LazyColumn {
            items(sidebarApps) { appInfo ->
                SidebarAppListItem(
                    appInfo = appInfo,
                    onCheckedChange = { isChecked ->
                        if (isChecked) {
                            viewModel.addSidebarApp(appInfo)
                        } else {
                            viewModel.deleteSidebarApp(appInfo)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun SidebarAppListItem(
    appInfo: SidebarAppInfo,
    onCheckedChange: (Boolean) -> Unit
) {
    var appChecked = rememberSaveable { mutableStateOf(appInfo.isSidebarApp) }
    SwitchPreference(
        model = object : SwitchPreferenceModel {
            override val title = appInfo.label
            override val icon = @Composable {
                Image(
                    painter = rememberDrawablePainter(appInfo.icon),
                    contentDescription = appInfo.label,
                    modifier = Modifier.size(SettingsDimension.appIconItemSize)
                )
            }
            override val checked = { appChecked.value }
            override val onCheckedChange: (Boolean) -> Unit = {
                appChecked.value = it
                onCheckedChange(it)
            }
        },
    )
}
