package com.jobradar.app.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jobradar.app.data.firestore.FeedRepository
import com.jobradar.app.data.firestore.JobPosting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

sealed interface FeedState {
    data object Loading : FeedState
    data class Loaded(val jobs: List<JobPosting>) : FeedState
    data class Error(val message: String) : FeedState
}

class FeedViewModel(private val repository: FeedRepository) : ViewModel() {

    private val _state = MutableStateFlow<FeedState>(FeedState.Loading)
    val state: StateFlow<FeedState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeJobs()
                .catch { e -> _state.value = FeedState.Error(e.message ?: "Failed to load feed") }
                .collect { jobs -> _state.value = FeedState.Loaded(jobs) }
        }
    }

    class Factory(private val repository: FeedRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            FeedViewModel(repository) as T
    }
}
