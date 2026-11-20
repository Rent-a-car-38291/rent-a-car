package com.avans.rentacar

import androidx.compose.runtime.Composable

interface Platform {
    val name: String
    /** The web portal, which skips Welcome. */
    val isWeb: Boolean get() = false
}

expect fun getPlatform(): Platform

/** True when the person turned animations off (Android), Reduce Motion on (iOS) or prefers-reduced-motion (web). */
@Composable
expect fun prefersReducedMotion(): Boolean
