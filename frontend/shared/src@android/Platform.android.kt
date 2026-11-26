package com.avans.rentacar

import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
}

actual fun getPlatform(): Platform = AndroidPlatform()

@Composable
actual fun prefersReducedMotion(): Boolean =
    Settings.Global.getFloat(LocalContext.current.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

// The Android emulator reaches this computer at 10.0.2.2. A real phone needs the computer's address on the wifi.
actual fun defaultServerUrl(): String = "http://10.0.2.2:8080"
