package com.gamaxtersnow.mytv

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gamaxtersnow.mytv.databinding.ChannelPanelBinding
import com.gamaxtersnow.mytv.databinding.ChannelPanelRowBinding
import com.gamaxtersnow.mytv.models.ChannelPanelGroup
import com.gamaxtersnow.mytv.models.ChannelPanelRow
import com.gamaxtersnow.mytv.models.TVListViewModel

class ChannelPanelFragment : Fragment() {
    private var _binding: ChannelPanelBinding? = null
    private val binding get() = _binding!!

    private val handler = Handler(Looper.getMainLooper())
    private val channelAdapter = ChannelAdapter()
    private var groups: List<ChannelPanelGroup> = emptyList()
    private var selectedGroupIndex = 0
    private var selectedRowIndex = 0
    private var panelMode = PanelMode.PLAYLIST
    private var suppressFocusSelection = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ChannelPanelBinding.inflate(inflater, container, false)
        binding.root.visibility = View.GONE
        binding.root.setOnClickListener { hide() }
        binding.panelContainer.setOnClickListener { }
        binding.channelList.adapter = channelAdapter
        binding.channelList.layoutManager = LinearLayoutManager(requireContext())
        return binding.root
    }

    fun show(tvListViewModel: TVListViewModel) {
        show(tvListViewModel, PanelMode.PLAYLIST)
    }

    fun showEpg(tvListViewModel: TVListViewModel) {
        show(tvListViewModel, PanelMode.EPG)
    }

    private fun show(tvListViewModel: TVListViewModel, mode: PanelMode) {
        panelMode = mode
        groups = tvListViewModel.channelPanelGroups(tvListViewModel.itemPosition.value ?: 0)
        if (groups.isEmpty()) {
            return
        }

        selectCurrentChannel()
        render(animated = false)
        updateModeChrome()
        updateEpgDetail()
        binding.root.post {
            binding.root.visibility = View.VISIBLE
            scheduleAutoHide()
        }
    }

    fun hide() {
        handler.removeCallbacks(hideRunnable)
        _binding?.root?.visibility = View.GONE
    }

    fun isShowing(): Boolean {
        return _binding?.root?.visibility == View.VISIBLE
    }

    fun refresh(tvListViewModel: TVListViewModel) {
        if (isShowing()) {
            show(tvListViewModel, panelMode)
        }
    }

    fun handleKeyDown(keyCode: Int): Boolean {
        if (!isShowing()) {
            return false
        }

        scheduleAutoHide()
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> {
                moveRow(-1)
                true
            }

            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                moveRow(1)
                true
            }

            KeyEvent.KEYCODE_DPAD_LEFT -> {
                moveGroup(-1)
                true
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                moveGroup(1)
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

    private fun selectCurrentChannel() {
        selectedGroupIndex = groups
            .drop(1)
            .indexOfFirst { group -> group.rows.any { it.isPlaying } }
            .takeIf { it >= 0 }
            ?.plus(1)
            ?: 0

        selectedRowIndex = groups.getOrNull(selectedGroupIndex)
            ?.rows
            ?.indexOfFirst { it.isPlaying }
            ?.takeIf { it >= 0 }
            ?: 0
    }

    private fun render(animated: Boolean = true) {
        suppressFocusSelection = true
        renderGroups(animated)
        renderRows(animated)
        suppressFocusSelection = false
    }

    private fun renderGroups(animated: Boolean = true) {
        binding.groupList.removeAllViews()
        groups.forEachIndexed { index, group ->
            binding.groupList.addView(
                groupTextView(index, group),
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(46)
                ).apply {
                    bottomMargin = dp(10)
                }
            )
        }
        scrollSelectedGroupIntoView(animated)
    }

    private fun groupTextView(index: Int, group: ChannelPanelGroup): TextView {
        return TextView(requireContext()).apply {
            text = displayGroupTitle(group.title)
            textSize = 16f
            gravity = android.view.Gravity.CENTER_VERTICAL
            setSingleLine(true)
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(12), 0, dp(12), 0)
            setTextColor(groupTextColor(index))
            setBackgroundResource(
                if (index == selectedGroupIndex) {
                    R.drawable.channel_panel_group_selected
                } else {
                    R.drawable.channel_panel_item_transparent
                }
            )
            isFocusable = true
            setOnClickListener {
                selectedGroupIndex = index
                selectedRowIndex = group.rows.indexOfFirst { it.isPlaying }
                    .takeIf { it >= 0 }
                    ?: 0
                render(animated = false)
                updateEpgDetail()
                scheduleAutoHide()
            }
        }
    }

    private fun renderRows(animated: Boolean = true) {
        channelAdapter.submitRows(currentRows(), selectedRowIndex)
        scrollSelectedRowIntoView(animated)
    }

    private fun moveGroup(delta: Int) {
        if (groups.isEmpty()) {
            return
        }
        selectedGroupIndex = (selectedGroupIndex + delta + groups.size) % groups.size
        selectedRowIndex = currentRows().indexOfFirst { it.isPlaying }
            .takeIf { it >= 0 }
            ?: 0
        render(animated = false)
        updateEpgDetail()
    }

    private fun moveRow(delta: Int) {
        val rows = currentRows()
        if (rows.isEmpty()) {
            return
        }
        val oldIndex = selectedRowIndex
        selectedRowIndex = (selectedRowIndex + delta + rows.size) % rows.size
        channelAdapter.updateSelection(oldIndex, selectedRowIndex)
        applySelectionToVisibleRow(oldIndex)
        applySelectionToVisibleRow(selectedRowIndex)
        updateEpgDetail()
        scrollSelectedRowIntoView()
    }

    private fun playSelected() {
        val row = currentRows().getOrNull(selectedRowIndex) ?: return
        (activity as? MainActivity)?.playFromChannelPanel(row.id)
        hide()
    }

    private fun currentRows(): List<ChannelPanelRow> {
        return groups.getOrNull(selectedGroupIndex)?.rows.orEmpty()
    }

    private fun applySelectionToVisibleRow(index: Int) {
        val holder = binding.channelList.findViewHolderForAdapterPosition(index)
                as? ChannelAdapter.ChannelViewHolder
        holder?.applySelection(index == selectedRowIndex)
    }

    private fun scrollSelectedRowIntoView(animated: Boolean = true) {
        if (animated) {
            binding.channelList.post { binding.channelList.smoothScrollToPosition(selectedRowIndex) }
        } else {
            (binding.channelList.layoutManager as? LinearLayoutManager)
                ?.scrollToPositionWithOffset(selectedRowIndex, 0)
        }
    }

    private fun scrollSelectedGroupIntoView(animated: Boolean = true) {
        binding.groupVerticalScroll.post {
            binding.groupList.getChildAt(selectedGroupIndex)?.let { selectedView ->
                val scrollView = binding.groupVerticalScroll
                val scrollY = scrollView.scrollY
                val scrollHeight = scrollView.height
                val viewTop = selectedView.top
                val viewBottom = selectedView.bottom

                if (viewTop >= scrollY && viewBottom <= scrollY + scrollHeight) {
                    return@let
                }

                val targetY = when {
                    viewTop < scrollY -> viewTop
                    viewBottom > scrollY + scrollHeight -> viewBottom - scrollHeight
                    else -> scrollY
                }

                if (animated) {
                    scrollView.smoothScrollTo(0, targetY)
                } else {
                    scrollView.scrollTo(0, targetY)
                }
            }
        }
    }

    private fun updateModeChrome() {
        val channelCount = groups.firstOrNull()?.rows?.size ?: 0
        val matchedCount = groups.firstOrNull()?.rows.orEmpty().count { it.currentProgram.isNotBlank() }
        if (panelMode == PanelMode.PLAYLIST) {
            binding.panelTitle.text = "频道"
            binding.panelSubtitle.text = "全部 ${channelCount} 个"
            binding.remoteHintText.text = "上下选择 · 左右切换分组 · 确认播放"
            binding.videoScene.visibility = View.GONE
            binding.detailCard.visibility = View.GONE
            binding.epgTicker.visibility = View.GONE
        } else {
            binding.panelTitle.text = "节目单"
            binding.panelSubtitle.text = "51zmt XMLTV · 今天 · 已匹配 ${matchedCount}/${channelCount}"
            binding.remoteHintText.text = "上下选择频道 · 左右切换分组 · 确认播放"
            binding.videoScene.visibility = View.VISIBLE
            binding.detailCard.visibility = View.VISIBLE
            binding.epgTicker.visibility = View.VISIBLE
            binding.epgTickerText.isSelected = true
        }
    }

    private fun updateEpgDetail() {
        if (panelMode != PanelMode.EPG) {
            binding.detailCard.visibility = View.GONE
            binding.epgTicker.visibility = View.GONE
            return
        }

        val row = currentRows().getOrNull(selectedRowIndex)
        if (row == null) {
            binding.detailCard.visibility = View.GONE
            binding.epgTicker.visibility = View.GONE
            return
        }

        val currentProgram = row.currentProgram.ifBlank { "暂无节目信息" }
        val nextLine = nextProgramLine(row)
        binding.detailCard.visibility = View.VISIBLE
        binding.epgTicker.visibility = View.VISIBLE
        binding.detailTitle.text = currentProgram
        binding.detailBody.text = "${row.title} 的节目简介会在这里显示；如 XMLTV 提供描述，可替换为完整节目说明。"
        binding.detailNext.text = nextLine
        binding.epgTickerText.text = "${row.title}    正在播放  $currentProgram    $nextLine"
        binding.epgTickerText.isSelected = true
    }

    private fun nextProgramLine(row: ChannelPanelRow): String {
        return if (row.nextProgramTime.isNotBlank() && row.nextProgram.isNotBlank()) {
            "${row.nextProgramTime} ${row.nextProgram}"
        } else if (row.nextProgram.isNotBlank()) {
            row.nextProgram
        } else {
            "暂无后续节目信息"
        }
    }

    private fun rowBackground(row: ChannelPanelRow, isSelected: Boolean): Int {
        return when {
            isSelected -> R.drawable.channel_panel_row_selected
            row.isPlaying -> R.drawable.channel_panel_row_playing
            else -> R.drawable.channel_panel_item_transparent
        }
    }

    private fun rowTitleColor(isSelected: Boolean): Int {
        return if (isSelected) {
            ContextCompat.getColor(requireContext(), R.color.channel_panel_text_selected)
        } else {
            ContextCompat.getColor(requireContext(), R.color.channel_panel_text_primary)
        }
    }

    private fun rowMetaColor(isSelected: Boolean): Int {
        return if (isSelected) {
            ContextCompat.getColor(requireContext(), R.color.channel_panel_text_selected)
        } else {
            ContextCompat.getColor(requireContext(), R.color.channel_panel_text_secondary)
        }
    }

    private fun groupTextColor(index: Int): Int {
        return if (index == selectedGroupIndex) {
            ContextCompat.getColor(requireContext(), R.color.channel_panel_text_selected)
        } else {
            ContextCompat.getColor(requireContext(), R.color.channel_panel_text_secondary)
        }
    }

    private fun scheduleAutoHide() {
        handler.removeCallbacks(hideRunnable)
        handler.postDelayed(hideRunnable, AUTO_HIDE_DELAY)
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun displayGroupTitle(title: String): String {
        return when (title) {
            TVListViewModel.ALL_GROUP_KEY -> getString(R.string.channel_panel_group_all)
            TVListViewModel.FALLBACK_GROUP_KEY -> getString(R.string.channel_panel_group_custom)
            else -> title
        }
    }

    private val hideRunnable = Runnable {
        hide()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(hideRunnable)
    }

    override fun onResume() {
        super.onResume()
        if (isShowing()) {
            scheduleAutoHide()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(hideRunnable)
        _binding = null
    }

    private enum class PanelMode {
        PLAYLIST,
        EPG
    }

    private inner class ChannelAdapter : RecyclerView.Adapter<ChannelAdapter.ChannelViewHolder>() {
        private var rows: List<ChannelPanelRow> = emptyList()
        private var selectedIndex = 0

        fun submitRows(rows: List<ChannelPanelRow>, selectedIndex: Int) {
            this.rows = rows
            this.selectedIndex = selectedIndex
            notifyDataSetChanged()
        }

        fun updateSelection(oldIndex: Int, newIndex: Int) {
            selectedIndex = newIndex
            if (binding.channelList.findViewHolderForAdapterPosition(oldIndex) == null) {
                notifyItemChanged(oldIndex)
            }
            if (binding.channelList.findViewHolderForAdapterPosition(newIndex) == null) {
                notifyItemChanged(newIndex)
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
            val rowBinding = ChannelPanelRowBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return ChannelViewHolder(rowBinding)
        }

        override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) {
            holder.bind(rows[position], position == selectedIndex)
        }

        override fun getItemCount(): Int {
            return rows.size
        }

        inner class ChannelViewHolder(
            private val rowBinding: ChannelPanelRowBinding
        ) : RecyclerView.ViewHolder(rowBinding.root) {
            private var row: ChannelPanelRow? = null

            init {
                rowBinding.root.setOnClickListener {
                    selectedRowIndex = bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }
                        ?: return@setOnClickListener
                    playSelected()
                }
                rowBinding.root.setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus && !suppressFocusSelection) {
                        val position = bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }
                            ?: return@setOnFocusChangeListener
                        val oldIndex = selectedRowIndex
                        selectedRowIndex = position
                        updateSelection(oldIndex, selectedRowIndex)
                        applySelectionToVisibleRow(oldIndex)
                        applySelectionToVisibleRow(selectedRowIndex)
                        updateEpgDetail()
                        scheduleAutoHide()
                    }
                }
            }

            fun bind(row: ChannelPanelRow, isSelected: Boolean) {
                this.row = row
                rowBinding.channelNumber.text = row.displayNumber
                rowBinding.channelTitle.text = row.title
                rowBinding.playingMarker.visibility =
                    if (row.isPlaying) View.VISIBLE else View.INVISIBLE

                val showEpg = panelMode == PanelMode.EPG
                rowBinding.channelProgram.text = row.currentProgram.ifBlank { "暂无节目信息" }
                rowBinding.channelProgram.visibility = if (showEpg) View.VISIBLE else View.GONE
                rowBinding.channelNext.text = nextProgramLine(row).replaceFirst(" ", "\n")
                rowBinding.channelNext.visibility = if (showEpg) View.VISIBLE else View.GONE
                rowBinding.progressContainer.visibility = if (showEpg && isSelected) View.VISIBLE else View.GONE

                applySelection(isSelected)
            }

            fun applySelection(isSelected: Boolean) {
                val currentRow = row ?: return
                val showEpg = panelMode == PanelMode.EPG
                val height = when {
                    showEpg && isSelected -> dp(92)
                    showEpg -> dp(78)
                    isSelected -> dp(76)
                    else -> dp(64)
                }
                rowBinding.root.layoutParams = (rowBinding.root.layoutParams as? RecyclerView.LayoutParams)
                    ?.apply { this.height = height }
                    ?: RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height)

                rowBinding.root.setBackgroundResource(rowBackground(currentRow, isSelected))
                rowBinding.channelTitle.setTextColor(rowTitleColor(isSelected))
                rowBinding.channelNumber.setTextColor(rowMetaColor(isSelected))
                rowBinding.channelProgram.setTextColor(rowMetaColor(isSelected))
                rowBinding.channelNext.setTextColor(rowMetaColor(isSelected))
                rowBinding.progressContainer.visibility = if (showEpg && isSelected) View.VISIBLE else View.GONE
                if (showEpg && isSelected) {
                    rowBinding.progressBar.layoutParams = FrameLayout.LayoutParams(
                        dp(300),
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            }
        }
    }

    companion object {
        private const val AUTO_HIDE_DELAY = 10000L
    }
}
