package com.dailywallpaper.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dailywallpaper.data.local.WallpaperPreferences
import com.dailywallpaper.data.model.WallpaperResult
import com.dailywallpaper.data.repository.WallpaperRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Hilt-injected CoroutineWorker.
 *
 * WorkManager handles:
 *  - Retry with backoff (EXPONENTIAL, max 3 attempts)
 *  - Network constraint enforcement
 *
 * This worker is scheduled daily via UniquePeriodicWork and rescheduled on boot.
 */
@HiltWorker
class WallpaperWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val wallpaperRepository: WallpaperRepository,
    private val preferences: WallpaperPreferences
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "WallpaperWorker"
        const val WORK_NAME = "daily_wallpaper_work"
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "Worker started. Attempt: ${runAttemptCount + 1}")

        // Honour the user's enable/disable toggle
        val isEnabled = try {
            readEnabled()
        } catch (e: Exception) {
            Log.w(TAG, "Could not read preferences, defaulting to enabled", e)
            true
        }

        if (!isEnabled) {
            Log.i(TAG, "Wallpaper update disabled by user — skipping.")
            return Result.success()
        }

        return when (val result = wallpaperRepository.downloadAndApplyLockScreenWallpaper()) {
            is WallpaperResult.Success -> {
                Log.i(TAG, "Wallpaper applied successfully.")
                preferences.recordSuccess()
                Result.success()
            }
            is WallpaperResult.Failure -> {
                Log.e(TAG, "Wallpaper update failed: ${result.reason}", result.cause)
                preferences.recordFailure(result.reason)

                if (runAttemptCount < 2) {
                    Log.i(TAG, "Retrying (attempt ${runAttemptCount + 1})")
                    Result.retry()
                } else {
                    Log.e(TAG, "All retries exhausted. Marking as failure.")
                    Result.failure()
                }
            }
        }
    }

    /**
     * DataStore reads are via Flow; we snapshot the current value synchronously-ish
     * by collecting once. Using runBlocking here would deadlock; instead we rely on
     * the suspend context we're already in.
     */
    private suspend fun readEnabled(): Boolean {
        var enabled = true
        // Fast path: collect one emission from the preferences flow
        preferences.wallpaperStatusFlow.collect { status ->
            enabled = status.isEnabled
            return@collect
        }
        return enabled
    }
}
