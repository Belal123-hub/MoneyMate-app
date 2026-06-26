package com.example.moneymate.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.moneymate.utils.AvatarDiagnostics
import com.example.moneymate.utils.AvatarDiskCache
import com.example.moneymate.utils.AvatarImageCache
import com.example.moneymate.utils.AvatarPrefetcher
import com.example.moneymate.utils.Config
import java.io.File

@Composable
fun ProfileAvatar(
    avatarUrl: String?,
    fullName: String?,
    modifier: Modifier = Modifier,
    localFilePath: String? = null,
    initialsFontSize: TextUnit = 18.sp,
    contentDescription: String = "Profile Avatar"
) {
    val context = LocalContext.current
    var diskCacheVersion by remember { mutableIntStateOf(0) }
    val authenticatedImage = avatarUrl?.let { Config.buildAuthenticatedAvatarImage(it) }
    val diskCachedPath = remember(avatarUrl, diskCacheVersion) {
        avatarUrl?.let { AvatarDiskCache.getCachedFile(context, it)?.absolutePath }
            ?: AvatarImageCache.lastLocalDiskPath?.takeIf { path -> File(path).exists() }
    }

    androidx.compose.runtime.LaunchedEffect(avatarUrl) {
        val path = avatarUrl?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        if (AvatarDiskCache.getCachedFile(context, path) == null) {
            AvatarPrefetcher.prefetchIfNeeded(context, path)
            if (AvatarDiskCache.getCachedFile(context, path) != null) {
                diskCacheVersion++
            }
        }
    }
    val remoteUrl = authenticatedImage?.urls?.firstOrNull()
    val imageModel = when {
        !localFilePath.isNullOrBlank() && File(localFilePath).exists() -> File(localFilePath)
        !diskCachedPath.isNullOrBlank() -> File(diskCachedPath)
        authenticatedImage != null -> authenticatedImage
        else -> null
    }

    val initials = remember(fullName) {
        fullName
            ?.split(" ")
            ?.filter { it.isNotBlank() }
            ?.take(2)
            ?.joinToString("") { it.firstOrNull()?.uppercaseChar()?.toString() ?: "" }
            ?.take(2)
            ?.ifBlank { null }
            ?: "U"
    }

    val cacheKey = remoteUrl ?: avatarUrl ?: localFilePath ?: "no-avatar"

    androidx.compose.runtime.LaunchedEffect(cacheKey) {
        AvatarDiagnostics.log(
            "ProfileAvatar",
            "raw=$avatarUrl disk=$diskCachedPath urls=${authenticatedImage?.urls} model=$imageModel local=$localFilePath"
        )
    }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color(0xFF4D6BFA)),
        contentAlignment = Alignment.Center
    ) {
        if (imageModel != null) {
            key(cacheKey) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(imageModel)
                        .crossfade(true)
                        .diskCacheKey(cacheKey)
                        .memoryCacheKey(cacheKey)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .listener(
                            onStart = {
                                AvatarDiagnostics.log("ProfileAvatar", "Loading $imageModel")
                            },
                            onSuccess = { _, _ ->
                                AvatarDiagnostics.log("ProfileAvatar", "Loaded $imageModel")
                            },
                            onError = { _, result ->
                                AvatarDiagnostics.logError(
                                    "ProfileAvatar",
                                    "Failed $imageModel — ${result.throwable.message}",
                                    result.throwable
                                )
                            }
                        )
                        .build(),
                    contentDescription = contentDescription,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                    loading = {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        }
                    },
                    error = {
                        AvatarInitials(initials = initials, fontSize = initialsFontSize)
                    },
                    success = {
                        SubcomposeAsyncImageContent()
                    }
                )
            }
        } else {
            AvatarInitials(initials = initials, fontSize = initialsFontSize)
        }
    }
}

@Composable
private fun AvatarInitials(
    initials: String,
    fontSize: TextUnit
) {
    Text(
        text = initials,
        color = Color.White,
        fontSize = fontSize,
        fontWeight = FontWeight.Bold
    )
}
