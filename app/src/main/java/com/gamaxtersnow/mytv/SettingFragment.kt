package com.gamaxtersnow.mytv

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.gamaxtersnow.mytv.epg.EpgRepository
import com.gamaxtersnow.mytv.databinding.SettingBinding
import com.gamaxtersnow.mytv.ui.AppUiMode
import com.gamaxtersnow.mytv.ui.AppUiSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class SettingFragment : DialogFragment() {

    private var _binding: SettingBinding? = null
    private val binding get() = _binding!!

    private val navItems: List<View>
        get() = listOf(binding.navSource, binding.navPlayback, binding.navDisplay, binding.navSystem, binding.navAdvanced)

    private val panels: List<View>
        get() = listOf(binding.panelSource, binding.panelPlayback, binding.panelDisplay, binding.panelSystem, binding.panelAdvanced)
    private var uiMode: AppUiMode? = null

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                insetsController?.apply {
                    hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                    systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                @Suppress("DEPRECATION")
                decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            }
            val mode = uiMode ?: AppUiMode.from(requireContext())
            when (mode.surface) {
                AppUiSurface.PHONE_PORTRAIT -> {
                    setLayout(ViewGroup.LayoutParams.MATCH_PARENT, (resources.displayMetrics.heightPixels * 0.76f).toInt())
                    setGravity(android.view.Gravity.BOTTOM)
                    setBackgroundDrawableResource(android.R.color.transparent)
                }
                AppUiSurface.PHONE_LANDSCAPE -> {
                    setLayout(resources.getDimensionPixelSize(R.dimen.iptv_landscape_panel_width), ViewGroup.LayoutParams.MATCH_PARENT)
                    setGravity(android.view.Gravity.START)
                    setBackgroundDrawableResource(android.R.color.transparent)
                }
                else -> {
                    setLayout((resources.displayMetrics.widthPixels * 0.74f).toInt(), (resources.displayMetrics.heightPixels * 0.78f).toInt())
                    setGravity(android.view.Gravity.CENTER)
                    setBackgroundDrawableResource(android.R.color.transparent)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, 0)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SettingBinding.inflate(inflater, container, false)

        setupNavigation()
        setupSourcePanel()
        setupPlaybackPanel()
        setupDisplayPanel()
        setupSystemPanel()
        setupAdvancedPanel()

        // Default select first category
        selectPanel(0)

        return binding.root
    }

    private fun setupNavigation() {
        navItems.forEachIndexed { index, navItem ->
            navItem.setOnClickListener { selectPanel(index) }
            navItem.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    selectPanel(index)
                    (activity as MainActivity).settingNeverHide()
                }
            }
        }
    }

    fun onUiModeChanged(mode: AppUiMode) {
        uiMode = mode
    }

    private fun selectPanel(index: Int) {
        panels.forEachIndexed { i, panel ->
            panel.visibility = if (i == index) View.VISIBLE else View.GONE
        }
        navItems.forEachIndexed { i, nav ->
            nav.isSelected = i == index
        }
    }

    private fun setupSourcePanel() {
        binding.remoteUrl.setText(SP.remoteUrl)
        binding.remoteUrl.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                (activity as MainActivity).settingNeverHide()
            } else {
                SP.remoteUrl = binding.remoteUrl.text.toString()
                (activity as MainActivity).settingDelayHide()
            }
        }

        binding.updateNow.setOnClickListener {
            val url = binding.remoteUrl.text.toString()
            if (url.isNotEmpty()) {
                SP.remoteUrl = url
                binding.updateStatus.text = "更新中..."
                binding.epgStatus.text = "节目单更新中..."
                lifecycleScope.launch(Dispatchers.IO) {
                    val success = RemotePlaylistManager.checkAndUpdate(requireContext(), true)
                    val epgRepository = (requireActivity().applicationContext as MyApplication).epgRepository
                    val epgSuccess = epgRepository.refresh()
                    if (epgSuccess) {
                        SP.epgSource = epgRepository.status.sourceKey
                        SP.epgLastUpdateTime = epgRepository.status.lastSuccessTime
                    }
                    withContext(Dispatchers.Main) {
                        if (success) {
                            binding.updateStatus.text = "更新成功"
                            TVList.refresh(requireContext())
                            (activity as MainActivity).mainFragment.reloadRows()
                        } else {
                            binding.updateStatus.text = "更新失败"
                        }
                        renderEpgStatus()
                        (activity as MainActivity).settingDelayHide()
                    }
                }
            } else {
                binding.updateStatus.text = "链接为空"
            }
            (activity as MainActivity).settingDelayHide()
        }

        val cachedCount = RemotePlaylistManager.getCachedChannelCount(requireContext())
        val lastUpdate = SP.lastUpdateTime
        val statusText = if (lastUpdate > 0) {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
            "缓存: $cachedCount 个频道 | 上次: ${sdf.format(java.util.Date(lastUpdate))}"
        } else {
            "缓存: $cachedCount 个频道 | 未更新"
        }
        binding.updateStatus.text = statusText
        renderEpgStatus()
    }

    private fun renderEpgStatus() {
        val repository = (requireActivity().applicationContext as MyApplication).epgRepository
        val status = repository.status
        val lastUpdate = SP.epgLastUpdateTime.takeIf { it > 0L } ?: status.lastSuccessTime
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        val lastUpdateText = if (lastUpdate > 0L) sdf.format(java.util.Date(lastUpdate)) else "未更新"
        val source = SP.epgSource.ifBlank { status.sourceKey.ifBlank { EpgRepository.DEFAULT_SOURCE_KEY } }
        val range = status.dateRangeText.ifBlank { "未知范围" }
        val availability = when {
            status.isAvailable && !status.isStale -> "可用"
            status.isAvailable && status.isStale -> "过期"
            else -> "不可用"
        }
        binding.epgStatus.text = "节目单: $source | $availability | $range | 上次: $lastUpdateText"
    }

    private fun setupPlaybackPanel() {
        binding.switchChannelReversal.run {
            isChecked = SP.channelReversal
            setOnCheckedChangeListener { _, isChecked ->
                SP.channelReversal = isChecked
                (activity as MainActivity).settingDelayHide()
            }
        }

        binding.switchChannelNum.run {
            isChecked = SP.channelNum
            setOnCheckedChangeListener { _, isChecked ->
                SP.channelNum = isChecked
                (activity as MainActivity).settingDelayHide()
            }
        }
    }

    private fun setupDisplayPanel() {
        binding.switchTime.run {
            isChecked = SP.time
            setOnCheckedChangeListener { _, isChecked ->
                SP.time = isChecked
                (activity as MainActivity).showTime()
                (activity as MainActivity).settingDelayHide()
            }
        }
    }

    private fun setupSystemPanel() {
        binding.switchBootStartup.run {
            isChecked = SP.bootStartup
            setOnCheckedChangeListener { _, isChecked ->
                SP.bootStartup = isChecked
                (activity as MainActivity).settingDelayHide()
            }
        }

        binding.exit.setOnClickListener {
            requireActivity().finishAffinity()
        }
    }

    private fun setupAdvancedPanel() {
        binding.openPlayerControls.setOnClickListener {
            (activity as MainActivity).togglePlayerControlPanel()
            dismiss()
        }
        binding.openPerformanceMonitor.setOnClickListener {
            (activity as MainActivity).togglePerformanceMonitorPanel()
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "SettingFragment"
    }
}
