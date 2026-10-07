package com.jobradar.app.ui.tracker

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jobradar.app.data.tracker.SharedJob
import com.jobradar.app.ui.theme.JobRadarMutedText

@Composable
fun AddManualJobScreen(
    viewModel: TrackerViewModel,
    onDone: () -> Unit,
    shared: SharedJob? = null,
) {
    var title by remember(shared) { mutableStateOf(shared?.title.orEmpty()) }
    var company by remember(shared) { mutableStateOf(shared?.company.orEmpty()) }
    var url by remember(shared) { mutableStateOf(shared?.url.orEmpty()) }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            text = if (shared != null) "Save job from ${shared.sourceLabel}" else "Add a job manually",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = if (shared != null) {
                "Check the title and company — shared text doesn't always say which is which."
            } else {
                "For postings from LinkedIn, Naukri, or anywhere else JobRadar doesn't auto-discover — " +
                    "paste the link, or use Share → JobRadar from those apps."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = JobRadarMutedText,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Job title") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = company,
            onValueChange = { company = it },
            label = { Text("Company") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Job posting link") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )

        Button(
            onClick = {
                val notes = shared?.let { "Shared from ${it.sourceLabel}" }.orEmpty()
                viewModel.addManual(title.trim(), company.trim(), url.trim(), notes, onSaved = onDone)
            },
            enabled = title.isNotBlank() && url.isNotBlank(),
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Text("Add to tracker")
        }
    }
}
