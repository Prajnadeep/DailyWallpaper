package com.dailywallpaper.data.model

import java.time.Instant

data class WallpaperStatus(
    val isEnabled: Boolean = true,
    val lastRunTime: Instant? = null,
    val lastRunSuccess: Boolean? = null,
    val lastRunError: String? = null
)

sealed class WallpaperResult {
    object Success : WallpaperResult()
    data class Failure(val reason: String, val cause: Throwable? = null) : WallpaperResult()
}
