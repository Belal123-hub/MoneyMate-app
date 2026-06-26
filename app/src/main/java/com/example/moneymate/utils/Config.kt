package com.example.moneymate.utils

import com.example.data.network.common.Network

object Config {
    /** Same host as Retrofit [Network.BASE_URL] — do not hardcode a different IP here. */
    val BASE_URL: String
        get() = Network.BASE_URL.trimEnd('/')

    fun buildImageUrl(relativePath: String?): String? {
        val path = relativePath?.trim().orEmpty()
        if (path.isEmpty()) return null
        if (path.startsWith("http://", ignoreCase = true) ||
            path.startsWith("https://", ignoreCase = true)
        ) {
            return path
        }
        val base = BASE_URL
        return if (path.startsWith("/")) "$base$path" else "$base/$path"
    }

    /**
     * Builds an authenticated Coil model for the current user's avatar.
     * Tries the API download route first, then static paths from [avatarUrl].
     */
    fun buildAuthenticatedAvatarImage(avatarUrl: String?): AuthenticatedImage? {
        val path = avatarUrl?.trim().orEmpty()
        if (path.isEmpty()) return null

        val urls = linkedSetOf<String>()

        // API route first (static /static/avatars often returns 404 on the server)
        urls.add("$BASE_URL/api/users/me/avatar")

        buildImageUrl(path)?.let { urls.add(it) }

        if (path.startsWith("/static/")) {
            buildImageUrl(path.removePrefix("/static"))?.let { urls.add(it) }
            buildImageUrl("/api$path")?.let { urls.add(it) }
        }

        return AuthenticatedImage(avatarUrlPath = path, urls = urls.toList())
    }
}
