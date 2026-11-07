package com.example.securityguard

import android.graphics.drawable.Drawable

data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val isSystemApp: Boolean,
    val riskyPermissions: List<String> = emptyList()
)
