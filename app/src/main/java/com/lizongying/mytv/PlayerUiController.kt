package com.lizongying.mytv

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.lizongying.mytv.UnifiedVideoPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * 播放器UI控制器
 * 负责状态显示、控制面板、性能监控面板的UI逻辑
 * 从PlayerFragment中拆分出来，符合单一职责原则
 */
class PlayerUiController(
    private val rootView: View,
    private val listener: PlayerUiListener
) {
    // 状态显示相关
    private var statusUpdateHandler: Handler? = null
    private val statusUpdateInterval = 5000L // 5秒更新一次，降低CPU占用
    private var isMonitoringVisible = true
    private var isControlPanelVisible = false
    private var isPerformanceMonitorVisible = true
    private var isPerformanceDetailsExpanded = false

    // UI组件
    private val playerTypeValue: TextView? by lazy { rootView.findViewById(R.id.player_type_value) }
    private val playerStateValue: TextView? by lazy { rootView.findViewById(R.id.player_state_value) }
    private val multicastSupportValue: TextView? by lazy { rootView.findViewById(R.id.multicast_support_value) }
    private val protocolValue: TextView? by lazy { rootView.findViewById(R.id.protocol_value) }
    private val multicastAddressValue: TextView? by lazy { rootView.findViewById(R.id.multicast_address_value) }
    private val performanceModeValue: TextView? by lazy { rootView.findViewById(R.id.performance_mode_value) }
    private val fpsValue: TextView? by lazy { rootView.findViewById(R.id.fps_value) }
    private val bufferValue: TextView? by lazy { rootView.findViewById(R.id.buffer_value) }
    private val bitrateValue: TextView? by lazy { rootView.findViewById(R.id.bitrate_value) }
    private val latencyValue: TextView? by lazy { rootView.findViewById(R.id.latency_value) }
    private val networkQualityProgress: ProgressBar? by lazy { rootView.findViewById(R.id.network_quality_progress) }
    private val networkQualityValue: TextView? by lazy { rootView.findViewById(R.id.network_quality_value) }
    private val errorMessage: TextView? by lazy { rootView.findViewById(R.id.error_message) }
    private val toggleMonitoringBtn: Button? by lazy { rootView.findViewById(R.id.toggle_monitoring_btn) }
    private val switchPlayerBtn: Button? by lazy { rootView.findViewById(R.id.switch_player_btn) }

    // 控制面板组件
    private val playerTypeGroup: RadioGroup? by lazy { rootView.findViewById(R.id.player_type_group) }
    private val performanceModeSpinner: Spinner? by lazy { rootView.findViewById(R.id.performance_mode_spinner) }
    private val cacheSeekBar: SeekBar? by lazy { rootView.findViewById(R.id.cache_seekbar) }
    private val cacheValueText: TextView? by lazy { rootView.findViewById(R.id.cache_value_text) }
    private val hardwareDecodingCheck: CheckBox? by lazy { rootView.findViewById(R.id.check_hardware_decoding) }
    private val rtspTcpCheck: CheckBox? by lazy { rootView.findViewById(R.id.check_rtsp_tcp) }
    private val applySettingsBtn: Button? by lazy { rootView.findViewById(R.id.apply_settings_btn) }
    private val resetSettingsBtn: Button? by lazy { rootView.findViewById(R.id.reset_settings_btn) }
    private val closePanelBtn: Button? by lazy { rootView.findViewById(R.id.close_panel_btn) }

    // 性能监控面板组件
    private val expandCollapseIcon: ImageView? by lazy { rootView.findViewById(R.id.expand_collapse_icon) }
    private val toggleDetailsBtn: Button? by lazy { rootView.findViewById(R.id.toggle_details_btn) }
    private val detailedPerformance: LinearLayout? by lazy { rootView.findViewById(R.id.detailed_performance) }
    private val closeMonitorBtn: Button? by lazy { rootView.findViewById(R.id.close_monitor_btn) }
    private val exportReportBtn: Button? by lazy { rootView.findViewById(R.id.export_report_btn) }
    private val clearAlertsBtn: Button? by lazy { rootView.findViewById(R.id.clear_alerts_btn) }
    private val performanceAlerts: TextView? by lazy { rootView.findViewById(R.id.performance_alerts) }
    private val optimizationSuggestions: TextView? by lazy { rootView.findViewById(R.id.optimization_suggestions) }

    // 当前设置
    private var currentCacheSize = 1000 // 默认缓存大小
    private var currentPerformanceMode = "平衡模式"
    private var useHardwareDecoding = true
    private var useRtspTcp = false

    interface PlayerUiListener {
        fun onSwitchPlayerRequested(type: UnifiedVideoPlayer.PlayerType)
        fun onSettingsApplied(cacheMs: Int, performanceMode: PerformanceConfig.PerformanceMode)
        fun onSettingsReset()
        fun onExportPerformanceReport()
        fun onClearPerformanceAlerts()
        fun showToast(message: String)
        fun onStatusUpdateRequested()
        fun onPauseRequested()
        fun onResumeRequested()
        fun onStopRequested()
        fun onReplayRequested()
    }

    init {
        setupStatusControls()
        setupControlPanel()
        setupPerformanceMonitorPanel()
        startStatusUpdates()
    }

    /**
     * 设置状态显示控制
     */
    private fun setupStatusControls() {
        toggleMonitoringBtn?.setOnClickListener {
            isMonitoringVisible = !isMonitoringVisible
            rootView.findViewById<View>(R.id.player_status_container)?.visibility =
                if (isMonitoringVisible) View.VISIBLE else View.GONE
            toggleMonitoringBtn?.text = if (isMonitoringVisible) "隐藏监控" else "显示监控"
        }

        switchPlayerBtn?.setOnClickListener {
            listener.onSwitchPlayerRequested(UnifiedVideoPlayer.PlayerType.GSY_PLAYER)
        }
    }

    /**
     * 设置控制面板
     */
    private fun setupControlPanel() {
        // 性能模式下拉框
        val modes = arrayOf("平衡模式", "低延迟模式", "高质量模式", "省电模式")
        val adapter = ArrayAdapter(rootView.context, android.R.layout.simple_spinner_item, modes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        performanceModeSpinner?.adapter = adapter

        // 默认选中平衡模式
        val defaultModeIndex = modes.indexOf(currentPerformanceMode)
        if (defaultModeIndex >= 0) {
            performanceModeSpinner?.setSelection(defaultModeIndex)
        }

        // 缓存SeekBar
        cacheSeekBar?.progress = currentCacheSize
        cacheValueText?.text = "$currentCacheSize ms"

        cacheSeekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                currentCacheSize = progress
                cacheValueText?.text = "$progress ms"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        // 解码器选项
        hardwareDecodingCheck?.isChecked = useHardwareDecoding
        rtspTcpCheck?.isChecked = useRtspTcp

        // 按钮点击事件
        applySettingsBtn?.setOnClickListener {
            val mode = when (performanceModeSpinner?.selectedItem) {
                "低延迟模式" -> PerformanceConfig.PerformanceMode.LOW_LATENCY
                "高质量模式" -> PerformanceConfig.PerformanceMode.HIGH_QUALITY
                "省电模式" -> PerformanceConfig.PerformanceMode.POWER_SAVING
                else -> PerformanceConfig.PerformanceMode.BALANCED
            }
            listener.onSettingsApplied(currentCacheSize, mode)
            listener.showToast("设置已应用")
        }

        resetSettingsBtn?.setOnClickListener {
            resetPlayerSettings()
            listener.onSettingsReset()
            listener.showToast("设置已重置")
        }

        closePanelBtn?.setOnClickListener {
            toggleControlPanel()
        }

        // 播放器类型切换
        playerTypeGroup?.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radio_exo_player -> {
                    listener.onSwitchPlayerRequested(UnifiedVideoPlayer.PlayerType.EXO_PLAYER)
                }
                R.id.radio_gsy_player -> {
                    listener.onSwitchPlayerRequested(UnifiedVideoPlayer.PlayerType.GSY_PLAYER)
                }
            }
        }

        // 性能模式选择
        performanceModeSpinner?.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>, view: View?, position: Int, id: Long) {
                currentPerformanceMode = modes[position]
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
        }
    }

    /**
     * 设置性能监控面板
     */
    private fun setupPerformanceMonitorPanel() {
        val toggleDetails = {
            isPerformanceDetailsExpanded = !isPerformanceDetailsExpanded
            detailedPerformance?.visibility = if (isPerformanceDetailsExpanded) View.VISIBLE else View.GONE
            expandCollapseIcon?.setImageResource(
                if (isPerformanceDetailsExpanded)
                    android.R.drawable.arrow_up_float
                else
                    android.R.drawable.arrow_down_float
            )
            toggleDetailsBtn?.text = if (isPerformanceDetailsExpanded) "隐藏详情" else "显示详情"
        }

        expandCollapseIcon?.setOnClickListener { toggleDetails() }
        toggleDetailsBtn?.setOnClickListener { toggleDetails() }

        closeMonitorBtn?.setOnClickListener {
            togglePerformanceMonitor()
        }

        exportReportBtn?.setOnClickListener {
            listener.onExportPerformanceReport()
            listener.showToast("性能报告导出功能开发中")
        }

        clearAlertsBtn?.setOnClickListener {
            performanceAlerts?.text = "告警已清除"
            performanceAlerts?.setTextColor(android.graphics.Color.GREEN)
            listener.onClearPerformanceAlerts()
            listener.showToast("性能告警已清除")
        }
    }

    /**
     * 重置播放器设置
     */
    private fun resetPlayerSettings() {
        currentCacheSize = 1000
        currentPerformanceMode = "平衡模式"
        useHardwareDecoding = true
        useRtspTcp = false

        cacheSeekBar?.progress = currentCacheSize
        cacheValueText?.text = "$currentCacheSize ms"

        val modes = arrayOf("平衡模式", "低延迟模式", "高质量模式", "省电模式")
        val defaultModeIndex = modes.indexOf(currentPerformanceMode)
        if (defaultModeIndex >= 0) {
            performanceModeSpinner?.setSelection(defaultModeIndex)
        }

        hardwareDecodingCheck?.isChecked = useHardwareDecoding
        rtspTcpCheck?.isChecked = useRtspTcp
    }

    /**
     * 开始状态更新
     */
    private fun startStatusUpdates() {
        statusUpdateHandler = Handler(Looper.getMainLooper())
        statusUpdateHandler?.postDelayed(object : Runnable {
            override fun run() {
                listener.onStatusUpdateRequested()
                statusUpdateHandler?.postDelayed(this, statusUpdateInterval)
            }
        }, statusUpdateInterval)
    }

    /**
     * 停止状态更新
     */
    fun stopStatusUpdates() {
        statusUpdateHandler?.removeCallbacksAndMessages(null)
    }

    /**
     * 更新播放器状态显示
     */
    fun updatePlayerStatus(
        playerType: UnifiedVideoPlayer.PlayerType?,
        playerState: UnifiedVideoPlayer.PlayerState,
        currentUrl: String?
    ) {
        if (!isMonitoringVisible) return

        // 播放器类型
        playerTypeValue?.text = when (playerType) {
            UnifiedVideoPlayer.PlayerType.GSY_PLAYER -> "GSYVideoPlayer"
            UnifiedVideoPlayer.PlayerType.EXO_PLAYER -> "ExoPlayer"
            else -> "未知"
        }

        // 播放器状态
        playerStateValue?.text = when (playerState) {
            UnifiedVideoPlayer.PlayerState.IDLE -> "空闲"
            UnifiedVideoPlayer.PlayerState.PREPARING -> "准备中"
            UnifiedVideoPlayer.PlayerState.PLAYING -> "播放中"
            UnifiedVideoPlayer.PlayerState.PAUSED -> "已暂停"
            UnifiedVideoPlayer.PlayerState.BUFFERING -> "缓冲中"
            UnifiedVideoPlayer.PlayerState.ERROR -> "错误"
            UnifiedVideoPlayer.PlayerState.ENDED -> "已完成"
            else -> "未知"
        }

        // 根据状态设置颜色
        val stateColor = when (playerState) {
            UnifiedVideoPlayer.PlayerState.PLAYING -> android.graphics.Color.GREEN
            UnifiedVideoPlayer.PlayerState.BUFFERING -> android.graphics.Color.YELLOW
            UnifiedVideoPlayer.PlayerState.ERROR -> android.graphics.Color.RED
            else -> android.graphics.Color.WHITE
        }
        playerStateValue?.setTextColor(stateColor)

        // 更新组播状态信息
        currentUrl?.let { url ->
            updateMulticastStatus(url)
        }

        // 更新性能指标（模拟数据）
        updatePerformanceMetrics()
    }

    /**
     * 更新组播状态信息
     */
    private fun updateMulticastStatus(url: String) {
        val multicastContainer = rootView.findViewById<View>(R.id.multicast_status_container) ?: return

        // 检查是否是RTP/UDP组播流
        val isMulticastStream = PlayerFactory.isRTPStream(url) || PlayerFactory.isRTSPStream(url)

        // 如果不是组播流，隐藏容器
        if (!isMulticastStream) {
            multicastContainer.visibility = View.GONE
            return
        }

        // 显示容器
        multicastContainer.visibility = View.VISIBLE

        // 检查组播支持
        val isMulticastSupported = NetworkDetector.isMulticastSupported()
        multicastSupportValue?.text = if (isMulticastSupported) "支持" else "不支持"
        multicastSupportValue?.setTextColor(
            if (isMulticastSupported) android.graphics.Color.GREEN else android.graphics.Color.RED
        )

        // 显示协议类型
        protocolValue?.text = PlayerFactory.getProtocolDescription(url)

        // 提取组播地址
        val address = extractMulticastAddress(url)
        multicastAddressValue?.text = address ?: "非组播地址"
        if (address != null) {
            val isValidMulticast = isValidMulticastAddress(address)
            multicastAddressValue?.setTextColor(
                if (isValidMulticast) android.graphics.Color.GREEN else android.graphics.Color.RED
            )
        }

        // 显示性能模式
        performanceModeValue?.text = currentPerformanceMode
    }

    /**
     * 从URL提取组播地址
     */
    private fun extractMulticastAddress(url: String): String? {
        return when {
            url.startsWith("rtp://") -> {
                url.removePrefix("rtp://").takeWhile { it != ':' && it != '/' }
            }
            url.startsWith("udp://") -> {
                val withoutPrefix = url.removePrefix("udp://")
                if (withoutPrefix.startsWith("@")) {
                    withoutPrefix.removePrefix("@").takeWhile { it != ':' && it != '/' }
                } else {
                    withoutPrefix.takeWhile { it != ':' && it != '/' }
                }
            }
            else -> null
        }
    }

    /**
     * 验证是否为有效的组播地址 (224.0.0.0 - 239.255.255.255)
     */
    private fun isValidMulticastAddress(address: String): Boolean {
        return try {
            val parts = address.split(".")
            if (parts.size != 4) return false

            val firstOctet = parts[0].toInt()
            firstOctet in 224..239
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 更新性能指标显示
     */
    private fun updatePerformanceMetrics() {
        // 模拟性能数据
        val fps = (20..60).random()
        val buffer = (80..100).random()
        val bitrate = (1000..5000).random()
        val latency = (50..200).random()
        val networkQuality = (70..95).random()

        fpsValue?.text = "$fps fps"
        bufferValue?.text = "$buffer%"
        bitrateValue?.text = "$bitrate kbps"
        latencyValue?.text = "$latency ms"

        networkQualityProgress?.progress = networkQuality
        networkQualityValue?.text = "$networkQuality%"

        // 根据网络质量设置颜色
        val networkColor = when {
            networkQuality >= 80 -> android.graphics.Color.GREEN
            networkQuality >= 60 -> android.graphics.Color.YELLOW
            else -> android.graphics.Color.RED
        }
        networkQualityProgress?.progressTintList = android.content.res.ColorStateList.valueOf(networkColor)

        // 更新性能告警和优化建议
        updatePerformanceAlertsAndSuggestions(fps, buffer, bitrate, latency)
    }

    /**
     * 更新性能告警和优化建议
     */
    private fun updatePerformanceAlertsAndSuggestions(fps: Int, buffer: Int, bitrate: Int, latency: Int) {
        val alerts = mutableListOf<String>()
        val suggestions = mutableListOf<String>()

        if (fps < 25) {
            alerts.add("⚠️ 帧率过低: $fps fps (建议 > 30 fps)")
            suggestions.add("• 尝试切换到'低延迟模式'")
            suggestions.add("• 检查网络连接质量")
        }

        if (buffer < 85) {
            alerts.add("⚠️ 缓冲不足: $buffer% (建议 > 90%)")
            suggestions.add("• 增加网络缓存大小")
            suggestions.add("• 检查服务器负载")
        }

        if (bitrate < 1500) {
            alerts.add("⚠️ 码率较低: $bitrate kbps (建议 > 2000 kbps)")
        }

        if (latency > 150) {
            alerts.add("⚠️ 延迟过高: $latency ms (建议 < 100 ms)")
            suggestions.add("• 使用有线网络连接")
            suggestions.add("• 关闭其他占用网络的应用程序")
        }

        if (bitrate < 2000 && buffer > 95) {
            suggestions.add("• 切换到'高质量模式'以获得更好画质")
        }

        if (alerts.isEmpty()) {
            performanceAlerts?.text = "无告警"
            performanceAlerts?.setTextColor(android.graphics.Color.GREEN)
        } else {
            performanceAlerts?.text = alerts.joinToString("\n")
            performanceAlerts?.setTextColor(android.graphics.Color.YELLOW)
        }

        if (suggestions.isEmpty()) {
            optimizationSuggestions?.text = "播放状态良好，无需优化"
            optimizationSuggestions?.setTextColor(android.graphics.Color.GREEN)
        } else {
            optimizationSuggestions?.text = suggestions.joinToString("\n")
            optimizationSuggestions?.setTextColor(android.graphics.Color.LTGRAY)
        }
    }

    /**
     * 更新状态消息显示
     */
    fun updateStatus(message: String) {
        Log.d(TAG, "状态更新: $message")
        playerStateValue?.text = message
    }

    /**
     * 显示错误消息
     */
    fun showErrorMessage(message: String) {
        errorMessage?.let {
            it.text = message
            it.visibility = View.VISIBLE
        }
    }

    /**
     * 隐藏错误消息
     */
    fun hideErrorMessage() {
        errorMessage?.visibility = View.GONE
    }

    /**
     * 切换控制面板显示/隐藏
     */
    fun toggleControlPanel() {
        val controlPanel = rootView.findViewById<View>(R.id.player_control_container)
        if (controlPanel != null) {
            isControlPanelVisible = !isControlPanelVisible
            controlPanel.visibility = if (isControlPanelVisible) View.VISIBLE else View.GONE
        }
    }

    /**
     * 切换性能监控面板显示/隐藏
     */
    fun togglePerformanceMonitor() {
        val monitorPanel = rootView.findViewById<View>(R.id.performance_monitor_container)
        if (monitorPanel != null) {
            isPerformanceMonitorVisible = !isPerformanceMonitorVisible
            monitorPanel.visibility = if (isPerformanceMonitorVisible) View.VISIBLE else View.GONE
        }
    }

    /**
     * 更新播放器类型选中状态
     */
    fun updatePlayerTypeSelection(type: UnifiedVideoPlayer.PlayerType) {
        when (type) {
            UnifiedVideoPlayer.PlayerType.GSY_PLAYER -> {
                playerTypeGroup?.check(R.id.radio_gsy_player)
            }
            UnifiedVideoPlayer.PlayerType.EXO_PLAYER -> {
                playerTypeGroup?.check(R.id.radio_exo_player)
            }
        }
    }

    companion object {
        private const val TAG = "PlayerUiController"
    }
}
