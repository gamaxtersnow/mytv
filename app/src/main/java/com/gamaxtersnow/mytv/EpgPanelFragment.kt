package com.gamaxtersnow.mytv

import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gamaxtersnow.mytv.databinding.EpgPanelBinding
import com.gamaxtersnow.mytv.databinding.EpgPanelRowBinding
import com.gamaxtersnow.mytv.models.EpgPanelRow
import com.gamaxtersnow.mytv.models.EpgPanelState
import com.gamaxtersnow.mytv.models.TVListViewModel
import com.gamaxtersnow.mytv.ui.AppUiMode
import com.gamaxtersnow.mytv.ui.AppUiSurface

class EpgPanelFragment : Fragment() {
    private var _binding: EpgPanelBinding? = null
    private val binding get() = _binding!!
    private val adapter = EpgAdapter()
    private var rows: List<EpgPanelRow> = emptyList()
    private var selectedIndex = 0
    private var uiMode: AppUiMode? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = EpgPanelBinding.inflate(inflater, container, false)
        binding.root.visibility = View.GONE
        binding.root.setOnClickListener { hide() }
        binding.epgContainer.setOnClickListener { }
        binding.epgList.adapter = adapter
        binding.epgList.layoutManager = LinearLayoutManager(requireContext())
        configureLayout()
        return binding.root
    }

    fun show(tvListViewModel: TVListViewModel) {
        val currentId = tvListViewModel.itemPosition.value ?: 0
        val state = EpgPanelState.fromGroups(tvListViewModel.channelPanelGroups(currentId), currentId)
        rows = state.rows
        selectedIndex = state.selectedIndex
        if (rows.isEmpty()) {
            return
        }
        renderStatus()
        adapter.submitRows(rows, selectedIndex)
        binding.root.visibility = View.VISIBLE
        binding.epgList.post {
            (binding.epgList.layoutManager as? LinearLayoutManager)
                ?.scrollToPositionWithOffset(selectedIndex, 0)
        }
    }

    fun hide() {
        _binding?.root?.visibility = View.GONE
    }

    fun isShowing(): Boolean {
        return _binding?.root?.visibility == View.VISIBLE
    }

    fun onUiModeChanged(mode: AppUiMode) {
        uiMode = mode
        if (_binding != null) {
            configureLayout()
        }
    }

    fun handleKeyDown(keyCode: Int): Boolean {
        if (!isShowing()) {
            return false
        }
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> {
                moveSelection(-1)
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                moveSelection(1)
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                playSelected()
                true
            }
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                hide()
                true
            }
            else -> false
        }
    }

    private fun configureLayout() {
        val params = binding.epgContainer.layoutParams as FrameLayout.LayoutParams
        when (uiMode?.surface) {
            AppUiSurface.PHONE_PORTRAIT -> {
                params.width = ViewGroup.LayoutParams.MATCH_PARENT
                params.height = resources.getDimensionPixelSize(R.dimen.iptv_phone_drawer_height)
                params.gravity = Gravity.BOTTOM
                binding.epgContainer.setBackgroundResource(R.drawable.iptv_phone_drawer_bg)
                binding.epgTitle.setTextColor(ContextCompat.getColor(requireContext(), R.color.iptv_phone_text_primary))
                binding.epgStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.iptv_phone_text_secondary))
            }
            AppUiSurface.PHONE_LANDSCAPE -> {
                params.width = resources.getDimensionPixelSize(R.dimen.iptv_landscape_panel_width)
                params.height = ViewGroup.LayoutParams.MATCH_PARENT
                params.gravity = Gravity.START
                binding.epgContainer.setBackgroundResource(R.drawable.channel_panel_background)
                binding.epgTitle.setTextColor(ContextCompat.getColor(requireContext(), R.color.channel_panel_text_primary))
                binding.epgStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.channel_panel_text_secondary))
            }
            else -> {
                params.width = resources.getDimensionPixelSize(R.dimen.channel_panel_width)
                params.height = ViewGroup.LayoutParams.MATCH_PARENT
                params.gravity = Gravity.START
                binding.epgContainer.setBackgroundResource(R.drawable.channel_panel_background)
                binding.epgTitle.setTextColor(ContextCompat.getColor(requireContext(), R.color.channel_panel_text_primary))
                binding.epgStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.channel_panel_text_secondary))
            }
        }
        binding.epgContainer.layoutParams = params
    }

    private fun renderStatus() {
        val status = (requireActivity().applicationContext as MyApplication).epgRepository.status
        binding.epgStatus.text = when {
            status.isAvailable -> "${status.sourceKey}  ${status.dateRangeText}  ${status.message}"
            else -> getString(R.string.epg_panel_status_unavailable)
        }
    }

    private fun moveSelection(delta: Int) {
        if (rows.isEmpty()) return
        val old = selectedIndex
        selectedIndex = (selectedIndex + delta + rows.size) % rows.size
        adapter.updateSelection(old, selectedIndex)
        binding.epgList.smoothScrollToPosition(selectedIndex)
    }

    private fun playSelected() {
        val row = rows.getOrNull(selectedIndex) ?: return
        (activity as? MainActivity)?.playFromChannelPanel(row.id)
        hide()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private inner class EpgAdapter : RecyclerView.Adapter<EpgAdapter.EpgViewHolder>() {
        private var adapterRows: List<EpgPanelRow> = emptyList()
        private var adapterSelectedIndex = 0

        fun submitRows(rows: List<EpgPanelRow>, selectedIndex: Int) {
            adapterRows = rows
            adapterSelectedIndex = selectedIndex
            notifyDataSetChanged()
        }

        fun updateSelection(oldIndex: Int, newIndex: Int) {
            adapterSelectedIndex = newIndex
            notifyItemChanged(oldIndex)
            notifyItemChanged(newIndex)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EpgViewHolder {
            return EpgViewHolder(
                EpgPanelRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
        }

        override fun onBindViewHolder(holder: EpgViewHolder, position: Int) {
            holder.bind(adapterRows[position], position == adapterSelectedIndex)
        }

        override fun getItemCount(): Int = adapterRows.size

        private inner class EpgViewHolder(private val rowBinding: EpgPanelRowBinding) :
            RecyclerView.ViewHolder(rowBinding.root) {
            init {
                rowBinding.root.setOnClickListener {
                    selectedIndex = bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }
                        ?: return@setOnClickListener
                    playSelected()
                }
            }

            fun bind(row: EpgPanelRow, selected: Boolean) {
                rowBinding.root.setBackgroundResource(
                    when {
                        selected -> R.drawable.channel_panel_row_selected
                        row.isPlaying -> R.drawable.channel_panel_row_playing
                        else -> R.drawable.channel_panel_item_transparent
                    }
                )
                rowBinding.epgRowNumber.text = row.displayNumber
                rowBinding.epgRowTitle.text = row.title
                rowBinding.epgRowCurrent.text = row.currentText
                rowBinding.epgRowNext.text = row.nextText.takeIf { it.isNotBlank() }?.let {
                    "${getString(R.string.playback_next_prefix)}  $it"
                }.orEmpty()
                rowBinding.epgRowNext.visibility =
                    if (rowBinding.epgRowNext.text.isNullOrBlank()) View.GONE else View.VISIBLE
            }
        }
    }
}
