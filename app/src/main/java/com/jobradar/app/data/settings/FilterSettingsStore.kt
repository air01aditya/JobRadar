package com.jobradar.app.data.settings

import android.content.Context
import com.jobradar.app.data.discovery.FilterSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Persists [FilterSettings] on-device and exposes them as a live stream for the feed. */
class FilterSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("filter_settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<FilterSettings> = _settings.asStateFlow()

    fun current(): FilterSettings = _settings.value

    fun update(settings: FilterSettings) {
        prefs.edit()
            .putBoolean(KEY_REQUIRE_PROOF, settings.requireEntryProof)
            .putInt(KEY_MAX_YEARS, settings.maxYears)
            .putInt(KEY_MAX_AGE_DAYS, settings.maxAgeDays)
            .putBoolean(KEY_SHOW_REMOTE, settings.showRemote)
            .putString(KEY_CITIES, settings.cities.joinToString("\n"))
            .putString(KEY_EXTRA_ROLES, settings.extraRoles.joinToString("\n"))
            .putString(KEY_BLOCKED, settings.blockedWords.joinToString("\n"))
            .apply()
        _settings.value = settings
    }

    private fun load(): FilterSettings {
        val defaults = FilterSettings()
        return FilterSettings(
            requireEntryProof = prefs.getBoolean(KEY_REQUIRE_PROOF, defaults.requireEntryProof),
            maxYears = prefs.getInt(KEY_MAX_YEARS, defaults.maxYears),
            maxAgeDays = prefs.getInt(KEY_MAX_AGE_DAYS, defaults.maxAgeDays),
            showRemote = prefs.getBoolean(KEY_SHOW_REMOTE, defaults.showRemote),
            cities = prefs.getList(KEY_CITIES),
            extraRoles = prefs.getList(KEY_EXTRA_ROLES),
            blockedWords = prefs.getList(KEY_BLOCKED),
        )
    }

    private fun android.content.SharedPreferences.getList(key: String): List<String> =
        getString(key, "").orEmpty().split("\n").filter { it.isNotBlank() }

    private companion object {
        const val KEY_REQUIRE_PROOF = "require_entry_proof"
        const val KEY_MAX_YEARS = "max_years"
        const val KEY_MAX_AGE_DAYS = "max_age_days"
        const val KEY_SHOW_REMOTE = "show_remote"
        const val KEY_CITIES = "cities"
        const val KEY_EXTRA_ROLES = "extra_roles"
        const val KEY_BLOCKED = "blocked_words"
    }
}
