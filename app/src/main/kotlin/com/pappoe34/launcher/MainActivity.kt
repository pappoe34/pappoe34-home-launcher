package com.pappoe34.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pappoe34.launcher.ui.HomeScreen
import com.pappoe34.launcher.ui.theme.LauncherTheme

/** The HOME activity. Lives as a singleTask and never finishes on Back. */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // A launcher must swallow Back: there is nowhere "behind" the home screen.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })

        setContent {
            LauncherTheme {
                HomeScreen()
            }
        }
    }
}
