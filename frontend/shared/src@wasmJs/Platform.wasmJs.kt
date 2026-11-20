package com.avans.rentacar

import androidx.compose.runtime.Composable
import kotlinx.browser.window

class WasmPlatform : Platform {
    override val name: String = "Web with Kotlin/Wasm"
    override val isWeb = true
}

actual fun getPlatform(): Platform = WasmPlatform()

@Composable
actual fun prefersReducedMotion(): Boolean = window.matchMedia("(prefers-reduced-motion: reduce)").matches
