package com.avans.rentacar

import androidx.compose.runtime.Composable
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled
import platform.UIKit.UIDevice

class IOSPlatform : Platform {
    override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
}

actual fun getPlatform(): Platform = IOSPlatform()

@Composable
actual fun prefersReducedMotion(): Boolean = UIAccessibilityIsReduceMotionEnabled()

// The iOS simulator shares this computer's network. A real iPhone needs the computer's address on the wifi.
actual fun defaultServerUrl(): String = "http://localhost:8080"
