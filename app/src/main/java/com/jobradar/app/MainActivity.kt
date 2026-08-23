package com.jobradar.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.jobradar.app.navigation.JobRadarNavHost
import com.jobradar.app.ui.theme.JobRadarTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as JobRadarApp

        setContent {
            JobRadarTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    JobRadarNavHost(
                        feedRepository = app.feedRepository,
                        trackerRepository = app.trackerRepository,
                    )
                }
            }
        }
    }
}
