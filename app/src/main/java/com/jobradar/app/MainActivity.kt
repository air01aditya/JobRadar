package com.jobradar.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.jobradar.app.data.tracker.SharedJob
import com.jobradar.app.data.tracker.SharedJobParser
import com.jobradar.app.navigation.JobRadarNavHost
import com.jobradar.app.ui.theme.JobRadarTheme

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()

        val app = application as JobRadarApp
        // Only on a fresh launch — after rotation the share has already been handled.
        val sharedJob = if (savedInstanceState == null) readSharedJob(intent) else null

        setContent {
            JobRadarTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    JobRadarNavHost(
                        jobDiscoveryRepository = app.jobDiscoveryRepository,
                        trackerRepository = app.trackerRepository,
                        filterSettingsStore = app.filterSettingsStore,
                        sharedJob = sharedJob,
                        onSharedJobSaved = {
                            Toast.makeText(this, "Saved to your JobRadar tracker", Toast.LENGTH_SHORT).show()
                            finish() // back to Naukri / LinkedIn / Indeed, where the user was browsing
                        },
                    )
                }
            }
        }
    }

    private fun readSharedJob(intent: Intent?): SharedJob? {
        if (intent?.action != Intent.ACTION_SEND) return null
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
        Log.i("JobShare", "received share — subject=[$subject] text=[$text]")
        val job = SharedJobParser.parse(text, subject)
        if (job == null) Toast.makeText(this, "No job link found in what was shared", Toast.LENGTH_LONG).show()
        return job
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
