package com.dailywallpaper.ui.main

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailywallpaper.data.local.WallpaperPreferences
import com.dailywallpaper.data.model.WallpaperResult
import com.dailywallpaper.data.model.WallpaperStatus
import com.dailywallpaper.data.repository.WallpaperRepository
import com.dailywallpaper.scheduler.WallpaperScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ApplyState {
    object Idle : ApplyState()
    object Loading : ApplyState()
    object Success : ApplyState()
    data class Error(val message: String) : ApplyState()
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val preferences: WallpaperPreferences,
    private val repository: WallpaperRepository,
    private val scheduler: WallpaperScheduler
) : ViewModel() {

    companion object {
        private const val TAG = "MainViewModel"
    }

    val wallpaperStatus: StateFlow<WallpaperStatus> = preferences.wallpaperStatusFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = WallpaperStatus()
        )

    private val _applyState = MutableStateFlow<ApplyState>(ApplyState.Idle)
    val applyState: StateFlow<ApplyState> = _applyState.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setEnabled(enabled)
            if (enabled) {
                scheduler.schedule()
                Log.i(TAG, "Wallpaper updates enabled. Worker scheduled.")
            } else {
                scheduler.cancel()
                Log.i(TAG, "Wallpaper updates disabled. Worker cancelled.")
            }
        }
    }

    fun applyNow() {
        if (_applyState.value is ApplyState.Loading) return // prevent double-tap

        viewModelScope.launch {
            _applyState.value = ApplyState.Loading
            Log.i(TAG, "Manual apply triggered.")

            when (val result = repository.downloadAndApplyLockScreenWallpaper()) {
                is WallpaperResult.Success -> {
                    preferences.recordSuccess()
                    _applyState.value = ApplyState.Success
                    Log.i(TAG, "Manual apply succeeded.")
                }
                is WallpaperResult.Failure -> {
                    preferences.recordFailure(result.reason)
                    _applyState.value = ApplyState.Error(result.reason)
                    Log.e(TAG, "Manual apply failed: ${result.reason}", result.cause)
                }
            }

            // Reset to idle after brief display
            kotlinx.coroutines.delay(3_000)
            _applyState.value = ApplyState.Idle
        }
    }
}
