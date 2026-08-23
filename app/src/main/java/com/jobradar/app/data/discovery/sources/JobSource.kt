package com.jobradar.app.data.discovery.sources

import com.jobradar.app.data.discovery.RawJob

interface JobSource {
    val name: String
    suspend fun fetch(): List<RawJob>
}
