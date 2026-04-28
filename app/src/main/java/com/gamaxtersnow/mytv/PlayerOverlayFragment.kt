package com.gamaxtersnow.mytv

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.gamaxtersnow.mytv.databinding.PlayerOverlayBinding
import com.gamaxtersnow.mytv.models.TVViewModel

class PlayerOverlayFragment : Fragment() {
    private var _binding: PlayerOverlayBinding? = null
    private val binding get() = _binding!!

    private val handler = Handler(Looper.getMainLooper())
    private var infoHideDelay: Long = 3000
    private var isTimeAlwaysVisible = false
    private var isInfoVisible = false

    private val timeUpdateRunnable = object : Runnable {
        override fun run() {
            _binding?.statusTime?.text = Utils.getDateFormat("HH:mm")
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = PlayerOverlayBinding.inflate(inflater, container, false)
        binding.root.visibility = View.GONE

        setupQuickActions()
        setupFocusListeners()
        (activity as MainActivity).fragmentReady("PlayerOverlayFragment")
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        if (isTimeAlwaysVisible) {
            startTimeUpdates()
        }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(infoHideRunnable)
        handler.removeCallbacks(timeUpdateRunnable)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(infoHideRunnable)
        handler.removeCallbacks(timeUpdateRunnable)
        _binding = null
    }

    fun show(tvViewModel: TVViewModel) {
        updateInfo(tvViewModel)
        showInfoArea()
        if (isTimeAlwaysVisible) {
            showTimeArea()
            startTimeUpdates()
        }
        binding.root.visibility = View.VISIBLE
    }

    fun hide() {
        handler.removeCallbacks(infoHideRunnable)
        handler.removeCallbacks(timeUpdateRunnable)
        isInfoVisible = false
        binding.infoArea.visibility = View.GONE
        if (!isTimeAlwaysVisible) {
            binding.root.visibility = View.GONE
        }
    }

    fun showTimeArea() {
        binding.topStatus.visibility = View.VISIBLE
        startTimeUpdates()
    }

    fun hideTimeArea() {
        handler.removeCallbacks(timeUpdateRunnable)
        binding.topStatus.visibility = View.GONE
        if (!isInfoVisible) {
            binding.root.visibility = View.GONE
        }
    }

    fun setTimeAlwaysVisible(always: Boolean) {
        isTimeAlwaysVisible = always
        if (always) {
            showTimeArea()
            if (binding.root.visibility != View.VISIBLE) {
                binding.root.visibility = View.VISIBLE
            }
        } else {
            hideTimeArea()
        }
    }

    fun handleKeyDown(keyCode: Int): Boolean {
        if (binding.root.visibility != View.VISIBLE) {
            return false
        }
        if (!isInfoVisible) {
            return false
        }
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                scheduleInfoHide()
                false
            }
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER -> {
                false
            }
            else -> false
        }
    }

    private fun showInfoArea() {
        isInfoVisible = true
        binding.infoArea.visibility = View.VISIBLE
        scheduleInfoHide()
    }

    private fun scheduleInfoHide() {
        handler.removeCallbacks(infoHideRunnable)
        handler.postDelayed(infoHideRunnable, infoHideDelay)
    }

    private fun startTimeUpdates() {
        handler.removeCallbacks(timeUpdateRunnable)
        handler.post(timeUpdateRunnable)
    }

    private fun updateInfo(tvViewModel: TVViewModel) {
        val tv = tvViewModel.getTV()
        binding.channelNum.text = "%03d".format(tv.id + 1)
        binding.channelTitle.text = tv.title

        val epgList = tvViewModel.epg.value.orEmpty()
        val now = Utils.getDateTimestamp()

        val currentEpg = epgList.filter { it.beginTime < now }.lastOrNull()
        val nextEpg = epgList.filter { it.beginTime >= now }.firstOrNull()

        val programText = if (currentEpg != null) {
            val timeStr = String.format("%02d:%02d", currentEpg.beginTime / 3600 % 24, currentEpg.beginTime / 60 % 60)
            val endTimeStr = if (nextEpg != null) {
                String.format("%02d:%02d", nextEpg.beginTime / 3600 % 24, nextEpg.beginTime / 60 % 60)
            } else ""
            val range = if (endTimeStr.isNotBlank()) "  $timeStr-$endTimeStr" else ""
            "${currentEpg.title}$range"
        } else {
            ""
        }
        binding.currentProgram.text = programText

        val nextText = if (nextEpg != null) {
            val timeStr = String.format("%02d:%02d", nextEpg.beginTime / 3600 % 24, nextEpg.beginTime / 60 % 60)
            "接下来 $timeStr ${nextEpg.title}"
        } else {
            ""
        }
        binding.nextProgram.text = nextText
        binding.nextProgram.visibility = if (nextText.isNotBlank()) View.VISIBLE else View.GONE

        binding.progressContainer.visibility = View.GONE
    }

    private fun setupQuickActions() {
        binding.btnChannelList.setOnClickListener {
            (activity as? MainActivity)?.toggleChannelPanel()
            hideInfoOnly()
        }
        binding.btnEpg.setOnClickListener {
            (activity as? MainActivity)?.toggleEpgPanel()
            hideInfoOnly()
        }
        binding.btnSettings.setOnClickListener {
            (activity as? MainActivity)?.showSetting()
            hideInfoOnly()
        }
    }

    private fun setupFocusListeners() {
        val actionViews = listOf(binding.btnChannelList, binding.btnEpg, binding.btnSettings)
        actionViews.forEach { view ->
            view.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    handler.removeCallbacks(infoHideRunnable)
                } else {
                    if (isInfoVisible) {
                        scheduleInfoHide()
                    }
                }
            }
        }
    }

    private fun hideInfoOnly() {
        handler.removeCallbacks(infoHideRunnable)
        isInfoVisible = false
        binding.infoArea.visibility = View.GONE
    }

    private val infoHideRunnable = Runnable {
        isInfoVisible = false
        binding.infoArea.visibility = View.GONE
        if (!isTimeAlwaysVisible) {
            binding.root.visibility = View.GONE
        }
    }

    companion object {
        const val TAG = "PlayerOverlayFragment"
    }
}
