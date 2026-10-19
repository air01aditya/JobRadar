package com.jobradar.app.ui.feed

import android.content.Context
import android.os.PowerManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.jobradar.app.data.discovery.JobDiscoveryEntity
import com.jobradar.app.data.discovery.JobDiscoveryRepository
import com.jobradar.app.work.JobCheckWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

sealed interface FeedState {
    data object Loading : FeedState
    data class Loaded(val jobs: List<JobDiscoveryEntity>) : FeedState
}

private const val REFRESH_COOLDOWN_SECONDS = 90

// Checks are every 15 minutes, but Android can hold them back for a few hours while the phone
// sleeps. Longer than this and the phone is most likely blocking JobRadar in the background.
val STALE_CHECK_MILLIS = TimeUnit.HOURS.toMillis(6)

private const val PREFS = "feed"
private const val KEY_BACKGROUND_PROMPT_DISMISSED = "background_prompt_dismissed"

class FeedViewModel(
    private val repository: JobDiscoveryRepository,
    private val appContext: Context,
) : ViewModel() {

    private val _state = MutableStateFlow<FeedState>(FeedState.Loading)
    val state: StateFlow<FeedState> = _state.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()
    private val _cooldown = MutableStateFlow(0)
    val cooldown: StateFlow<Int> = _cooldown.asStateFlow()

    val lastCheckAt: StateFlow<Long> = repository.lastCheckAt

    private val _showBackgroundPrompt = MutableStateFlow(false)
    val showBackgroundPrompt: StateFlow<Boolean> = _showBackgroundPrompt.asStateFlow()

    init {
        // The background check may not have run for hours (phone asleep), so clear out
        // expired jobs on open — the feed should never show something past the freshness window.
        viewModelScope.launch { repository.cleanUp() }
        viewModelScope.launch {
            repository.observeJobs().collect { jobs -> _state.value = FeedState.Loaded(jobs) }
        }
    }

    /**
     * Many phones (Oppo, Vivo, Xiaomi, OnePlus) stop apps in the background unless the user
     * allows it, and then alerts silently stop. Ask until the user allows it or says "Not now",
     * and ask again anyway if checks have actually stalled. Called on every return to the feed,
     * so it updates as soon as the user comes back from Settings.
     */
    fun recheckBackgroundAccess() {
        val power = appContext.getSystemService(PowerManager::class.java)
        val allowed = power.isIgnoringBatteryOptimizations(appContext.packageName)
        val dismissed = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_BACKGROUND_PROMPT_DISMISSED, false)
        val lastCheck = lastCheckAt.value
        val stalled = lastCheck > 0 && System.currentTimeMillis() - lastCheck > STALE_CHECK_MILLIS
        _showBackgroundPrompt.value = !allowed && (!dismissed || stalled)
    }

    fun dismissBackgroundPrompt() {
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_BACKGROUND_PROMPT_DISMISSED, true).apply()
        _showBackgroundPrompt.value = false
    }

    fun refresh() {
        if (_isRefreshing.value || _cooldown.value > 0) return
        _isRefreshing.value = true
        JobCheckWorker.runFeedNow(appContext)
        viewModelScope.launch {
            WorkManager.getInstance(appContext)
                .getWorkInfosForUniqueWorkFlow(JobCheckWorker.FEED_ONE_TIME_WORK_NAME)
                .first { infos -> infos.firstOrNull()?.state?.let(WorkInfo.State::isFinished) == true }
            _isRefreshing.value = false
            _cooldown.value = REFRESH_COOLDOWN_SECONDS
            while (_cooldown.value > 0) {
                delay(1_000)
                _cooldown.value -= 1
            }
        }
    }

    class Factory(
        private val repository: JobDiscoveryRepository,
        private val appContext: Context,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            FeedViewModel(repository, appContext) as T
    }
}
