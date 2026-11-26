package com.avans.rentacar

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

// Android 17 (API 37) needs this permission to reach an address on the local network, like 10.0.2.2.
private const val LocalNetworkApi = 37
private const val LocalNetwork = "android.permission.ACCESS_LOCAL_NETWORK"

class MainActivity : ComponentActivity() {
    // Started over once granted, so the first calls (which failed without it) run again.
    private val askLocalNetwork = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) recreate()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // shortcut: only needed while the API runs on a local network; drop it once the API is hosted on the internet
        if (Build.VERSION.SDK_INT >= LocalNetworkApi && checkSelfPermission(LocalNetwork) != PackageManager.PERMISSION_GRANTED) {
            askLocalNetwork.launch(LocalNetwork)
        }
        setContent {
            App()
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
