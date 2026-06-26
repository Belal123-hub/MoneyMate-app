package com.example.moneymate.utils

import android.net.Uri
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okio.Buffer

/**
 * Loads avatars from disk cache first, then http(s) with the authenticated OkHttp client.
 */
class AuthenticatedOkHttpFetcher(
    private val avatarUrlPath: String?,
    private val urls: List<String>,
    private val callFactory: Call.Factory,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        var lastError: Throwable? = null
        for (url in urls) {
            try {
                return fetchSingle(url)
            } catch (e: Throwable) {
                lastError = e
                val is404 = e.message?.contains("404") == true
                AvatarDiagnostics.logError(
                    "CoilFetch",
                    "Attempt failed for $url${if (is404) " (will try fallback)" else ""}",
                    e
                )
                if (!is404) throw e
            }
        }
        throw lastError ?: IllegalStateException("All avatar URLs failed: $urls")
    }

    private suspend fun fetchSingle(url: String): FetchResult {
        val request = Request.Builder().url(url).build()
        val response = withContext(Dispatchers.IO) {
            callFactory.newCall(request).execute()
        }

        val contentType = response.header("Content-Type")
        AvatarDiagnostics.log(
            "CoilFetch",
            "GET $url -> HTTP ${response.code}, Content-Type=$contentType"
        )

        if (!response.isSuccessful) {
            val errorPeek = runCatching { response.peekBody(512).string() }.getOrNull()
            response.close()
            throw IllegalStateException("HTTP ${response.code} for $url body=$errorPeek")
        }

        val body = response.body ?: throw IllegalStateException("Empty body for $url")
        val mimeType = body.contentType()?.toString() ?: "image/jpeg"
        val bytes = withContext(Dispatchers.IO) { body.bytes() }
        response.close()

        avatarUrlPath?.let { path ->
            AvatarDiskCache.saveFromNetwork(options.context, path, bytes)
        }
        AvatarDiagnostics.log("CoilFetch", "Success $url bytes=${bytes.size}")

        val buffer = Buffer().apply { write(bytes) }
        return SourceResult(
            source = ImageSource(buffer, options.context),
            mimeType = mimeType,
            dataSource = DataSource.NETWORK
        )
    }

    class Factory(private val callFactory: Call.Factory) : Fetcher.Factory<Any> {
        override fun create(data: Any, options: Options, imageLoader: ImageLoader): Fetcher? {
            when (data) {
                is AuthenticatedImage -> {
                    return AuthenticatedOkHttpFetcher(
                        avatarUrlPath = data.avatarUrlPath,
                        urls = data.urls,
                        callFactory = callFactory,
                        options = options
                    )
                }
                else -> {
                    val url = when (data) {
                        is String -> data
                        is Uri -> data.toString()
                        is okhttp3.HttpUrl -> data.toString()
                        is java.net.URL -> data.toString()
                        else -> return null
                    }
                    if (!url.startsWith("http://", ignoreCase = true) &&
                        !url.startsWith("https://", ignoreCase = true)
                    ) {
                        return null
                    }
                    if (url.toHttpUrlOrNull() == null) return null
                    return AuthenticatedOkHttpFetcher(
                        avatarUrlPath = null,
                        urls = listOf(url),
                        callFactory = callFactory,
                        options = options
                    )
                }
            }
        }
    }
}
