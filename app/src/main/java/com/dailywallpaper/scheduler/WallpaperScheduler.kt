package com.dailywallpaper.scheduler

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.dailywallpaper.data.local.WallpaperPreferences
import com.dailywallpaper.worker.WallpaperWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WallpaperScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: WallpaperPreferences
) {
    companion object {
        private const val TAG = "WallpaperScheduler"
        private val TARGET_HOUR = LocalTime.of(7, 0) // 7:00 AM
    }

    /**
     * Schedules the periodic worker only if the feature is enabled.
     * Safe to call multiple times — uses KEEP policy to avoid duplicates.
     */
    fun scheduleIfEnabled() {
        val isEnabled = runBlocking {
            preferences.wallpaperStatusFlow.first().isEnabled
        }
        if (isEnabled) {
            schedule()
        } else {
            Log.d(TAG, "Feature disabled — skipping schedule.")
        }
    }

    /**
     * Schedules (or replaces) the daily worker.
     * Uses KEEP policy: existing work is preserved, preventing duplicate scheduling.
     * Use [forceReschedule] to replace with updated timing.
     */
    fun schedule(forceReschedule: Boolean = false) {
        val initialDelay = computeInitialDelayToNextTarget()
        Log.i(TAG, "Scheduling wallpaper worker. Initial delay: ${initialDelay}ms " +
                "(~${initialDelay / 3_600_000}h ${(initialDelay % 3_600_000) / 60_000}m)")

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<WallpaperWorker>(
            repeatInterval = 24,
            repeatIntervalTimeUnit = TimeUnit.HOURS
        )
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                Duration.ofMinutes(15)
            )
            .build()

        val policy = if (forceReschedule) {
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE
        } else {
            ExistingPeriodicWorkPolicy.KEEP
        }

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WallpaperWorker.WORK_NAME, policy, workRequest)

        Log.i(TAG, "Worker enqueued with policy=$policy")
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WallpaperWorker.WORK_NAME)
        Log.i(TAG, "Wallpaper worker cancelled.")
    }

    /**
     * Computes milliseconds until the next 7:00 AM occurrence.
     * If current time is past 7:00 AM, targets 7:00 AM tomorrow.
     */
    private fun computeInitialDelayToNextTarget(): Long {
        val now = LocalDateTime.now()
        val todayTarget = now.toLocalDate().atTime(TARGET_HOUR)
        val nextTarget = if (now.isBefore(todayTarget)) todayTarget else todayTarget.plusDays(1)
        return Duration.between(now, nextTarget).toMillis().coerceAtLeast(0L)
    }
}
