package com.jobradar.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jobradar.app.data.discovery.FilterSettings
import com.jobradar.app.data.discovery.parseWordList
import com.jobradar.app.ui.theme.JobRadarMutedText

/**
 * Edits a draft; nothing changes until Save. The feed re-filters instantly on save because
 * these settings are applied when jobs are shown, not when they're fetched.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    initial: FilterSettings,
    onSave: (FilterSettings) -> Unit,
    onBack: () -> Unit,
) {
    var draft by remember { mutableStateOf(initial) }
    var citiesText by remember { mutableStateOf(initial.cities.joinToString(", ")) }
    var extraRolesText by remember { mutableStateOf(initial.extraRoles.joinToString(", ")) }
    var blockedText by remember { mutableStateOf(initial.blockedWords.joinToString(", ")) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Job filters", style = MaterialTheme.typography.headlineSmall)
        }
        Text(
            text = "These decide what your feed shows and what you get notified about. Saving updates the feed right away.",
            style = MaterialTheme.typography.bodyMedium,
            color = JobRadarMutedText,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )

        Section("Experience")
        SettingSwitch(
            title = "Only entry-level postings",
            description = "Show a job only if it says fresher, trainee, intern, junior, graduate or 0-1 years. " +
                "Turn off to also see plain titles like \"Software Engineer\" that don't ask for more experience than below.",
            checked = draft.requireEntryProof,
            onCheckedChange = { draft = draft.copy(requireEntryProof = it) },
        )
        Label("Most experience a job can ask for")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterSettings.MAX_YEARS_OPTIONS.forEach { years ->
                FilterChip(
                    selected = draft.maxYears == years,
                    onClick = { draft = draft.copy(maxYears = years) },
                    label = { Text(if (years == 0) "None" else "$years yr") },
                )
            }
        }

        Section("Freshness")
        Label("Posted within")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterSettings.MAX_AGE_OPTIONS.forEach { days ->
                FilterChip(
                    selected = draft.maxAgeDays == days,
                    onClick = { draft = draft.copy(maxAgeDays = days) },
                    label = { Text("$days days") },
                )
            }
        }

        Section("Location")
        SettingSwitch(
            title = "Show remote jobs",
            description = "Only remote jobs open to India or worldwide — never \"US only\".",
            checked = draft.showRemote,
            onCheckedChange = { draft = draft.copy(showRemote = it) },
        )
        OutlinedTextField(
            value = citiesText,
            onValueChange = { citiesText = it },
            label = { Text("Only these cities") },
            placeholder = { Text("e.g. Pune, Bengaluru") },
            supportingText = { Text("Comma-separated. Leave empty for all of India.") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )

        Section("Roles")
        OutlinedTextField(
            value = extraRolesText,
            onValueChange = { extraRolesText = it },
            label = { Text("Also include these job titles") },
            placeholder = { Text("e.g. game developer, ui designer") },
            supportingText = { Text("Applies to jobs found from the next check onwards.") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = blockedText,
            onValueChange = { blockedText = it },
            label = { Text("Hide jobs whose title contains") },
            placeholder = { Text("e.g. bpo, night shift") },
            supportingText = { Text("Comma-separated.") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )

        Spacer(Modifier.height(20.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            OutlinedButton(onClick = {
                draft = FilterSettings()
                citiesText = ""
                extraRolesText = ""
                blockedText = ""
            }) { Text("Reset to defaults") }
            Spacer(Modifier.width(10.dp))
            Button(onClick = {
                onSave(
                    draft.copy(
                        cities = parseWordList(citiesText),
                        extraRoles = parseWordList(extraRolesText),
                        blockedWords = parseWordList(blockedText),
                    ),
                )
            }) { Text("Save") }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Section(title: String) {
    HorizontalDivider(modifier = Modifier.padding(top = 20.dp, bottom = 12.dp))
    Text(title, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun Label(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingSwitch(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = JobRadarMutedText)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
