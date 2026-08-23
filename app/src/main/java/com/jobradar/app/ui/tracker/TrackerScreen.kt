package com.jobradar.app.ui.tracker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jobradar.app.data.TrackedJobEntity
import com.jobradar.app.data.TrackedStatus
import com.jobradar.app.ui.theme.JobRadarAccent
import com.jobradar.app.ui.theme.JobRadarMutedText

@Composable
fun TrackerScreen(
    viewModel: TrackerViewModel,
    onOpenJob: (String) -> Unit,
    onAddManual: () -> Unit,
) {
    val trackedJobs by viewModel.trackedJobs.collectAsState()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onAddManual) {
                Icon(Icons.Default.Add, contentDescription = "Add a job manually")
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (trackedJobs.isEmpty()) {
                Text(
                    text = "Nothing tracked yet.\nTap a job in the Feed to add it, or use + to add one manually (e.g. from a LinkedIn/Naukri alert).",
                    color = JobRadarMutedText,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(trackedJobs, key = { it.id }) { job ->
                        TrackedJobCard(job = job, onClick = { onOpenJob(job.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackedJobCard(job: TrackedJobEntity, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = job.title, style = MaterialTheme.typography.titleMedium)
            Text(text = job.company, style = MaterialTheme.typography.bodyMedium, color = JobRadarMutedText)
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = JobRadarAccent.copy(alpha = 0.12f),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text(
                    text = TrackedStatus.valueOf(job.status).label,
                    style = MaterialTheme.typography.labelSmall,
                    color = JobRadarAccent,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}
