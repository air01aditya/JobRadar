package com.jobradar.app.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jobradar.app.data.TrackedStatus
import com.jobradar.app.data.discovery.JobDiscoveryEntity
import com.jobradar.app.data.discovery.JobDiscoveryRepository
import com.jobradar.app.data.tracker.TrackerRepository
import com.jobradar.app.ui.feed.FeedScreen
import com.jobradar.app.ui.feed.FeedViewModel
import com.jobradar.app.ui.theme.JobRadarMutedText
import com.jobradar.app.ui.tracker.AddManualJobScreen
import com.jobradar.app.ui.tracker.TrackerDetailScreen
import com.jobradar.app.ui.tracker.TrackerScreen
import com.jobradar.app.ui.tracker.TrackerViewModel
import kotlinx.coroutines.launch

private object Routes {
    const val FEED = "feed"
    const val TRACKER = "tracker"
    const val TRACKER_DETAIL = "tracker/{jobId}"
    const val ADD_MANUAL = "tracker_add"

    fun trackerDetail(jobId: String) = "tracker/$jobId"
}

@Composable
fun JobRadarNavHost(
    jobDiscoveryRepository: JobDiscoveryRepository,
    trackerRepository: TrackerRepository,
    navController: NavHostController = rememberNavController(),
) {
    val appContext = LocalContext.current.applicationContext
    val trackerViewModel: TrackerViewModel = viewModel(factory = TrackerViewModel.Factory(trackerRepository, appContext))
    val coroutineScope = rememberCoroutineScope()
    val currentDestination = navController.currentBackStackEntryAsState().value?.destination

    val trackedJobs by trackerViewModel.trackedJobs.collectAsState()
    val trackedJobIds = remember(trackedJobs) { trackedJobs.map { it.id }.toSet() }

    // "Did you apply?" flow: remember the job whose link was just opened, and ask once
    // the user comes back to the app (not immediately — they were away applying).
    var pendingApplyJob by remember { mutableStateOf<JobDiscoveryEntity?>(null) }
    var promptJob by remember { mutableStateOf<JobDiscoveryEntity?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && pendingApplyJob != null) {
                promptJob = pendingApplyJob
                pendingApplyJob = null
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentDestination.isInHierarchy(Routes.FEED),
                        onClick = { navController.navigateToTab(Routes.FEED) },
                        icon = { Icon(Icons.Default.Radar, contentDescription = "Feed") },
                        label = { Text("Feed") },
                    )
                    NavigationBarItem(
                        selected = currentDestination.isInHierarchy(Routes.TRACKER),
                        onClick = { navController.navigateToTab(Routes.TRACKER) },
                        icon = { Icon(Icons.Default.List, contentDescription = "Tracker") },
                        label = { Text("Tracker") },
                    )
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Routes.FEED,
                modifier = Modifier.padding(padding),
            ) {
                composable(Routes.FEED) {
                    val feedViewModel: FeedViewModel = viewModel(
                        factory = FeedViewModel.Factory(jobDiscoveryRepository, appContext),
                    )
                    FeedScreen(
                        viewModel = feedViewModel,
                        trackedJobIds = trackedJobIds,
                        onAddToTracker = { job ->
                            coroutineScope.launch { trackerRepository.addFromFeed(job) }
                        },
                        onJobOpened = { job -> pendingApplyJob = job },
                    )
                }
                composable(Routes.TRACKER) {
                    TrackerScreen(
                        viewModel = trackerViewModel,
                        onOpenJob = { jobId -> navController.navigate(Routes.trackerDetail(jobId)) },
                        onAddManual = { navController.navigate(Routes.ADD_MANUAL) },
                    )
                }
                composable(Routes.TRACKER_DETAIL) { backStackEntry ->
                    val jobId = backStackEntry.arguments?.getString("jobId").orEmpty()
                    TrackerDetailScreen(
                        viewModel = trackerViewModel,
                        jobId = jobId,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(Routes.ADD_MANUAL) {
                    AddManualJobScreen(
                        viewModel = trackerViewModel,
                        onDone = { navController.popBackStack() },
                    )
                }
            }
        }

        val job = promptJob
        AnimatedVisibility(
            visible = job != null,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            if (job != null) {
                ApplyPromptCard(
                    job = job,
                    onApplied = {
                        coroutineScope.launch {
                            trackerRepository.addFromFeed(job)
                            trackerRepository.updateStatus(job.id, TrackedStatus.APPLIED)
                        }
                        promptJob = null
                    },
                    onNotYet = { promptJob = null },
                )
            }
        }
    }
}

@Composable
private fun ApplyPromptCard(job: JobDiscoveryEntity, onApplied: () -> Unit, onNotYet: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 12.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(text = "Welcome back — did you apply?", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "${job.title.ifBlank { "That job" }}${job.company.let { if (it.isNotBlank()) " at $it" else "" }}",
                style = MaterialTheme.typography.bodyMedium,
                color = JobRadarMutedText,
                modifier = Modifier.padding(top = 4.dp, bottom = 18.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onNotYet, modifier = Modifier.weight(1f)) { Text("Not yet") }
                Button(onClick = onApplied, modifier = Modifier.weight(1f)) { Text("Yes, track it") }
            }
        }
    }
}

private fun androidx.navigation.NavDestination?.isInHierarchy(route: String): Boolean =
    this?.hierarchy?.any { it.route == route } ?: false

private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
