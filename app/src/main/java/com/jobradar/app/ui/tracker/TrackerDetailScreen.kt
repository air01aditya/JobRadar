package com.jobradar.app.ui.tracker

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.jobradar.app.data.TrackedStatus
import com.jobradar.app.ui.theme.JobRadarError
import com.jobradar.app.ui.theme.JobRadarMutedText
import com.jobradar.app.util.formatDeadline
import com.jobradar.app.util.formatSavedAt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerDetailScreen(
    viewModel: TrackerViewModel,
    jobId: String,
    onBack: () -> Unit,
) {
    val trackedJobs by viewModel.trackedJobs.collectAsState()
    val job = trackedJobs.find { it.id == jobId } ?: run {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val context = LocalContext.current

    var notes by remember(job.id) { mutableStateOf(job.notes) }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = job.deadlineAt)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(text = job.title, style = MaterialTheme.typography.headlineSmall)
        Text(text = job.company, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = "Saved ${formatSavedAt(job.createdAt)}",
            style = MaterialTheme.typography.bodySmall,
            color = JobRadarMutedText,
            modifier = Modifier.padding(top = 4.dp),
        )

        Text(text = "Status", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 20.dp))
        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TrackedStatus.entries.forEach { status ->
                FilterChip(
                    selected = job.status == status.name,
                    onClick = { viewModel.updateStatus(job.id, status) },
                    label = { Text(status.label) },
                )
            }
        }

        Text(text = "Deadline", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 20.dp))
        Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showDatePicker = true }) {
                Text(if (job.deadlineAt != null) formatDeadline(job.deadlineAt) else "Set a deadline")
            }
            if (job.deadlineAt != null) {
                TextButton(onClick = { viewModel.updateDeadline(job.id, null) }) {
                    Text("Clear")
                }
            }
        }

        Text(text = "Notes", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 20.dp))
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            minLines = 3,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        Button(
            onClick = { viewModel.updateNotes(job.id, notes) },
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text("Save notes")
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = 28.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(job.url))) }) {
                Text("Open posting")
            }
            TextButton(onClick = {
                viewModel.delete(job)
                onBack()
            }) {
                Text("Remove from tracker", color = JobRadarError)
            }
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateDeadline(job.id, datePickerState.selectedDateMillis)
                    showDatePicker = false
                }) { Text("Set") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
