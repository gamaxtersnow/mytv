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
import com.gamaxtersnow.mytv.databinding.SettingBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class SettingFragment : DialogFragment() {

    private var _binding: SettingBinding? = null
    private val binding get() = _binding!!

    private val navItems by lazy {
        listOf(binding.navSource, binding.navPlayback, binding.navDisplay, binding.navSystem)
    }

    private val panels by lazy {
        listOf(binding.panelSource, binding.panelPlayback, binding.panelDisplay, binding.panelSystem)
    }

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
                lifecycleScope.launch(Dispatchers.IO) {
                    val success = RemotePlaylistManager.checkAndUpdate(requireContext(), true)
                    withContext(Dispatchers.Main) {
                        if (success) {
                            binding.updateStatus.text = "更新成功"
                            TVList.refresh(requireContext())
                            (activity as MainActivity).mainFragment.reloadRows()
                        } else {
                            binding.updateStatus.text = "更新失败"
                        }
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "SettingFragment"
    }
}
