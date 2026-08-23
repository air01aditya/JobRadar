package com.jobradar.app.ui.feed

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jobradar.app.data.discovery.JobDiscoveryEntity
import com.jobradar.app.ui.theme.JobRadarAccent
import com.jobradar.app.ui.theme.JobRadarMutedText
import com.jobradar.app.util.relativeTime
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

private val TABS = listOf("India", "Remote", "Telegram")
private const val TELEGRAM_SOURCE_PREFIX = "Telegram · "
private const val ACTIVE_FILTERS_LABEL = "Filtering: Analyst + Software/QA roles · Fresher (0-1 yr) · India & Remote"
private val URGENT_COLOR = Color(0xFF2E9E5B)
private val FRESH_COLOR = Color(0xFFC9A227)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    viewModel: FeedViewModel,
    trackedJobIds: Set<String>,
    onAddToTracker: (JobDiscoveryEntity) -> Unit,
    onJobOpened: (JobDiscoveryEntity) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val pagerState = rememberPagerState(pageCount = { TABS.size })
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Text(
            text = ACTIVE_FILTERS_LABEL,
            style = MaterialTheme.typography.labelSmall,
            color = JobRadarMutedText,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        )

        TabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = JobRadarAccent,
        ) {
            TABS.forEachIndexed { index, label ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(label) },
                )
            }
        }

        val current = state
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            // Each tab refreshes only the sources it actually shows — India/Remote share
            // the job-board group, Telegram has its own, so pulling one never waits on the other.
            val target = if (page == 2) RefreshTarget.TELEGRAM else RefreshTarget.BOARDS
            val isRefreshing by (if (target == RefreshTarget.TELEGRAM) viewModel.isRefreshingTelegram else viewModel.isRefreshingBoards)
                .collectAsState()
            val cooldownSeconds by (if (target == RefreshTarget.TELEGRAM) viewModel.cooldownTelegram else viewModel.cooldownBoards)
                .collectAsState()

            Column(modifier = Modifier.fillMaxSize()) {
                AnimatedVisibility(visible = cooldownSeconds > 0, enter = fadeIn(), exit = fadeOut()) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = JobRadarAccent.copy(alpha = 0.12f),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = "✨ Refresh again in ${cooldownSeconds / 60}:${(cooldownSeconds % 60).toString().padStart(2, '0')}",
                            style = MaterialTheme.typography.labelSmall,
                            color = JobRadarAccent,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }

                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = { viewModel.refresh(target) },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    when (current) {
                        is FeedState.Loading -> {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        }
                        is FeedState.Loaded -> {
                            // Telegram gets its own bucket entirely, regardless of remote/India —
                            // it's not split by that axis, it's split by source.
                            val (telegramJobs, otherJobs) = current.jobs.partition { it.source.startsWith(TELEGRAM_SOURCE_PREFIX) }
                            val filtered = when (page) {
                                0 -> otherJobs.filter { !it.isRemote }
                                1 -> otherJobs.filter { it.isRemote }
                                else -> telegramJobs
                            }
                            if (filtered.isEmpty()) {
                                Text(
                                    text = "No matching jobs yet — pull down to check now.",
                                    color = JobRadarMutedText,
                                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                                )
                            } else {
                                JobList(
                                    jobs = filtered,
                                    trackedJobIds = trackedJobIds,
                                    onAddToTracker = onAddToTracker,
                                    onJobOpened = onJobOpened,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JobList(
    jobs: List<JobDiscoveryEntity>,
    trackedJobIds: Set<String>,
    onAddToTracker: (JobDiscoveryEntity) -> Unit,
    onJobOpened: (JobDiscoveryEntity) -> Unit,
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(jobs, key = { it.id }) { job ->
            JobCard(
                job = job,
                isTracked = job.id in trackedJobIds,
                onClick = {
                    if (job.url.isNotBlank()) {
                        onJobOpened(job)
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(job.url)))
                    }
                },
                onAddToTracker = { onAddToTracker(job) },
            )
        }
    }
}

private fun urgencyColor(job: JobDiscoveryEntity): Color? {
    val reference = job.postedAtEpochMillis ?: job.firstSeenAtEpochMillis
    val age = System.currentTimeMillis() - reference
    return when {
        age < TimeUnit.HOURS.toMillis(2) -> URGENT_COLOR
        age < TimeUnit.HOURS.toMillis(24) -> FRESH_COLOR
        else -> null
    }
}

@Composable
private fun JobCard(job: JobDiscoveryEntity, isTracked: Boolean, onClick: () -> Unit, onAddToTracker: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp, pressedElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        urgencyColor(job)?.let { color ->
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(color, CircleShape),
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(text = job.title, style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(4.dp))
                    val subtitle = listOf(job.company, job.location.ifBlank { if (job.isRemote) "Remote" else "" })
                        .filter { it.isNotBlank() }
                        .joinToString(" · ")
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = JobRadarMutedText,
                        )
                    }
                }
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onAddToTracker, enabled = !isTracked) {
                    Icon(
                        if (isTracked) Icons.Default.Bookmark else Icons.Default.BookmarkAdd,
                        contentDescription = if (isTracked) "Already in tracker" else "Add to tracker",
                        tint = JobRadarAccent,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = JobRadarAccent.copy(alpha = 0.12f),
                ) {
                    Text(
                        text = job.source,
                        style = MaterialTheme.typography.labelSmall,
                        color = JobRadarAccent,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "found ${relativeTime(job.firstSeenAtEpochMillis)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = JobRadarMutedText,
                    )
                    if (job.postedAtEpochMillis != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "posted ${relativeTime(job.postedAtEpochMillis)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = JobRadarMutedText,
                        )
                    }
                }
            }
        }
    }
}
