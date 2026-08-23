package com.jobradar.app.ui.feed

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jobradar.app.data.firestore.JobPosting
import com.jobradar.app.ui.theme.JobRadarAccent
import com.jobradar.app.ui.theme.JobRadarMutedText
import com.jobradar.app.util.relativeTime

@Composable
fun FeedScreen(viewModel: FeedViewModel, onAddToTracker: (JobPosting) -> Unit) {
    val state by viewModel.state.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        when (val current = state) {
            is FeedState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            is FeedState.Error -> {
                Text(
                    text = "Couldn't load the feed: ${current.message}",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
            }
            is FeedState.Loaded -> {
                if (current.jobs.isEmpty()) {
                    Text(
                        text = "No matching jobs yet — check back soon.",
                        color = JobRadarMutedText,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    )
                } else {
                    JobList(jobs = current.jobs, onAddToTracker = onAddToTracker)
                }
            }
        }
    }
}

@Composable
private fun JobList(jobs: List<JobPosting>, onAddToTracker: (JobPosting) -> Unit) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(jobs, key = { it.id }) { job ->
            JobCard(
                job = job,
                onClick = {
                    if (job.url.isNotBlank()) {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(job.url)))
                    }
                },
                onAddToTracker = { onAddToTracker(job) },
            )
        }
    }
}

@Composable
private fun JobCard(job: JobPosting, onClick: () -> Unit, onAddToTracker: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = job.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "${job.company} · ${job.location.ifBlank { if (job.isRemote) "Remote" else "" }}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = JobRadarMutedText,
                    )
                }
                IconButton(onClick = onAddToTracker) {
                    Icon(Icons.Default.BookmarkAdd, contentDescription = "Add to tracker", tint = JobRadarAccent)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = JobRadarAccent.copy(alpha = 0.12f),
                ) {
                    Text(
                        text = job.source,
                        style = MaterialTheme.typography.labelSmall,
                        color = JobRadarAccent,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                Text(
                    text = relativeTime(job.firstSeenAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = JobRadarMutedText,
                )
            }
        }
    }
}
