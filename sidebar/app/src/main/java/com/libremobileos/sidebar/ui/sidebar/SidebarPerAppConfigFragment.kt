/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */
 
package com.libremobileos.sidebar.ui.sidebar

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.navigation.compose.rememberNavController
import com.android.settingslib.spa.framework.compose.localNavController
import com.android.settingslib.spa.framework.compose.rememberDrawablePainter
import com.android.settingslib.spa.framework.theme.SettingsDimension
import com.android.settingslib.spa.widget.preference.SwitchPreference
import com.android.settingslib.spa.widget.preference.SwitchPreferenceModel
import com.android.settingslib.spa.widget.scaffold.SettingsScaffold
import com.android.settingslib.spa.widget.ui.Category
import com.libremobileos.sidebar.R
import com.libremobileos.sidebar.app.SidebarApplication
import com.libremobileos.sidebar.ui.theme.SidebarTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SidebarPerAppConfigFragment : Fragment() {

    companion object {
        const val PREF_AUTO_APPS = "sidebar_auto_apps"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                SidebarTheme {
                    val navController = rememberNavController()
                    CompositionLocalProvider(navController.localNavController()) {
                        SidebarPerAppConfigScreen()
                    }
                }
            }
        }
    }
}

@Composable
fun SidebarPerAppConfigContent() {
    val navController = rememberNavController()
    CompositionLocalProvider(navController.localNavController()) {
        SidebarPerAppConfigScreen()
    }
}

@Composable
fun SidebarPerAppConfigScreen() {
    val context = LocalContext.current
    val sharedPrefs = context.getSharedPreferences(SidebarApplication.CONFIG, Context.MODE_PRIVATE)
    
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    
    LaunchedEffect(Unit) {
        val loadedApps = withContext(Dispatchers.IO) {
            loadInstalledApps(context)
        }
        apps = loadedApps
    }
    
    val filteredApps = remember(apps, searchQuery) {
        if (searchQuery.isBlank()) {
            apps
        } else {
            apps.filter { app ->
                app.label.contains(searchQuery, ignoreCase = true) ||
                app.packageName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    SettingsScaffold(
        title = stringResource(R.string.sidebar_per_app_config)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Text(
                text = stringResource(R.string.sidebar_auto_enable_selected_apps_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    bottom = 16.dp
                )
            )
            
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search apps") },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Search"
                    )
                },
                trailingIcon = if (searchQuery.isNotEmpty()) {
                    {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "Clear search"
                            )
                        }
                    }
                } else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = 8.dp
                    ),
                singleLine = true
            )
            
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 16.dp
                )
            ) {
                items(filteredApps) { appInfo ->
                    SidebarPerAppListItem(
                        appInfo = appInfo,
                        sharedPrefs = sharedPrefs
                    )
                }
            }
        }
    }
}

@Composable
fun SidebarPerAppListItem(
    appInfo: AppInfo,
    sharedPrefs: android.content.SharedPreferences
) {
    val autoApps = sharedPrefs.getStringSet(SidebarPerAppConfigFragment.PREF_AUTO_APPS, emptySet()) ?: emptySet()
    var isChecked by rememberSaveable { mutableStateOf(autoApps.contains(appInfo.packageName)) }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        // App icon
        Image(
            painter = rememberDrawablePainter(appInfo.icon),
            contentDescription = appInfo.label,
            modifier = Modifier.size(48.dp)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // App info column
        Column(
            modifier = Modifier.weight(1f)
        ) {
            // App name - now properly themed
            Text(
                text = appInfo.label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            // Package name - now properly themed
            Text(
                text = appInfo.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        // Switch
        Switch(
            checked = isChecked,
            onCheckedChange = { newValue ->
                isChecked = newValue
                val currentAutoApps = sharedPrefs.getStringSet(SidebarPerAppConfigFragment.PREF_AUTO_APPS, emptySet())?.toMutableSet() ?: mutableSetOf()
                
                if (newValue) {
                    currentAutoApps.add(appInfo.packageName)
                } else {
                    currentAutoApps.remove(appInfo.packageName)
                }
                
                sharedPrefs.edit()
                    .putStringSet(SidebarPerAppConfigFragment.PREF_AUTO_APPS, currentAutoApps)
                    .apply()
            }
        )
    }
}

data class AppInfo(
    val label: String,
    val packageName: String,
    val icon: android.graphics.drawable.Drawable
)

private suspend fun loadInstalledApps(context: Context): List<AppInfo> = withContext(Dispatchers.IO) {
    val pm = context.packageManager
    val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
    
    apps.asSequence()
        .filter { app ->
            // Filter out system apps and the current app
            (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0 && 
            app.packageName != context.packageName
        }
        .map { app ->
            AppInfo(
                label = app.loadLabel(pm).toString(),
                packageName = app.packageName,
                icon = app.loadIcon(pm)
            )
        }
        .sortedBy { it.label.lowercase() }
        .toList()
}
