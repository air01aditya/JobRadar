package com.jobradar.app.data.discovery

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "discovered_jobs")
data class JobDiscoveryEntity(
    @PrimaryKey val id: String,
    val source: String,
    val title: String,
    val company: String,
    val location: String,
    val url: String,
    val isRemote: Boolean,
    val postedAtEpochMillis: Long?,
    val firstSeenAtEpochMillis: Long,
)
