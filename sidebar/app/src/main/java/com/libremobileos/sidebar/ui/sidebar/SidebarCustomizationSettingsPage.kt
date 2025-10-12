/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */
 

package com.libremobileos.sidebar.ui.sidebar

import android.content.SharedPreferences
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

import com.libremobileos.sidebar.R

@Composable
fun SidebarCustomizationSettingsPage(
    sharedPrefs: SharedPreferences,
    onBack: () -> Unit = {},
    onSettingChanged: () -> Unit = {}
) {
    BackHandler { onBack() }

    var transparency by remember { mutableStateOf(sharedPrefs.getFloat("slider_transparency", 1.0f)) }
    var sliderLength by remember { mutableStateOf(sharedPrefs.getInt("slider_length", 200)) }
    var position by remember { mutableStateOf(sharedPrefs.getInt("sideline_position_x", 1)) }
    var columnCount by remember { mutableStateOf(sharedPrefs.getInt("sidebar_columns", 1)) }
    var iconSize by remember { mutableStateOf(sharedPrefs.getInt("sidebar_icon_size", 50)) }
    var iconPadding by remember { mutableStateOf(sharedPrefs.getInt("sidebar_icon_padding", 8)) }
    var columnSpacing by remember { mutableStateOf(sharedPrefs.getInt("sidebar_column_spacing", 4)) }
    var cornerRadius by remember { mutableStateOf(sharedPrefs.getFloat("sidebar_corner_radius", 16f)) }
    var backgroundTransparency by remember { mutableStateOf(sharedPrefs.getFloat("sidebar_background_transparency", 1.0f)) }
    var showShadow by remember { mutableStateOf(sharedPrefs.getBoolean("sidebar_show_shadow", true)) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.sidebar_customization_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }

        // Slider Settings Section
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.sidebar_section_slider),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    // Transparency
                    Text(text = stringResource(R.string.sidebar_transparency_value, transparency))
                    Slider(
                        value = transparency,
                        onValueChange = { 
                            transparency = it
                            sharedPrefs.edit().putFloat("slider_transparency", transparency).apply()
                            onSettingChanged()
                        },
                        valueRange = 0.1f..1.0f,
                        steps = 8,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Slider Length
                    Text(text = stringResource(R.string.sidebar_length_value, sliderLength))
                    Slider(
                        value = sliderLength.toFloat(),
                        onValueChange = { 
                            sliderLength = it.toInt()
                            sharedPrefs.edit().putInt("slider_length", sliderLength).apply()
                            onSettingChanged()
                        },
                        valueRange = 100f..500f,
                        steps = 19,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Position
                    Text(
                        text = stringResource(R.string.sidebar_position_label),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        FilterChip(
                            onClick = {
                                position = -1
                                sharedPrefs.edit().putInt("sideline_position_x", position).apply()
                                onSettingChanged()
                            },
                            label = { Text(stringResource(R.string.sidebar_left)) },
                            selected = position == -1
                        )
                        FilterChip(
                            onClick = {
                                position = 1
                                sharedPrefs.edit().putInt("sideline_position_x", position).apply()
                                onSettingChanged()
                            },
                            label = { Text(stringResource(R.string.sidebar_right)) },
                            selected = position == 1
                        )
                    }
                }
            }
        }

        // Layout Settings Section
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.sidebar_section_layout),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    // Column Count
                    Text(
                        text = pluralStringResource(
                            R.plurals.sidebar_columns_value, columnCount, columnCount
                        ),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        for (i in 1..3) {
                            FilterChip(
                                onClick = {
                                    columnCount = i
                                    sharedPrefs.edit().putInt("sidebar_columns", columnCount).apply()
                                    onSettingChanged()
                                },
                                label = {
                                    Text(
                                        pluralStringResource(
                                            R.plurals.sidebar_columns_value, i, i
                                        )
                                    )
                                },
                                selected = columnCount == i
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Icon Size
                    Text(text = stringResource(R.string.sidebar_icon_size_value, iconSize))
                    Slider(
                        value = iconSize.toFloat(),
                        onValueChange = { 
                            iconSize = it.toInt()
                            sharedPrefs.edit().putInt("sidebar_icon_size", iconSize).apply()
                            onSettingChanged()
                        },
                        valueRange = 30f..80f,
                        steps = 9,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Icon Padding
                    Text(text = stringResource(R.string.sidebar_icon_padding_value, iconPadding))
                    Text(
                        text = stringResource(R.string.sidebar_icon_padding_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Slider(
                        value = iconPadding.toFloat(),
                        onValueChange = { 
                            iconPadding = it.toInt()
                            sharedPrefs.edit().putInt("sidebar_icon_padding", iconPadding).apply()
                            onSettingChanged()
                        },
                        valueRange = 4f..20f,
                        steps = 15,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Column Spacing
                    if (columnCount > 1) {
                        Text(text = stringResource(R.string.sidebar_column_spacing_value, columnSpacing))
                        Text(
                            text = stringResource(R.string.sidebar_column_spacing_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Slider(
                            value = columnSpacing.toFloat(),
                            onValueChange = { 
                                columnSpacing = it.toInt()
                                sharedPrefs.edit().putInt("sidebar_column_spacing", columnSpacing).apply()
                                onSettingChanged()
                            },
                            valueRange = 1f..12f,
                            steps = 10,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Corner Radius
                    Text(text = stringResource(R.string.sidebar_corner_radius_value, cornerRadius.toInt()))
                    Slider(
                        value = cornerRadius,
                        onValueChange = { 
                            cornerRadius = it
                            sharedPrefs.edit().putFloat("sidebar_corner_radius", cornerRadius).apply()
                            onSettingChanged()
                        },
                        valueRange = 0f..32f,
                        steps = 15,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Background Transparency
                    Text(text = stringResource(R.string.sidebar_bg_transparency_value, backgroundTransparency))
                    Slider(
                        value = backgroundTransparency,
                        onValueChange = { 
                            backgroundTransparency = it
                            sharedPrefs.edit().putFloat("sidebar_background_transparency", backgroundTransparency).apply()
                            onSettingChanged()
                        },
                        valueRange = 0.1f..1.0f,
                        steps = 8,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Visual Effects Section
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.sidebar_section_visual),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    // Shadow Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Switch(
                            checked = showShadow,
                            onCheckedChange = { 
                                showShadow = it
                                sharedPrefs.edit().putBoolean("sidebar_show_shadow", showShadow).apply()
                                onSettingChanged()
                            }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(text = stringResource(R.string.sidebar_drop_shadow_title))
                            Text(
                                text = stringResource(R.string.sidebar_drop_shadow_summary),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Reset Button
        item {
            OutlinedButton(
                onClick = {
                    // Reset to defaults
                    transparency = 1.0f
                    sliderLength = 200
                    position = 1
                    columnCount = 1
                    iconSize = 50
                    iconPadding = 8
                    columnSpacing = 4
                    cornerRadius = 16f
                    backgroundTransparency = 1.0f
                    showShadow = true
                    
                    sharedPrefs.edit()
                        .putFloat("slider_transparency", transparency)
                        .putInt("slider_length", sliderLength)
                        .putInt("sideline_position_x", position)
                        .putInt("sidebar_columns", columnCount)
                        .putInt("sidebar_icon_size", iconSize)
                        .putInt("sidebar_icon_padding", iconPadding)
                        .putInt("sidebar_column_spacing", columnSpacing)
                        .putFloat("sidebar_corner_radius", cornerRadius)
                        .putFloat("sidebar_background_transparency", backgroundTransparency)
                        .putBoolean("sidebar_show_shadow", showShadow)
                        .apply()
                    onSettingChanged()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Text(stringResource(R.string.sidebar_reset_defaults))
            }
        }
    }
}
