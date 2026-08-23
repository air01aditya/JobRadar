package com.jobradar.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jobradar.app.data.firestore.FeedRepository
import com.jobradar.app.data.tracker.TrackerRepository
import com.jobradar.app.ui.feed.FeedScreen
import com.jobradar.app.ui.feed.FeedViewModel
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
    feedRepository: FeedRepository,
    trackerRepository: TrackerRepository,
    navController: NavHostController = rememberNavController(),
) {
    val trackerViewModel: TrackerViewModel = viewModel(factory = TrackerViewModel.Factory(trackerRepository))
    val coroutineScope = rememberCoroutineScope()
    val currentDestination = navController.currentBackStackEntryAsState().value?.destination

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
                val feedViewModel: FeedViewModel = viewModel(factory = FeedViewModel.Factory(feedRepository))
                FeedScreen(
                    viewModel = feedViewModel,
                    onAddToTracker = { job ->
                        coroutineScope.launch { trackerRepository.addFromFeed(job) }
                    },
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
