package com.example.moneymate.utils

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koin.core.qualifier.named
import org.koin.java.KoinJavaComponent

object AvatarPrefetcher {

    suspend fun prefetchIfNeeded(context: Context, avatarUrl: String) {
        if (AvatarDiskCache.getCachedFile(context, avatarUrl) != null) return

        val urls = Config.buildAuthenticatedAvatarImage(avatarUrl)?.urls ?: return
        val client: OkHttpClient = KoinJavaComponent.getOrNull(
            OkHttpClient::class.java,
            named("coilOkHttp")
        ) ?: KoinJavaComponent.get(OkHttpClient::class.java)

        withContext(Dispatchers.IO) {
            for (url in urls) {
                runCatching {
                    val response = client.newCall(Request.Builder().url(url).build()).execute()
                    try {
                        if (response.isSuccessful) {
                            val bytes = response.body?.bytes()
                            if (bytes != null && bytes.isNotEmpty()) {
                                AvatarDiskCache.saveFromNetwork(context, avatarUrl, bytes)
                                AvatarDiagnostics.log("Prefetch", "Saved from $url")
                                return@withContext
                            }
                        } else {
                            AvatarDiagnostics.log(
                                "Prefetch",
                                "Skip $url HTTP ${response.code}"
                            )
                        }
                    } finally {
                        response.close()
                    }
                }.onFailure {
                    AvatarDiagnostics.logError("Prefetch", "Failed $url", it)
                }
            }
        }
    }
}
