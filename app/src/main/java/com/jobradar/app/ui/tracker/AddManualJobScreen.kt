package com.jobradar.app.ui.tracker

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jobradar.app.ui.theme.JobRadarMutedText

@Composable
fun AddManualJobScreen(
    viewModel: TrackerViewModel,
    onDone: () -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var company by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(text = "Add a job manually", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "For postings from LinkedIn, Naukri, or anywhere else JobRadar doesn't auto-discover — paste the link and it'll live in your tracker alongside everything else.",
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
                viewModel.addManual(title.trim(), company.trim(), url.trim())
                onDone()
            },
            enabled = title.isNotBlank() && url.isNotBlank(),
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Text("Add to tracker")
        }
    }
}
