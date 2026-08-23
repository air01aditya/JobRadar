package com.jobradar.app.data.discovery

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object JobHttpClient {
    // A browser-like User-Agent is required by some sources (e.g. RemoteOK 403s bare clients).
    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
