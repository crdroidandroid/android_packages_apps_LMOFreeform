/*
 * SPDX-FileCopyrightText: 2026 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */
 
package com.libremobileos.sidebar.ui.sidebar

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.libremobileos.sidebar.app.SidebarApplication
import com.libremobileos.sidebar.ui.theme.SidebarTheme

class SidebarCustomizationActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val sharedPrefs = getSharedPreferences(SidebarApplication.CONFIG, Context.MODE_PRIVATE)
        
        setContent {
            SidebarTheme {
                SidebarCustomizationSettingsPage(
                    sharedPrefs = sharedPrefs,
                    onBack = { finish() },
                    onSettingChanged = {
                        // Handle setting changes if needed beyond SharedPreferences
                    }
                )
            }
        }
    }
}
