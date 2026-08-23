package com.jobradar.app.data.firestore

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class FeedRepository(private val firestore: FirebaseFirestore) {

    fun observeJobs(limit: Long = 100): Flow<List<JobPosting>> = callbackFlow {
        val registration = firestore.collection("jobs")
            .orderBy("firstSeenAt", Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val jobs = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(JobPosting::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(jobs)
            }
        awaitClose { registration.remove() }
    }
}
