package com.jobradar.app.ui.feed

import android.content.Context
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

sealed interface FeedState {
    data object Loading : FeedState
    data class Loaded(val jobs: List<JobDiscoveryEntity>) : FeedState
}

private const val REFRESH_COOLDOWN_SECONDS = 90

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

    init {
        viewModelScope.launch {
            repository.observeJobs().collect { jobs -> _state.value = FeedState.Loaded(jobs) }
        }
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
