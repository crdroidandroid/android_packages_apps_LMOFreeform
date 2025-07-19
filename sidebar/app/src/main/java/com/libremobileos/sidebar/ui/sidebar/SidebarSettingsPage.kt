package com.libremobileos.sidebar.ui.sidebar

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
    val context = LocalContext.current
    val sharedPrefs = context.getSharedPreferences("config", Context.MODE_PRIVATE)
    
    var showCustomization by remember { mutableStateOf(false) }
    val navController = rememberNavController()
    var mainChecked = rememberSaveable { mutableStateOf(viewModel.getSidebarEnabled()) }

    if (showCustomization) {
        SidebarCustomizationSettingsPage(
            sharedPrefs = sharedPrefs,
            onBack = { showCustomization = false },
            onSettingChanged = {
                if (mainChecked.value) {
                    viewModel.setSidebarEnabled(false)
                    viewModel.setSidebarEnabled(true)
                }
            }
        )
    } else {
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
                        }
                    })
                    if (mainChecked.value) {
                        SidebarSettingSwitch(
                            title = stringResource(R.string.sidebar_predicted_apps),
                            summary = stringResource(R.string.sidebar_predicted_apps_summary),
                            isChecked = viewModel.getPredictedAppsEnabled(),
                            onCheckedChange = { viewModel.setPredictedAppsEnabled(it) }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { showCustomization = true },
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text("Sidebar Customization")
                    }
                    
                    if (mainChecked.value) {
                        SidebarAppList(viewModel)
                    }
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

@Composable
fun SidebarSettingSwitch(
    title: String,
    summary: String?,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    var myChecked = rememberSaveable { mutableStateOf(isChecked) }
    SwitchPreference(
        model = object : SwitchPreferenceModel {
            override val title = title
            override val summary = { summary ?: "" }
            override val checked = { myChecked.value }
            override val onCheckedChange: (Boolean) -> Unit = {
                myChecked.value = it
                onCheckedChange(it)
            }
        },
    )
}
