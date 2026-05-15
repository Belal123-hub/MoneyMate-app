package com.example.moneymate.utils

object Config {
    // Same host/port as `Network.BASE_URL` (images / relative asset URLs).
    const val BASE_URL = "http://10.20.105.58:5143"

    // Helper function to build full URL for relative paths
    fun buildImageUrl(relativePath: String?): String? {
        return relativePath?.let { path ->
            if (path.startsWith("http")) {
                path // Already a full URL
            } else if (path.startsWith("/")) {
                "$BASE_URL$path" // Prepend base URL to relative path
            } else {
                "$BASE_URL/$path" // Prepend base URL and slash
            }
        }
    }
}