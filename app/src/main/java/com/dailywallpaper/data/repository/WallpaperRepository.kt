package com.dailywallpaper.data.repository

import android.app.WallpaperManager
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.dailywallpaper.data.model.WallpaperResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WallpaperRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "WallpaperRepository"
        private const val BASE_URL = "https://lifecal-virid.vercel.app/days"
        private const val DEFAULT_WIDTH = 1080
        private const val DEFAULT_HEIGHT = 1920
    }

    /**
     * Downloads image from the configured URL and applies it as the lock screen wallpaper.
     * Runs entirely on IO dispatcher; returns a typed result instead of throwing.
     */
    suspend fun downloadAndApplyLockScreenWallpaper(): WallpaperResult =
        withContext(Dispatchers.IO) {
            val url = getWallpaperUrl()
            Log.d(TAG, "Starting wallpaper download from $url")

            val imageBytes = downloadImage(url).getOrElse { cause ->
                val msg = "Download failed: ${cause.message}"
                Log.e(TAG, msg, cause)
                return@withContext WallpaperResult.Failure(msg, cause)
            }

            Log.d(TAG, "Downloaded ${imageBytes.size} bytes. Decoding bitmap...")

            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                ?: return@withContext WallpaperResult.Failure("Failed to decode image bytes into bitmap")

            Log.d(TAG, "Bitmap decoded (${bitmap.width}x${bitmap.height}). Applying to lock screen...")

            return@withContext applyLockScreenWallpaper(imageBytes)
        }

    private fun getWallpaperUrl(): String {
        val (width, height) = getScreenDimensions()
        return "$BASE_URL?height=$height&width=$width"
    }

    private fun getScreenDimensions(): Pair<Int, Int> {
        return try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                ?: return Pair(DEFAULT_WIDTH, DEFAULT_HEIGHT)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val windowMetrics = wm.currentWindowMetrics
                val bounds = windowMetrics.bounds
                Pair(bounds.width(), bounds.height())
            } else {
                val displayMetrics = DisplayMetrics()
                @Suppress("DEPRECATION")
                wm.defaultDisplay.getRealMetrics(displayMetrics)
                Pair(displayMetrics.widthPixels, displayMetrics.heightPixels)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get screen dimensions, using defaults: ${e.message}")
            Pair(DEFAULT_WIDTH, DEFAULT_HEIGHT)
        }
    }

    private fun downloadImage(url: String): Result<ByteArray> {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "DailyWallpaper/1.0 Android/${Build.VERSION.SDK_INT}")
            .build()

        return try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(IOException("HTTP ${response.code}: ${response.message}"))
                }
                val body = response.body
                    ?: return Result.failure(IOException("Empty response body"))
                Result.success(body.bytes())
            }
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

    private fun applyLockScreenWallpaper(imageBytes: ByteArray): WallpaperResult {
        val wallpaperManager = WallpaperManager.getInstance(context)

        if (!wallpaperManager.isWallpaperSupported) {
            return WallpaperResult.Failure("WallpaperManager not supported on this device")
        }

        if (!wallpaperManager.isSetWallpaperAllowed) {
            return WallpaperResult.Failure("Setting wallpaper is not allowed (managed device policy?)")
        }

        return try {
            // setBitmap with FLAG_LOCK targets lock screen only (API 24+, we're minSdk 26)
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                ?: return WallpaperResult.Failure("Bitmap decode returned null during apply step")

            wallpaperManager.setBitmap(
                bitmap,
                null,  // visibleCropHint — null = full image
                true,  // allowBackup
                WallpaperManager.FLAG_LOCK
            )

            bitmap.recycle()
            Log.d(TAG, "Lock screen wallpaper applied successfully")
            WallpaperResult.Success
        } catch (e: IOException) {
            val msg = "Failed to set wallpaper: ${e.message}"
            Log.e(TAG, msg, e)
            WallpaperResult.Failure(msg, e)
        } catch (e: SecurityException) {
            val msg = "Permission denied setting wallpaper: ${e.message}"
            Log.e(TAG, msg, e)
            WallpaperResult.Failure(msg, e)
        }
    }
}
