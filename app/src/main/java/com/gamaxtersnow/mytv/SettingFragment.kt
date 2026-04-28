package com.gamaxtersnow.mytv

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.gamaxtersnow.mytv.databinding.SettingBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class SettingFragment : DialogFragment() {

    private var _binding: SettingBinding? = null
    private val binding get() = _binding!!

    private val navItems: List<View>
        get() = listOf(binding.navSource, binding.navPlayback, binding.navDisplay, binding.navSystem)

    private val panels: List<View>
        get() = listOf(binding.panelSource, binding.panelPlayback, binding.panelDisplay, binding.panelSystem)

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

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
                    binding.navScroll.post { binding.navScroll.smoothScrollTo(0, navItem.top) }
                    (activity as MainActivity).settingNeverHide()
                }
            }
            navItem.setOnKeyListener { _, keyCode, event ->
                if (event.action != KeyEvent.ACTION_DOWN) {
                    return@setOnKeyListener false
                }
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        if (index > 0) {
                            navItems[index - 1].requestFocus()
                            true
                        } else {
                            false
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (index < navItems.lastIndex) {
                            navItems[index + 1].requestFocus()
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
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
                Toast.makeText(requireContext(), "更新中...", Toast.LENGTH_SHORT).show()
                lifecycleScope.launch(Dispatchers.IO) {
                    val success = RemotePlaylistManager.checkAndUpdate(requireContext(), true)
                    withContext(Dispatchers.Main) {
                        if (success) {
                            TVList.refresh(requireContext())
                            (activity as MainActivity).mainFragment.reloadRows()
                            refreshSourceStats()
                            Toast.makeText(requireContext(), "更新成功", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(requireContext(), "更新失败", Toast.LENGTH_SHORT).show()
                        }
                        (activity as MainActivity).settingDelayHide()
                    }
                }
            } else {
                Toast.makeText(requireContext(), "链接为空", Toast.LENGTH_SHORT).show()
            }
            (activity as MainActivity).settingDelayHide()
        }

        refreshSourceStats()
    }

    private fun refreshSourceStats() {
        val cachedCount = RemotePlaylistManager.getCachedChannelCount(requireContext())
        binding.statCachedCount.text = "$cachedCount 个"

        val lastUpdate = SP.lastUpdateTime
        binding.statLastUpdate.text = if (lastUpdate > 0) {
            val sdf = java.text.SimpleDateFormat("今天 HH:mm", java.util.Locale.getDefault())
            sdf.format(java.util.Date(lastUpdate))
        } else {
            "未更新"
        }

        val epgMatch = calcEpgMatchRate()
        binding.statEpgMatch.text = "$epgMatch%"

    }

    private fun calcEpgMatchRate(): Int {
        val tvList = (activity as? MainActivity)?.mainFragment?.tvListViewModel?.tvListViewModel?.value
            ?: return 0
        if (tvList.isEmpty()) {
            return 0
        }
        val matched = tvList.count { it.epg.value?.isNotEmpty() == true }
        return matched * 100 / tvList.size
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "SettingFragment"
    }
}
