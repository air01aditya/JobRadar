package com.jobradar.app.ui.tracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jobradar.app.data.TrackedJobEntity
import com.jobradar.app.data.TrackedStatus
import com.jobradar.app.data.tracker.TrackerRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TrackerViewModel(private val repository: TrackerRepository) : ViewModel() {

    val trackedJobs: StateFlow<List<TrackedJobEntity>> = repository.observeTrackedJobs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addManual(title: String, company: String, url: String) {
        viewModelScope.launch { repository.addManual(title, company, url) }
    }

    fun updateStatus(id: String, status: TrackedStatus) {
        viewModelScope.launch { repository.updateStatus(id, status) }
    }

    fun updateNotes(id: String, notes: String) {
        viewModelScope.launch { repository.updateNotes(id, notes) }
    }

    fun updateDeadline(id: String, deadlineAt: Long?) {
        viewModelScope.launch { repository.updateDeadline(id, deadlineAt) }
    }

    fun delete(job: TrackedJobEntity) {
        viewModelScope.launch { repository.delete(job) }
    }

    class Factory(private val repository: TrackerRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TrackerViewModel(repository) as T
    }
}
