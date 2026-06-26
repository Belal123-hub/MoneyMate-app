package com.example.moneymate.utils

/**
 * Coil model type handled only by [AuthenticatedOkHttpFetcher].
 * [avatarUrlPath] is the API-relative path (e.g. /static/avatars/uuid.jpg) used for disk cache lookup.
 */
data class AuthenticatedImage(
    val avatarUrlPath: String,
    val urls: List<String>
) {
    init {
        require(urls.isNotEmpty()) { "AuthenticatedImage requires at least one URL" }
    }
}
