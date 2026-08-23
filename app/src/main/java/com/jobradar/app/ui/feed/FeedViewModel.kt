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

enum class RefreshTarget { BOARDS, TELEGRAM }

private const val REFRESH_COOLDOWN_SECONDS = 90

class FeedViewModel(
    private val repository: JobDiscoveryRepository,
    private val appContext: Context,
) : ViewModel() {

    private val _state = MutableStateFlow<FeedState>(FeedState.Loading)
    val state: StateFlow<FeedState> = _state.asStateFlow()

    private val _isRefreshingBoards = MutableStateFlow(false)
    val isRefreshingBoards: StateFlow<Boolean> = _isRefreshingBoards.asStateFlow()
    private val _cooldownBoards = MutableStateFlow(0)
    val cooldownBoards: StateFlow<Int> = _cooldownBoards.asStateFlow()

    private val _isRefreshingTelegram = MutableStateFlow(false)
    val isRefreshingTelegram: StateFlow<Boolean> = _isRefreshingTelegram.asStateFlow()
    private val _cooldownTelegram = MutableStateFlow(0)
    val cooldownTelegram: StateFlow<Int> = _cooldownTelegram.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeJobs().collect { jobs -> _state.value = FeedState.Loaded(jobs) }
        }
    }

    fun refresh(target: RefreshTarget) {
        when (target) {
            RefreshTarget.BOARDS -> refresh(
                isRefreshing = _isRefreshingBoards,
                cooldown = _cooldownBoards,
                start = { JobCheckWorker.runBoardsNow(appContext) },
                workName = JobCheckWorker.BOARDS_ONE_TIME_WORK_NAME,
            )
            RefreshTarget.TELEGRAM -> refresh(
                isRefreshing = _isRefreshingTelegram,
                cooldown = _cooldownTelegram,
                start = { JobCheckWorker.runTelegramNow(appContext) },
                workName = JobCheckWorker.TELEGRAM_ONE_TIME_WORK_NAME,
            )
        }
    }

    private fun refresh(
        isRefreshing: MutableStateFlow<Boolean>,
        cooldown: MutableStateFlow<Int>,
        start: () -> Unit,
        workName: String,
    ) {
        if (isRefreshing.value || cooldown.value > 0) return
        isRefreshing.value = true
        start()
        viewModelScope.launch {
            WorkManager.getInstance(appContext)
                .getWorkInfosForUniqueWorkFlow(workName)
                .first { infos -> infos.firstOrNull()?.state?.let(WorkInfo.State::isFinished) == true }
            isRefreshing.value = false
            cooldown.value = REFRESH_COOLDOWN_SECONDS
            while (cooldown.value > 0) {
                delay(1_000)
                cooldown.value -= 1
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
