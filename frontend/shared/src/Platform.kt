package com.avans.rentacar

import androidx.compose.runtime.Composable

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform

/** True when the person turned animations off (Android), Reduce Motion on (iOS) or prefers-reduced-motion (web). */
@Composable
expect fun prefersReducedMotion(): Boolean

/** Where the API runs by default on this platform. */
expect fun defaultServerUrl(): String
