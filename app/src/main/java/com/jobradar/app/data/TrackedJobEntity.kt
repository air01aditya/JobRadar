package com.jobradar.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracked_jobs")
data class TrackedJobEntity(
    @PrimaryKey val id: String,
    val title: String,
    val company: String,
    val url: String,
    val status: String,
    val notes: String = "",
    val deadlineAt: Long? = null,
    val createdAt: Long,
)
