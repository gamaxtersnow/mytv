package com.gamaxtersnow.mytv

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.sqrt

/**
 * 性能监控器
 * 监控播放性能指标：帧率、延迟、缓冲状态、网络质量等
 */
class PerformanceMonitor(private val context: Context) {
    private val TAG = "PerformanceMonitor"

    // 监控配置
    var isMonitoringEnabled = PerformanceConfig.DEFAULT_MONITORING_ENABLED
    var samplingIntervalMs = PerformanceConfig.DEFAULT_SAMPLING_INTERVAL_MS
    var maxHistorySize = PerformanceConfig.DEFAULT_HISTORY_SIZE
    
    // 性能指标枚举
    enum class MetricType {
        FRAME_RATE,             // 帧率 (fps)
        BUFFER_LEVEL,           // 缓冲级别 (%)
        NETWORK_BITRATE,        // 网络比特率 (bps)
        DECODING_TIME,          // 解码时间 (ms)
        RENDERING_LATENCY,      // 渲染延迟 (ms)
        DROPPED_FRAMES,         // 丢帧数
        AUDIO_SYNC_OFFSET,      // 音视频同步偏移 (ms)
        CPU_USAGE,              // CPU使用率 (%)
        MEMORY_USAGE,           // 内存使用率 (%)
        NETWORK_QUALITY,        // 网络质量评分 (0-100)
        // 组播特定指标
        MULTICAST_PACKET_LOSS,  // 组播丢包率 (%)
        MULTICAST_JITTER,       // 组播抖动 (ms)
        MULTICAST_LATENCY,      // 组播延迟 (ms)
        IGMP_RESPONSE_TIME,     // IGMP响应时间 (ms)
        MULTICAST_JOIN_TIME,    // 加入组播时间 (ms)
        MULTICAST_LEAVE_TIME,   // 离开组播时间 (ms)
        RTP_SEQUENCE_COUNT,     // RTP序列计数
        RTP_PACKET_COUNT,       // RTP包计数
        RTP_MISSING_PACKETS     // RTP丢失包数
    }
    
    // 性能指标数据
    data class MetricData(
        val type: MetricType,
        val value: Float,
        val timestamp: Long = System.currentTimeMillis(),
        val unit: String = ""
    )
    
    // 性能报告
    data class PerformanceReport(
        val sessionId: String,
        val startTime: Long,
        val endTime: Long,
        val metrics: Map<MetricType, List<MetricData>>,
        val summary: PerformanceSummary,
        val issues: List<PerformanceIssue>
    )
    
    // 性能摘要
    data class PerformanceSummary(
        val averageFrameRate: Float = 0f,
        val averageBufferLevel: Float = 0f,
        val averageBitrate: Float = 0f,
        val totalDroppedFrames: Int = 0,
        val maxLatency: Float = 0f,
        val minLatency: Float = 0f,
        val networkQualityScore: Int = 0,
        val playbackStability: Float = 0f, // 0-100
        val recommendations: List<String> = emptyList()
    )
    
    // 性能问题
    data class PerformanceIssue(
        val type: IssueType,
        val severity: Severity,
        val description: String,
        val timestamp: Long,
        val metricValues: Map<MetricType, Float> = emptyMap(),
        val suggestion: String = ""
    ) {
        enum class IssueType {
            LOW_FRAME_RATE,         // 低帧率
            HIGH_BUFFERING,         // 高缓冲
            NETWORK_CONGESTION,     // 网络拥塞
            HIGH_LATENCY,           // 高延迟
            AUDIO_SYNC_ISSUE,       // 音视频同步问题
            HIGH_CPU_USAGE,         // 高CPU使用率
            MEMORY_PRESSURE,        // 内存压力
            DECODER_STRUGGLING      // 解码器困难
        }
        
        enum class Severity {
            LOW, MEDIUM, HIGH, CRITICAL
        }
    }
    
    // 监控配置
    data class MonitoringConfig(
        val enabled: Boolean = true,
        val samplingIntervalMs: Long = 1000, // 采样间隔
        val logToFile: Boolean = false,
        val alertThresholds: Map<MetricType, Threshold> = defaultThresholds(),
        val maxHistorySize: Int = 1000
    )
    
    // 阈值配置
    data class Threshold(
        val warning: Float,
        val critical: Float,
        val min: Float? = null,
        val max: Float? = null
    )
    
    private var config = MonitoringConfig()

    /**
     * 设置监控配置
     */
    fun setMonitoringConfig(newConfig: MonitoringConfig) {
        config = newConfig
        Log.d(TAG, "监控配置已更新: $newConfig")
    }
    private var isMonitoring = false
    private val handler = Handler(Looper.getMainLooper())
    private val monitoringRunnable = object : Runnable {
        override fun run() {
            if (isMonitoring) {
                collectMetrics()
                handler.postDelayed(this, config.samplingIntervalMs)
            }
        }
    }
    
    // 指标存储
    private val metricHistory = ConcurrentHashMap<MetricType, MutableList<MetricData>>()
    private val issueHistory = mutableListOf<PerformanceIssue>()
    
    // 计数器
    private val frameCount = AtomicInteger(0)
    private val droppedFrameCount = AtomicInteger(0)
    private val totalBytesReceived = AtomicLong(0)
    private var lastFrameTime = 0L
    private var lastBitrateUpdateTime = 0L
    private var lastBytesReceived = 0L
    
    // 事件监听器
    interface PerformanceEventListener {
        fun onMetricUpdated(metric: MetricData)
        fun onIssueDetected(issue: PerformanceIssue)
        fun onPerformanceAlert(alert: PerformanceAlert)
    }
    
    data class PerformanceAlert(
        val message: String,
        val severity: PerformanceIssue.Severity,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    private var eventListener: PerformanceEventListener? = null
    
    companion object {
        /**
         * 默认阈值配置
         */
        fun defaultThresholds(): Map<MetricType, Threshold> {
            return mapOf(
                MetricType.FRAME_RATE to Threshold(
                    warning = 20f,
                    critical = 15f,
                    min = null,
                    max = 60f
                ),
                MetricType.BUFFER_LEVEL to Threshold(
                    warning = 20f,
                    critical = 10f,
                    min = 0f,
                    max = 100f
                ),
                MetricType.NETWORK_BITRATE to Threshold(
                    warning = 1000000f, // 1 Mbps
                    critical = 500000f,  // 500 Kbps
                    min = null,
                    max = null
                ),
                MetricType.RENDERING_LATENCY to Threshold(
                    warning = 200f,
                    critical = 500f,
                    min = null,
                    max = null
                ),
                MetricType.DROPPED_FRAMES to Threshold(
                    warning = 10f,
                    critical = 30f,
                    min = null,
                    max = null
                ),
                MetricType.AUDIO_SYNC_OFFSET to Threshold(
                    warning = 100f,
                    critical = 300f,
                    min = -300f,
                    max = 300f
                ),
                MetricType.CPU_USAGE to Threshold(
                    warning = 70f,
                    critical = 90f,
                    min = null,
                    max = 100f
                ),
                MetricType.NETWORK_QUALITY to Threshold(
                    warning = 60f,
                    critical = 30f,
                    min = 0f,
                    max = 100f
                )
            )
        }
    }
    
    /**
     * 开始性能监控
     */
    fun startMonitoring(config: MonitoringConfig? = null) {
        val effectiveConfig = config ?: MonitoringConfig(
            enabled = PerformanceConfig.DEFAULT_MONITORING_ENABLED, // 正式版本默认关闭
            samplingIntervalMs = PerformanceConfig.DEFAULT_SAMPLING_INTERVAL_MS,
            maxHistorySize = PerformanceConfig.DEFAULT_HISTORY_SIZE
        )

        if (!effectiveConfig.enabled) {
            Log.d(TAG, "性能监控已被禁用，无法启动")
            return
        }

        if (isMonitoring) {
            Log.w(TAG, "性能监控已经在运行中")
            return
        }

        this.config = effectiveConfig

        // 清空历史数据
        metricHistory.clear()
        issueHistory.clear()
        resetCounters()

        isMonitoring = true
        Log.d(TAG, "开始性能监控，采样间隔: ${this.config.samplingIntervalMs}ms")

        // 开始定时收集指标
        handler.post(monitoringRunnable)
    }
    
    /**
     * 停止性能监控
     */
    fun stopMonitoring() {
        if (!isMonitoring) return
        
        isMonitoring = false
        handler.removeCallbacks(monitoringRunnable)
        Log.d(TAG, "停止性能监控")
    }
    
    /**
     * 设置事件监听器
     */
    fun setEventListener(listener: PerformanceEventListener) {
        this.eventListener = listener
    }
    
    /**
     * 收集性能指标
     */
    private fun collectMetrics() {
        if (!isMonitoring) return
        
        // 收集各种指标
        collectFrameRate()
        collectBufferLevel()
        collectNetworkMetrics()
        collectSystemMetrics()
        
        // 检查性能问题
        checkPerformanceIssues()
    }
    
    /**
     * 收集帧率指标
     */
    fun collectFrameRate() {
        // 这里应该从播放器获取实际帧率
        // 简化实现：模拟帧率计算
        val currentTime = SystemClock.elapsedRealtime()
        if (lastFrameTime > 0) {
            val elapsed = currentTime - lastFrameTime
            if (elapsed > 0) {
                val frames = frameCount.getAndSet(0)
                val fps = (frames * 1000f / elapsed).coerceIn(0f, 60f)
                
                val metric = MetricData(
                    type = MetricType.FRAME_RATE,
                    value = fps,
                    unit = "fps"
                )
                addMetric(metric)
            }
        }
        lastFrameTime = currentTime
    }
    
    /**
     * 收集缓冲级别
     */
    fun collectBufferLevel(bufferLevel: Float? = null) {
        val level = bufferLevel ?: 50f // 模拟值，实际应从播放器获取
        
        val metric = MetricData(
            type = MetricType.BUFFER_LEVEL,
            value = level,
            unit = "%"
        )
        addMetric(metric)
    }
    
    /**
     * 收集网络指标
     */
    fun collectNetworkMetrics(bytesReceived: Long? = null) {
        val currentTime = System.currentTimeMillis()
        
        // 计算比特率
        if (lastBitrateUpdateTime > 0 && bytesReceived != null) {
            val elapsed = (currentTime - lastBitrateUpdateTime) / 1000f // 秒
            if (elapsed > 0) {
                val bytesDiff = bytesReceived - lastBytesReceived
                val bitrate = (bytesDiff * 8 / elapsed).toFloat() // bps
                
                val metric = MetricData(
                    type = MetricType.NETWORK_BITRATE,
                    value = bitrate,
                    unit = "bps"
                )
                addMetric(metric)
                
                // 计算网络质量评分
                val networkQuality = calculateNetworkQuality(bitrate)
                val qualityMetric = MetricData(
                    type = MetricType.NETWORK_QUALITY,
                    value = networkQuality.toFloat(),
                    unit = "score"
                )
                addMetric(qualityMetric)
            }
        }
        
        if (bytesReceived != null) {
            lastBytesReceived = bytesReceived
            totalBytesReceived.addAndGet(bytesReceived - lastBytesReceived)
        }
        lastBitrateUpdateTime = currentTime
    }
    
    /**
     * 收集系统指标
     */
    private fun collectSystemMetrics() {
        // 这里可以收集CPU、内存使用率等系统指标
        // 简化实现：返回模拟值
        
        val cpuUsage = 30f // 模拟CPU使用率
        val memoryUsage = 40f // 模拟内存使用率
        
        addMetric(MetricData(MetricType.CPU_USAGE, cpuUsage, unit = "%"))
        addMetric(MetricData(MetricType.MEMORY_USAGE, memoryUsage, unit = "%"))
    }
    
    /**
     * 添加指标到历史记录
     */
    private fun addMetric(metric: MetricData) {
        val history = metricHistory.getOrPut(metric.type) { mutableListOf() }
        history.add(metric)
        
        // 限制历史记录大小
        if (history.size > config.maxHistorySize) {
            history.removeAt(0)
        }
        
        // 通知监听器
        eventListener?.onMetricUpdated(metric)
        
        // 记录日志
        if (config.logToFile) {
            Log.d(TAG, "指标更新: ${metric.type}=${metric.value}${metric.unit}")
        }
    }
    
    /**
     * 检查性能问题
     */
    private fun checkPerformanceIssues() {
        // 检查每个指标是否超过阈值
        config.alertThresholds.forEach { (metricType, threshold) ->
            val latestMetric = getLatestMetric(metricType)
            if (latestMetric != null) {
                checkMetricAgainstThreshold(metricType, latestMetric.value, threshold)
            }
        }
    }
    
    /**
     * 检查指标是否超过阈值
     */
    private fun checkMetricAgainstThreshold(
        metricType: MetricType,
        value: Float,
        threshold: Threshold
    ) {
        val severity = when {
            value <= threshold.critical -> PerformanceIssue.Severity.CRITICAL
            value <= threshold.warning -> PerformanceIssue.Severity.HIGH
            else -> null
        }
        
        if (severity != null) {
            val issueType = when (metricType) {
                MetricType.FRAME_RATE -> PerformanceIssue.IssueType.LOW_FRAME_RATE
                MetricType.BUFFER_LEVEL -> PerformanceIssue.IssueType.HIGH_BUFFERING
                MetricType.NETWORK_BITRATE -> PerformanceIssue.IssueType.NETWORK_CONGESTION
                MetricType.RENDERING_LATENCY -> PerformanceIssue.IssueType.HIGH_LATENCY
                MetricType.AUDIO_SYNC_OFFSET -> PerformanceIssue.IssueType.AUDIO_SYNC_ISSUE
                MetricType.CPU_USAGE -> PerformanceIssue.IssueType.HIGH_CPU_USAGE
                MetricType.MEMORY_USAGE -> PerformanceIssue.IssueType.MEMORY_PRESSURE
                else -> null
            }
            
            if (issueType != null) {
                val description = when (issueType) {
                    PerformanceIssue.IssueType.LOW_FRAME_RATE -> "帧率过低: ${value}fps"
                    PerformanceIssue.IssueType.HIGH_BUFFERING -> "缓冲级别过低: ${value}%"
                    PerformanceIssue.IssueType.NETWORK_CONGESTION -> "网络比特率过低: ${value}bps"
                    PerformanceIssue.IssueType.HIGH_LATENCY -> "渲染延迟过高: ${value}ms"
                    PerformanceIssue.IssueType.AUDIO_SYNC_ISSUE -> "音视频同步偏移: ${value}ms"
                    PerformanceIssue.IssueType.HIGH_CPU_USAGE -> "CPU使用率过高: ${value}%"
                    PerformanceIssue.IssueType.MEMORY_PRESSURE -> "内存使用率过高: ${value}%"
                    else -> "性能问题: ${metricType}=${value}"
                }
                
                val suggestion = when (issueType) {
                    PerformanceIssue.IssueType.LOW_FRAME_RATE -> "尝试降低视频质量或检查解码器性能"
                    PerformanceIssue.IssueType.HIGH_BUFFERING -> "增加网络缓存或检查网络连接"
                    PerformanceIssue.IssueType.NETWORK_CONGESTION -> "切换到低码率流或检查网络状况"
                    PerformanceIssue.IssueType.HIGH_LATENCY -> "减少缓冲延迟或优化渲染管道"
                    PerformanceIssue.IssueType.AUDIO_SYNC_ISSUE -> "调整音频延迟或检查解码时间戳"
                    PerformanceIssue.IssueType.HIGH_CPU_USAGE -> "降低解码复杂度或启用硬件加速"
                    PerformanceIssue.IssueType.MEMORY_PRESSURE -> "释放未使用的资源或降低缓存大小"
                    else -> "检查系统资源和网络连接"
                }
                
                val issue = PerformanceIssue(
                    type = issueType,
                    severity = severity,
                    description = description,
                    timestamp = System.currentTimeMillis(),
                    metricValues = mapOf(metricType to value),
                    suggestion = suggestion
                )
                
                issueHistory.add(issue)
                eventListener?.onIssueDetected(issue)
                
                // 发送警报
                val alert = PerformanceAlert(
                    message = "$description (严重性: $severity)",
                    severity = severity
                )
                eventListener?.onPerformanceAlert(alert)
                
                Log.w(TAG, "检测到性能问题: $description")
            }
        }
    }
    
    /**
     * 获取最新的指标值
     */
    private fun getLatestMetric(metricType: MetricType): MetricData? {
        return metricHistory[metricType]?.lastOrNull()
    }
    
    /**
     * 计算网络质量评分
     */
    private fun calculateNetworkQuality(bitrate: Float): Int {
        // 简化实现：根据比特率计算质量评分
        return when {
            bitrate > 5000000 -> 90  // >5 Mbps
            bitrate > 2000000 -> 80  // >2 Mbps
            bitrate > 1000000 -> 70  // >1 Mbps
            bitrate > 500000 -> 60   // >500 Kbps
            bitrate > 250000 -> 50   // >250 Kbps
            bitrate > 100000 -> 40   // >100 Kbps
            else -> 30               // 很差
        }
    }
    
    /**
     * 生成性能报告
     */
    fun generateReport(sessionId: String = "session_${System.currentTimeMillis()}"): PerformanceReport {
        val startTime = metricHistory.values.flatten().minOfOrNull { it.timestamp } ?: System.currentTimeMillis()
        val endTime = System.currentTimeMillis()
        
        // 计算摘要
        val summary = calculateSummary()
        
        val report = PerformanceReport(
            sessionId = sessionId,
            startTime = startTime,
            endTime = endTime,
            metrics = metricHistory,
            summary = summary,
            issues = issueHistory
        )
        
        Log.d(TAG, "生成性能报告: $sessionId, 问题数量: ${issueHistory.size}")
        
        return report
    }
    
    /**
     * 计算性能摘要
     */
    private fun calculateSummary(): PerformanceSummary {
        val frameRates = metricHistory[MetricType.FRAME_RATE]?.map { it.value } ?: emptyList()
        val bufferLevels = metricHistory[MetricType.BUFFER_LEVEL]?.map { it.value } ?: emptyList()
        val bitrates = metricHistory[MetricType.NETWORK_BITRATE]?.map { it.value } ?: emptyList()
        val droppedFrames = metricHistory[MetricType.DROPPED_FRAMES]?.map { it.value.toInt() } ?: emptyList()
        val networkQualities = metricHistory[MetricType.NETWORK_QUALITY]?.map { it.value.toInt() } ?: emptyList()

        // 计算平均值
        val averageFrameRate = if (frameRates.isNotEmpty()) {
            frameRates.average().toFloat()
        } else 0f

        val averageBufferLevel = if (bufferLevels.isNotEmpty()) {
            bufferLevels.average().toFloat()
        } else 0f

        val averageBitrate = if (bitrates.isNotEmpty()) {
            bitrates.average().toFloat()
        } else 0f

        val totalDroppedFrames = droppedFrames.sum()

        // 计算网络质量评分
        val networkQualityScore = if (networkQualities.isNotEmpty()) {
            networkQualities.average().toInt()
        } else 0

        // 计算播放稳定性（基于帧率和缓冲）
        val playbackStability = if (frameRates.isNotEmpty() && bufferLevels.isNotEmpty()) {
            val frameStability = (frameRates.filter { it >= 20 }.size.toFloat() / frameRates.size) * 100
            val bufferStability = (bufferLevels.filter { it >= 20 }.size.toFloat() / bufferLevels.size) * 100
            (frameStability + bufferStability) / 2
        } else 0f

        val recommendations = mutableListOf<String>()

        // 根据问题生成建议
        if (issueHistory.any { it.type == PerformanceIssue.IssueType.LOW_FRAME_RATE }) {
            recommendations.add("考虑降低视频质量或启用硬件加速")
        }

        if (issueHistory.any { it.type == PerformanceIssue.IssueType.HIGH_BUFFERING }) {
            recommendations.add("增加网络缓存或检查网络连接")
        }

        if (issueHistory.any { it.type == PerformanceIssue.IssueType.NETWORK_CONGESTION }) {
            recommendations.add("降低视频码率或使用更稳定的网络")
        }

        if (recommendations.isEmpty()) {
            recommendations.add("性能表现良好，无需优化")
        }

        return PerformanceSummary(
            averageFrameRate = averageFrameRate,
            averageBufferLevel = averageBufferLevel,
            averageBitrate = averageBitrate,
            totalDroppedFrames = totalDroppedFrames,
            networkQualityScore = networkQualityScore,
            playbackStability = playbackStability,
            recommendations = recommendations
        )
    }

    /**
     * 重置计数器
     */
    private fun resetCounters() {
        frameCount.set(0)
        droppedFrameCount.set(0)
        totalBytesReceived.set(0)
        lastFrameTime = 0L
        lastBitrateUpdateTime = 0L
        lastBytesReceived = 0L
    }
}
