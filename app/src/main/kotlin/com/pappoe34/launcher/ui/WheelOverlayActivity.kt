package com.pappoe34.launcher.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pappoe34.launcher.ui.theme.LauncherTheme

/** Translucent pop-up wheel launched from the home-screen widget; sits over whatever launcher is active. */
class WheelOverlayActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LauncherTheme {
                OverlayWheelScreen(onDismiss = { finish() })
            }
        }
    }
}
