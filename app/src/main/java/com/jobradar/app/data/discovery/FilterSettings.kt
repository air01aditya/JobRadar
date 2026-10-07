package com.jobradar.app.data.discovery

/** The user's own preferences — applied when the feed is shown, so changing them is instant. */
data class FilterSettings(
    val requireEntryProof: Boolean = true,
    val maxYears: Int = 1,
    val maxAgeDays: Int = 14,
    val showRemote: Boolean = true,
    val cities: List<String> = emptyList(),
    val extraRoles: List<String> = emptyList(),
    val blockedWords: List<String> = emptyList(),
) {
    companion object {
        val MAX_YEARS_OPTIONS = listOf(0, 1, 2, 3)
        val MAX_AGE_OPTIONS = listOf(3, 7, 14, 30)
    }
}

/** "pune, Bangalore ,, " -> ["pune", "bangalore"] */
fun parseWordList(text: String): List<String> =
    text.split(',', '\n').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()
