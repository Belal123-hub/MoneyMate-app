package com.example.moneymate.utils

import android.content.Context
import java.io.File

/**
 * Persists avatar JPEGs locally because the API returns /static/avatars/... paths
 * that are not always served over HTTP (404). After upload we cache the file here.
 */
object AvatarDiskCache {
    private const val DIR = "avatar_cache"

    private fun cacheDir(context: Context): File =
        File(context.cacheDir, DIR).apply { mkdirs() }

    private fun fileNameFromAvatarUrl(avatarUrl: String): String =
        avatarUrl.trim().substringAfterLast('/').ifBlank { "avatar.jpg" }

    fun fileFor(context: Context, avatarUrl: String): File =
        File(cacheDir(context), fileNameFromAvatarUrl(avatarUrl))

    fun getCachedFile(context: Context, avatarUrl: String?): File? {
        if (avatarUrl.isNullOrBlank()) return null
        val file = fileFor(context, avatarUrl)
        return file.takeIf { it.exists() && it.length() > 0L }
    }

    fun cacheFromUpload(context: Context, source: File, avatarUrl: String?): File? {
        val url = avatarUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (!source.exists()) return null
        return runCatching {
            val dest = fileFor(context, url)
            source.copyTo(dest, overwrite = true)
            AvatarDiagnostics.log("DiskCache", "Saved upload -> ${dest.absolutePath} (${dest.length()} bytes)")
            dest
        }.onFailure {
            AvatarDiagnostics.logError("DiskCache", "Failed to save upload", it)
        }.getOrNull()
    }

    fun saveFromNetwork(context: Context, avatarUrl: String, bytes: ByteArray): File? {
        if (bytes.isEmpty()) return null
        return runCatching {
            val dest = fileFor(context, avatarUrl)
            dest.writeBytes(bytes)
            AvatarDiagnostics.log("DiskCache", "Saved network -> ${dest.absolutePath} (${bytes.size} bytes)")
            dest
        }.onFailure {
            AvatarDiagnostics.logError("DiskCache", "Failed to save network bytes", it)
        }.getOrNull()
    }
}
