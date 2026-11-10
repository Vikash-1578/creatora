package com.creatora.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.creatora.app.ui.CreatoraApp
import com.creatora.app.ui.theme.CreatoraTheme

/** The single activity of the app. All screens are Compose destinations inside [CreatoraApp]. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CreatoraTheme {
                CreatoraApp()
            }
        }
    }
}
