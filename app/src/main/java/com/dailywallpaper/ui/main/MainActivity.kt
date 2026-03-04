package com.dailywallpaper.ui.main

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.dailywallpaper.R
import com.dailywallpaper.data.model.WallpaperStatus
import com.dailywallpaper.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    private val dateFormatter = DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withZone(ZoneId.systemDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUi()
        observeState()
    }

    private fun setupUi() {
        binding.switchEnable.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setEnabled(isChecked)
        }

        binding.btnApplyNow.setOnClickListener {
            viewModel.applyNow()
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.wallpaperStatus.collect { status ->
                        updateStatusUi(status)
                    }
                }
                launch {
                    viewModel.applyState.collect { state ->
                        updateApplyState(state)
                    }
                }
            }
        }
    }

    private fun updateStatusUi(status: WallpaperStatus) {
        // Update toggle without triggering listener
        binding.switchEnable.setOnCheckedChangeListener(null)
        binding.switchEnable.isChecked = status.isEnabled
        binding.switchEnable.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setEnabled(isChecked)
        }

        binding.tvStatusEnabled.text = if (status.isEnabled) {
            getString(R.string.status_enabled)
        } else {
            getString(R.string.status_disabled)
        }
        binding.tvStatusEnabled.setTextColor(
            getColor(if (status.isEnabled) R.color.status_success else R.color.status_disabled)
        )

        binding.tvLastRunTime.text = status.lastRunTime?.let {
            getString(R.string.last_run_prefix, dateFormatter.format(it))
        } ?: getString(R.string.last_run_never)

        when (status.lastRunSuccess) {
            true -> {
                binding.tvLastRunResult.text = getString(R.string.last_result_success)
                binding.tvLastRunResult.setTextColor(getColor(R.color.status_success))
                binding.tvLastRunError.visibility = View.GONE
            }
            false -> {
                binding.tvLastRunResult.text = getString(R.string.last_result_failure)
                binding.tvLastRunResult.setTextColor(getColor(R.color.status_error))
                binding.tvLastRunError.visibility = View.VISIBLE
                binding.tvLastRunError.text = status.lastRunError ?: ""
            }
            null -> {
                binding.tvLastRunResult.text = getString(R.string.last_result_none)
                binding.tvLastRunResult.setTextColor(getColor(R.color.status_neutral))
                binding.tvLastRunError.visibility = View.GONE
            }
        }
    }

    private fun updateApplyState(state: ApplyState) {
        when (state) {
            is ApplyState.Idle -> {
                binding.btnApplyNow.isEnabled = true
                binding.btnApplyNow.text = getString(R.string.btn_apply_now)
                binding.progressApply.visibility = View.GONE
                binding.tvApplyResult.visibility = View.GONE
            }
            is ApplyState.Loading -> {
                binding.btnApplyNow.isEnabled = false
                binding.btnApplyNow.text = getString(R.string.btn_applying)
                binding.progressApply.visibility = View.VISIBLE
                binding.tvApplyResult.visibility = View.GONE
            }
            is ApplyState.Success -> {
                binding.btnApplyNow.isEnabled = false
                binding.progressApply.visibility = View.GONE
                binding.tvApplyResult.visibility = View.VISIBLE
                binding.tvApplyResult.text = getString(R.string.apply_success)
                binding.tvApplyResult.setTextColor(getColor(R.color.status_success))
            }
            is ApplyState.Error -> {
                binding.btnApplyNow.isEnabled = true
                binding.btnApplyNow.text = getString(R.string.btn_apply_now)
                binding.progressApply.visibility = View.GONE
                binding.tvApplyResult.visibility = View.VISIBLE
                binding.tvApplyResult.text = getString(R.string.apply_error, state.message)
                binding.tvApplyResult.setTextColor(getColor(R.color.status_error))
            }
        }
    }
}
