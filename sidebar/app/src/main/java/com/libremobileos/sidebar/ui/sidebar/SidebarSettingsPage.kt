/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */
 
package com.libremobileos.sidebar.ui.sidebar

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.settingslib.spa.framework.compose.rememberDrawablePainter
import com.android.settingslib.spa.framework.theme.SettingsDimension
import com.android.settingslib.spa.widget.preference.MainSwitchPreference
import com.android.settingslib.spa.widget.preference.Preference
import com.android.settingslib.spa.widget.preference.PreferenceModel
import com.android.settingslib.spa.widget.preference.SwitchPreference
import com.android.settingslib.spa.widget.preference.SwitchPreferenceModel
import com.android.settingslib.spa.widget.scaffold.SettingsScaffold
import com.android.settingslib.spa.widget.ui.Category
import com.libremobileos.sidebar.R
import com.libremobileos.sidebar.bean.SidebarAppInfo
import com.libremobileos.sidebar.service.SidebarMonitorService

@Composable
fun SidebarSettingsPage(
    viewModel: SidebarSettingsViewModel
) {
    val context = LocalContext.current
    var mainChecked = rememberSaveable { mutableStateOf(viewModel.getSidebarEnabled()) }

    SettingsScaffold(
        title = stringResource(R.string.sidebar_label),
        actions = {
            IconButton(onClick = {
                val intent = Intent(context, SidebarCustomizationActivity::class.java)
                context.startActivity(intent)
            }) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.sidebar_customization_title)
                )
            }
        }
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
                    val intent = Intent(context, SidebarMonitorService::class.java)
                    if (it || viewModel.getAutoEnableSelectedAppsEnabled()) {
                        context.startService(intent)
                    } else {
                        context.stopService(intent)
                    }
                }
            })
            
            val autoEnableChecked = remember { mutableStateOf(viewModel.getAutoEnableSelectedAppsEnabled()) }
            val autoEnableSummary = stringResource(R.string.sidebar_auto_enable_selected_apps_summary)
            SwitchPreference(
                model = object : SwitchPreferenceModel {
                    override val title = stringResource(R.string.sidebar_auto_enable_selected_apps)
                    override val summary = { autoEnableSummary }
                    override val checked = { autoEnableChecked.value }
                    override val changeable = { viewModel.isEnabled }
                    override val onCheckedChange: (Boolean) -> Unit = {
                        autoEnableChecked.value = it
                        viewModel.setAutoEnableSelectedAppsEnabled(it)
                        val intent = Intent(context, SidebarMonitorService::class.java)
                        if (it || mainChecked.value) {
                            context.startService(intent)
                        } else {
                            context.stopService(intent)
                        }
                    }
                }
            )
            
            if (autoEnableChecked.value) {
                val perAppConfigSummary = stringResource(R.string.sidebar_per_app_config_summary)
                Preference(
                    model = object : PreferenceModel {
                        override val title = stringResource(R.string.sidebar_per_app_config)
                        override val summary = { perAppConfigSummary }
                        override val onClick = {
                            val intent = Intent(context, SidebarPerAppConfigActivity::class.java)
                            context.startActivity(intent)
                        }
                    }
                )
            }
            
            if (mainChecked.value) {
                SidebarSettingSwitch(
                    title = stringResource(R.string.sidebar_predicted_apps),
                    summary = stringResource(R.string.sidebar_predicted_apps_summary),
                    isChecked = viewModel.getPredictedAppsEnabled(),
                    onCheckedChange = { viewModel.setPredictedAppsEnabled(it) }
                )
            }
            
            if (mainChecked.value) {
                SidebarAppList(viewModel)
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
