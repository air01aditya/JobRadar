package com.jobradar.app.data.firestore

import com.google.firebase.Timestamp

data class JobPosting(
    val id: String = "",
    val source: String = "",
    val title: String = "",
    val company: String = "",
    val location: String = "",
    val url: String = "",
    val isRemote: Boolean = false,
    val postedAt: String? = null,
    val firstSeenAt: Timestamp? = null,
)
