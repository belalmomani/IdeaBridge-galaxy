package com.ideabridge.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ideabridge.app.ui.IdeaBridgeApp
import com.ideabridge.app.ui.theme.IdeaBridgeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            IdeaBridgeTheme {
                IdeaBridgeApp()
            }
        }
    }
}
