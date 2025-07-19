/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */
 

package com.libremobileos.sidebar.ui.sidebar

import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SidebarCustomizationSettingsPage(
    sharedPrefs: SharedPreferences,
    onBack: () -> Unit = {},
    onSettingChanged: () -> Unit = {}
) {
    var transparency by remember { mutableStateOf(sharedPrefs.getFloat("slider_transparency", 1.0f)) }
    var sliderLength by remember { mutableStateOf(sharedPrefs.getInt("slider_length", 200)) }
    var position by remember { mutableStateOf(sharedPrefs.getInt("sideline_position_x", 1)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        Text(text = "Customize Sidebar Slider", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Transparency: ${"%.2f".format(transparency)}")
        Slider(
            value = transparency,
            onValueChange = { transparency = it },
            valueRange = 0.1f..1.0f,
            steps = 8,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                sharedPrefs.edit().putFloat("slider_transparency", transparency).apply()
                onSettingChanged()
            },
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text("Apply Transparency")
        }
        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Slider Length (Height): $sliderLength px")
        Slider(
            value = sliderLength.toFloat(),
            onValueChange = { sliderLength = it.toInt() },
            valueRange = 100f..500f,
            steps = 8,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                sharedPrefs.edit().putInt("slider_length", sliderLength).apply()
                onSettingChanged()
            },
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text("Apply Length")
        }
        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Position: " + if (position == 1) "Right" else "Left")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Button(onClick = {
                position = -1
                sharedPrefs.edit().putInt("sideline_position_x", position).apply()
                onSettingChanged()
            }) {
                Text("Left")
            }
            Button(onClick = {
                position = 1
                sharedPrefs.edit().putInt("sideline_position_x", position).apply()
                onSettingChanged()
            }) {
                Text("Right")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onBack,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text("Back")
        }
    }
}
