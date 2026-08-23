package com.jobradar.app.data.discovery

data class RawJob(
    val source: String,
    val sourceId: String,
    val title: String,
    val company: String,
    val location: String,
    val url: String,
    val description: String,
    val isRemote: Boolean,
    val postedAtEpochMillis: Long? = null,
)
