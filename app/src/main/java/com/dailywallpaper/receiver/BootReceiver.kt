package com.dailywallpaper.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.dailywallpaper.scheduler.WallpaperScheduler
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Reschedules the periodic worker after device reboot.
 *
 * WorkManager periodic work survives reboots internally via its own DB,
 * but explicitly rescheduling ensures correctness after OEM battery killers
 * or WorkManager DB corruption on older ROMs.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var wallpaperScheduler: WallpaperScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != "android.intent.action.LOCKED_BOOT_COMPLETED") {
            return
        }

        Log.i("BootReceiver", "Boot completed. Rescheduling wallpaper worker...")
        // Use KEEP — don't displace an already-correct schedule
        wallpaperScheduler.scheduleIfEnabled()
    }
}
