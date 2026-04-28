package com.gamaxtersnow.mytv

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.gamaxtersnow.mytv.databinding.ChannelPanelBinding
import com.gamaxtersnow.mytv.databinding.ChannelPanelRowBinding
import com.gamaxtersnow.mytv.models.ChannelPanelGroup
import com.gamaxtersnow.mytv.models.ChannelPanelRow
import com.gamaxtersnow.mytv.models.TVListViewModel
import com.gamaxtersnow.mytv.ui.AppUiMode
import com.gamaxtersnow.mytv.ui.AppUiSurface

class ChannelPanelFragment : Fragment() {
    private var _binding: ChannelPanelBinding? = null
    private val binding get() = _binding!!

    private val handler = Handler(Looper.getMainLooper())
    private var groups: List<ChannelPanelGroup> = emptyList()
    private var selectedGroupIndex = 0
    private var selectedRowIndex = 0
    private var playingId = 0
    private var uiMode: AppUiMode? = null
    private var suppressFocusSelection = false
    private val channelAdapter = ChannelAdapter()

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
        configurePanelLayout()
        return binding.root
    }

    fun show(tvListViewModel: TVListViewModel) {
        playingId = tvListViewModel.itemPosition.value ?: 0
        groups = tvListViewModel.channelPanelGroups(playingId)
        if (groups.isEmpty()) {
            return
        }

        selectCurrentChannel()
        render(animated = false)
        // 延迟显示面板，确保无动画的 scrollTo 先执行完，避免闪烁
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
        if (!isShowing()) {
            return
        }
        show(tvListViewModel)
    }

    fun onUiModeChanged(mode: AppUiMode) {
        uiMode = mode
        if (_binding != null) {
            configurePanelLayout()
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

    private fun configurePanelLayout() {
        val params = binding.panelContainer.layoutParams as FrameLayout.LayoutParams
        val groupParams = binding.groupContainer.layoutParams as LinearLayout.LayoutParams
        val channelParams = binding.channelList.layoutParams as LinearLayout.LayoutParams
        val groupListParams = binding.groupList.layoutParams

        when (uiMode?.surface) {
            AppUiSurface.PHONE_PORTRAIT -> {
                params.width = ViewGroup.LayoutParams.MATCH_PARENT
                params.height = resources.getDimensionPixelSize(R.dimen.iptv_phone_drawer_height)
                params.gravity = android.view.Gravity.BOTTOM
                binding.panelContainer.setBackgroundResource(R.drawable.iptv_phone_drawer_bg)
                binding.panelContent.orientation = LinearLayout.VERTICAL
                groupParams.width = ViewGroup.LayoutParams.MATCH_PARENT
                groupParams.height = dp(54)
                channelParams.width = ViewGroup.LayoutParams.MATCH_PARENT
                channelParams.height = 0
                channelParams.weight = 1f
                channelParams.marginStart = 0
                channelParams.topMargin = dp(12)
                groupListParams.width = ViewGroup.LayoutParams.WRAP_CONTENT
                groupListParams.height = ViewGroup.LayoutParams.MATCH_PARENT
            }
            AppUiSurface.PHONE_LANDSCAPE -> {
                params.width = resources.getDimensionPixelSize(R.dimen.iptv_landscape_panel_width)
                params.height = ViewGroup.LayoutParams.MATCH_PARENT
                params.gravity = android.view.Gravity.START
                binding.panelContainer.setBackgroundResource(R.drawable.channel_panel_background)
                binding.panelContent.orientation = LinearLayout.HORIZONTAL
                groupParams.width = dp(124)
                groupParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                channelParams.width = 0
                channelParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                channelParams.weight = 1f
                channelParams.marginStart = dp(12)
                channelParams.topMargin = 0
                groupListParams.width = ViewGroup.LayoutParams.MATCH_PARENT
                groupListParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
            }
            else -> {
                params.width = resources.getDimensionPixelSize(R.dimen.channel_panel_width)
                params.height = ViewGroup.LayoutParams.MATCH_PARENT
                params.gravity = android.view.Gravity.START
                binding.panelContainer.setBackgroundResource(R.drawable.channel_panel_background)
                binding.panelContent.orientation = LinearLayout.HORIZONTAL
                groupParams.width = dp(160)
                groupParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                channelParams.width = 0
                channelParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                channelParams.weight = 1f
                channelParams.marginStart = dp(18)
                channelParams.topMargin = 0
                groupListParams.width = ViewGroup.LayoutParams.MATCH_PARENT
                groupListParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
            }
        }

        binding.panelContainer.layoutParams = params
        binding.groupContainer.layoutParams = groupParams
        binding.channelList.layoutParams = channelParams
        binding.groupList.layoutParams = groupListParams
    }

    private fun selectCurrentChannel() {
        // 跳过”全部”分组（索引0），优先定位到当前播放电视台所在的分类
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
            val view = groupTextView(index, group)

            binding.groupList.addView(
                view,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(8)
                }
            )
        }
        scrollSelectedGroupIntoView(animated)
    }

    private fun groupTextView(index: Int, group: ChannelPanelGroup): TextView {
        return TextView(requireContext()).apply {
            text = displayGroupTitle(group.title)
            textSize = 16f
            setSingleLine(true)
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(groupTextColor(index))
            setBackgroundResource(
                if (index == selectedGroupIndex) {
                    R.drawable.channel_panel_group_selected
                } else {
                    R.drawable.channel_panel_item_transparent
                }
            )
            setPadding(dp(14), dp(10), dp(14), dp(10))
            isFocusable = true
            setOnClickListener {
                selectedGroupIndex = index
                selectedRowIndex = group.rows.indexOfFirst { it.isPlaying }
                    .takeIf { it >= 0 }
                    ?: 0
                render(animated = false)
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
            binding.channelList.post {
                binding.channelList.smoothScrollToPosition(selectedRowIndex)
            }
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

    private fun rowBackground(row: ChannelPanelRow, isSelected: Boolean): Int {
        return when {
            isSelected -> R.drawable.channel_panel_row_selected
            row.isPlaying -> R.drawable.channel_panel_row_playing
            else -> R.drawable.channel_panel_item_transparent
        }
    }

    private fun rowTitleColor(row: ChannelPanelRow, isSelected: Boolean): Int {
        return when {
            isSelected -> ContextCompat.getColor(requireContext(), R.color.channel_panel_text_selected)
            row.isPlaying -> ContextCompat.getColor(requireContext(), R.color.channel_panel_accent)
            else -> ContextCompat.getColor(requireContext(), R.color.channel_panel_text_primary)
        }
    }

    private fun rowMetaColor(row: ChannelPanelRow, isSelected: Boolean): Int {
        return when {
            isSelected -> ContextCompat.getColor(requireContext(), R.color.channel_panel_text_selected)
            row.isPlaying -> ContextCompat.getColor(requireContext(), R.color.channel_panel_accent)
            else -> ContextCompat.getColor(requireContext(), R.color.channel_panel_text_secondary)
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

    companion object {
        private const val AUTO_HIDE_DELAY = 10000L
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
                        scheduleAutoHide()
                    }
                }
            }

            fun bind(row: ChannelPanelRow, isSelected: Boolean) {
                this.row = row
                rowBinding.channelNumber.text = row.displayNumber
                rowBinding.channelTitle.text = row.title
                rowBinding.channelProgram.text = row.programSummary.currentScheduleText
                rowBinding.channelProgram.visibility =
                    if (rowBinding.channelProgram.text.isNullOrBlank()) View.GONE else View.VISIBLE
                rowBinding.channelProgramProgress.progress = row.programSummary.progressPercent ?: 0
                rowBinding.channelProgramProgress.visibility =
                    if (isSelected && row.programSummary.progressPercent != null) View.VISIBLE else View.GONE
                rowBinding.channelNextProgram.text = row.programSummary.nextTitle.takeIf { it.isNotBlank() }?.let {
                    "${getString(R.string.playback_next_prefix)}  $it"
                }.orEmpty()
                rowBinding.channelNextProgram.visibility =
                    if (isSelected && rowBinding.channelNextProgram.text.isNotBlank()) View.VISIBLE else View.GONE
                rowBinding.playingMarker.visibility =
                    if (row.isPlaying) View.VISIBLE else View.INVISIBLE
                applySelection(isSelected)
                loadLogo(rowBinding.channelLogo, row.logo)
            }

            fun applySelection(isSelected: Boolean) {
                val currentRow = row ?: return
                rowBinding.root.setBackgroundResource(rowBackground(currentRow, isSelected))
                rowBinding.channelTitle.setTextColor(rowTitleColor(currentRow, isSelected))
                rowBinding.channelNumber.setTextColor(rowMetaColor(currentRow, isSelected))
                rowBinding.channelProgram.setTextColor(rowMetaColor(currentRow, isSelected))
                rowBinding.channelNextProgram.setTextColor(rowMetaColor(currentRow, isSelected))
            }

            private fun loadLogo(imageView: ImageView, logo: String) {
                if (logo.isBlank()) {
                    imageView.setImageResource(R.drawable.logo)
                    return
                }

                Glide.with(imageView.context)
                    .load(logo)
                    .centerInside()
                    .placeholder(R.drawable.logo)
                    .into(imageView)
            }
        }
    }
}
