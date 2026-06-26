package com.example.moneymate.utils

import android.util.Log

/**
 * Holds the latest avatar path from a successful upload so Home can show it
 * immediately even if a later API refresh is slow or briefly omits avatar_url.
 */
object AvatarImageCache {
    private const val TAG = "MoneyMateAvatar"

    @Volatile
    var lastUploadedAvatarUrl: String? = null
        private set

    @Volatile
    var lastLocalDiskPath: String? = null
        private set

    fun onAvatarUploaded(relativeOrAbsoluteUrl: String?, localDiskPath: String? = null) {
        lastUploadedAvatarUrl = relativeOrAbsoluteUrl?.trim()?.takeIf { it.isNotEmpty() }
        lastLocalDiskPath = localDiskPath?.takeIf { it.isNotEmpty() }
        Log.i(TAG, "Cached avatar_url=$lastUploadedAvatarUrl disk=$lastLocalDiskPath")
    }

    fun clear() {
        lastUploadedAvatarUrl = null
        lastLocalDiskPath = null
        Log.i(TAG, "Cleared avatar cache")
    }

    fun resolve(
        preferredFromEvent: String? = null,
        fromApi: String? = null,
        previousInUi: String? = null
    ): String? {
        return when {
            !preferredFromEvent.isNullOrBlank() -> preferredFromEvent
            !fromApi.isNullOrBlank() -> fromApi
            !lastUploadedAvatarUrl.isNullOrBlank() -> lastUploadedAvatarUrl
            !previousInUi.isNullOrBlank() -> previousInUi
            else -> fromApi
        }
    }
}
