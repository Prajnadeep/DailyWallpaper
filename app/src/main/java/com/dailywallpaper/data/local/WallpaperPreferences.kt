package com.dailywallpaper.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dailywallpaper.data.model.WallpaperStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "wallpaper_prefs")

@Singleton
class WallpaperPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val KEY_ENABLED = booleanPreferencesKey("wallpaper_enabled")
        private val KEY_LAST_RUN_TIME = longPreferencesKey("last_run_time")
        private val KEY_LAST_RUN_SUCCESS = booleanPreferencesKey("last_run_success")
        private val KEY_LAST_RUN_ERROR = stringPreferencesKey("last_run_error")
    }

    val wallpaperStatusFlow: Flow<WallpaperStatus> = context.dataStore.data.map { prefs ->
        val lastRunEpoch = prefs[KEY_LAST_RUN_TIME]
        WallpaperStatus(
            isEnabled = prefs[KEY_ENABLED] ?: true,
            lastRunTime = lastRunEpoch?.let { Instant.ofEpochMilli(it) },
            lastRunSuccess = prefs[KEY_LAST_RUN_SUCCESS],
            lastRunError = prefs[KEY_LAST_RUN_ERROR]
        )
    }

    suspend fun setEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ENABLED] = enabled
        }
    }

    suspend fun recordSuccess() {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_RUN_TIME] = Instant.now().toEpochMilli()
            prefs[KEY_LAST_RUN_SUCCESS] = true
            prefs.remove(KEY_LAST_RUN_ERROR)
        }
    }

    suspend fun recordFailure(error: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_RUN_TIME] = Instant.now().toEpochMilli()
            prefs[KEY_LAST_RUN_SUCCESS] = false
            prefs[KEY_LAST_RUN_ERROR] = error
        }
    }

    suspend fun isEnabled(): Boolean {
        var enabled = true
        context.dataStore.edit { prefs ->
            enabled = prefs[KEY_ENABLED] ?: true
        }
        return enabled
    }
}
