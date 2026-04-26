package com.gamaxtersnow.mytv

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 组播播放调试工具
 * 提供详细的调试日志记录、性能数据收集和分析功能
 */
object MulticastDebugTool {
    private const val TAG = "MulticastDebugTool"
    private const val LOG_DIR = "multicast_debug_logs"
    private const val MAX_LOG_SIZE = 10 * 1024 * 1024 // 10MB

    // 日志级别
    enum class LogLevel {
        DEBUG,      // 调试信息
        INFO,       // 一般信息
        WARN,       // 警告信息
        ERROR,      // 错误信息
        VERBOSE     // 详细信息
    }

    // 调会话信息
    data class DebugSession(
        val sessionId: String,
        val startTime: Long,
        val url: String,
        val playerType: UnifiedVideoPlayer.PlayerType,
        var endTime: Long? = null,
        val events: MutableList<DebugEvent> = mutableListOf(),
        val metrics: ConcurrentHashMap<String, MutableList<MetricData>> = ConcurrentHashMap()
    )

    // 调试事件
    data class DebugEvent(
        val timestamp: Long,
        val level: LogLevel,
        val category: String,
        val message: String,
        val details: Map<String, Any> = emptyMap()
    )

    // 指标数据
    data class MetricData(
        val timestamp: Long,
        val metricName: String,
        val value: Float,
        val unit: String = "",
        val metadata: Map<String, Any> = emptyMap()
    )

    // 统计数据
    data class DebugStatistics(
        val totalEvents: Int,
        val eventsByLevel: Map<LogLevel, Int>,
        val eventsByCategory: Map<String, Int>,
        val averageMetrics: Map<String, Float>,
        val peakMetrics: Map<String, Float>,
        val sessionDuration: Long
    )

    // 当前会话
    private var currentSession: DebugSession? = null

    // 全局事件计数器
    private val eventCounter = AtomicLong(0)

    // 日期格式化器
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    /**
     * 开始新的调试会话
     */
    fun startDebugSession(
        context: Context,
        url: String,
        playerType: UnifiedVideoPlayer.PlayerType
    ): String {
        val sessionId = "session_${System.currentTimeMillis()}_${eventCounter.getAndIncrement()}"

        Log.i(TAG, "开始调试会话: $sessionId, URL: $url, 播放器: $playerType")

        // 创建日志目录
        val logDir = File(context.getExternalFilesDir(null), LOG_DIR)
        if (!logDir.exists()) {
            logDir.mkdirs()
        }

        currentSession = DebugSession(
            sessionId = sessionId,
            startTime = System.currentTimeMillis(),
            url = url,
            playerType = playerType
        )

        // 记录会话开始事件
        logEvent(LogLevel.INFO, "SESSION_START", "调试会话开始", mapOf(
            "url" to url,
            "player_type" to playerType.toString(),
            "session_id" to sessionId
        ))

        return sessionId
    }

    /**
     * 结束当前调试会话
     */
    fun endDebugSession() {
        currentSession?.let { session ->
            session.endTime = System.currentTimeMillis()

            // 保存会话数据到文件
            // TODO: 需要传入Context参数
            // saveSessionToFile(context, session)

            // 生成统计报告
            val stats = generateStatistics(session)
            Log.i(TAG, "调试会话结束: ${session.sessionId}")
            Log.i(TAG, "统计信息: $stats")

            // 重置当前会话
            currentSession = null
        }
    }

    /**
     * 记录事件
     */
    fun logEvent(
        level: LogLevel,
        category: String,
        message: String,
        details: Map<String, Any> = emptyMap()
    ) {
        val timestamp = System.currentTimeMillis()
        val event = DebugEvent(
            timestamp = timestamp,
            level = level,
            category = category,
            message = message,
            details = details
        )

        // 添加到当前会话
        currentSession?.events?.add(event)

        // 输出到logcat
        val logMessage = "[$category] ${dateFormat.format(Date(timestamp))} - $message"
        when (level) {
            LogLevel.DEBUG -> Log.d(TAG, logMessage)
            LogLevel.INFO -> Log.i(TAG, logMessage)
            LogLevel.WARN -> Log.w(TAG, logMessage)
            LogLevel.ERROR -> Log.e(TAG, logMessage)
            LogLevel.VERBOSE -> Log.v(TAG, logMessage)
        }

        // 记录特殊事件
        when (category) {
            "PLAYER_ERROR" -> {
                Log.e(TAG, "播放器错误: $message, 详情: $details")
                // TODO: 需要传入Context参数
                // saveErrorToCrashReport(context, event)
            }
            "NETWORK_ISSUE" -> {
                Log.w(TAG, "网络问题: $message, 详情: $details")
            }
            "PERFORMANCE_ISSUE" -> {
                Log.w(TAG, "性能问题: $message, 详情: $details")
            }
        }
    }

    /**
     * 记录指标数据
     */
    fun recordMetric(
        metricName: String,
        value: Float,
        unit: String = "",
        metadata: Map<String, Any> = emptyMap()
    ) {
        val timestamp = System.currentTimeMillis()

        // 添加到当前会话
        currentSession?.metrics?.getOrPut(metricName) { mutableListOf() }?.add(
            MetricData(
                timestamp = timestamp,
                metricName = metricName,
                value = value,
                unit = unit,
                metadata = metadata
            )
        )

        // 记录特别重要的指标到logcat
        when (metricName) {
            "frame_rate" -> {
                Log.d(TAG, "帧率: ${value}${unit}")
                if (value < 20) {
                    logEvent(LogLevel.WARN, "PERFORMANCE_ISSUE", "帧率过低", mapOf(
                        "frame_rate" to value,
                        "unit" to unit
                    ))
                }
            }
            "buffer_level" -> {
                Log.d(TAG, "缓冲级别: ${value}${unit}")
                if (value < 10) {
                    logEvent(LogLevel.WARN, "BUFFER_LOW", "缓冲级别过低", mapOf(
                        "buffer_level" to value,
                        "unit" to unit
                    ))
                }
            }
            "network_quality" -> {
                Log.d(TAG, "网络质量: ${value}${unit}")
                if (value < 30) {
                    logEvent(LogLevel.WARN, "NETWORK_ISSUE", "网络质量差", mapOf(
                        "network_quality" to value,
                        "unit" to unit
                    ))
                }
            }
        }
    }

    /**
     * 记录播放状态变化
     */
    fun logPlaybackStateChange(
        state: UnifiedVideoPlayer.PlayerState,
        previousState: UnifiedVideoPlayer.PlayerState? = null,
        details: Map<String, Any> = emptyMap()
    ) {
        val message = "播放状态变化: $previousState -> $state"
        val eventDetails = mutableMapOf<String, Any>(
            "current_state" to state.toString(),
            "previous_state" to (previousState?.toString() ?: "null"),
            "timestamp" to System.currentTimeMillis()
        )
        eventDetails.putAll(details)

        logEvent(LogLevel.INFO, "PLAYBACK_STATE", message, eventDetails)
    }

    /**
     * 记录网络变化
     */
    fun logNetworkChange(
        networkQuality: Int,
        isMulticastSupported: Boolean,
        isLocalNetwork: Boolean,
        details: Map<String, Any> = emptyMap()
    ) {
        val message = "网络状态变化 - 质量: $networkQuality, 组播支持: $isMulticastSupported, 本地网络: $isLocalNetwork"
        val eventDetails = mapOf(
            "network_quality" to networkQuality,
            "multicast_supported" to isMulticastSupported,
            "local_network" to isLocalNetwork
        ) + details

        logEvent(LogLevel.INFO, "NETWORK_CHANGE", message, eventDetails)

        // 检查关键网络问题
        if (!isMulticastSupported) {
            logEvent(LogLevel.ERROR, "NETWORK_CRITICAL", "网络不支持组播")
        }
        if (!isLocalNetwork) {
            logEvent(LogLevel.WARN, "NETWORK_WARNING", "不是本地网络连接")
        }
        if (networkQuality < 20) {
            logEvent(LogLevel.ERROR, "NETWORK_CRITICAL", "网络质量极差", mapOf(
                "quality_score" to networkQuality
            ))
        }
    }

    /**
     * 记录性能问题
     */
    fun logPerformanceIssue(
        issueType: String,
        severity: String,
        message: String,
        details: Map<String, Any> = emptyMap()
    ) {
        logEvent(
            level = when (severity.lowercase()) {
                "low" -> LogLevel.WARN
                "medium" -> LogLevel.ERROR
                "high" -> LogLevel.ERROR
                "critical" -> LogLevel.ERROR
                else -> LogLevel.WARN
            },
            category = "PERFORMANCE_$issueType.uppercase()",
            message = message,
            details = details + mapOf("severity" to severity)
        )
    }

    /**
     * 记录调试信息
     */
    fun logDebug(category: String, message: String, details: Map<String, Any> = emptyMap()) {
        logEvent(LogLevel.DEBUG, category, message, details)
    }

    /**
     * 保存会话数据到文件
     */
    fun saveSessionToFile(context: Context, session: DebugSession) {
        try {
            val logDir = File(context.getExternalFilesDir(null), LOG_DIR)
            val sessionFile = File(logDir, "session_${session.sessionId}.json")

            // 检查文件大小，如果超过限制则删除最旧的文件
            if (sessionFile.exists() && sessionFile.length() > MAX_LOG_SIZE) {
                Log.w(TAG, "日志文件超过大小限制，删除最旧的文件")
                deleteOldestLogFile(logDir)
            }

            // 写入文件
            FileWriter(sessionFile, false).use { writer ->
                writer.write(session.toString())
            }

            Log.i(TAG, "会话数据已保存到: ${sessionFile.absolutePath}")
        } catch (e: IOException) {
            Log.e(TAG, "保存会话数据失败", e)
        }
    }

    /**
     * 删除最旧的日志文件
     */
    private fun deleteOldestLogFile(directory: File) {
        val files = directory.listFiles { file -> file.name.startsWith("session_") && file.name.endsWith(".json") }
        if (files != null && files.isNotEmpty()) {
            val oldestFile = files.minByOrNull { it.lastModified() }
            oldestFile?.delete()
        }
    }

    /**
     * 保存错误到崩溃报告
     */
    /**
     * 保存错误到崩溃报告
     */
    fun saveErrorToCrashReport(context: Context, event: DebugEvent) {
        try {

            val crashDir = File(context.getExternalFilesDir(null), "crash_reports")
            if (!crashDir.exists()) {
                crashDir.mkdirs()
            }

            val crashFile = File(crashDir, "crash_${event.timestamp}.txt")

            FileWriter(crashFile).use { writer ->
                writer.write("崩溃报告\n")
                writer.write("时间: ${dateFormat.format(Date(event.timestamp))}\n")
                writer.write("类别: ${event.category}\n")
                writer.write("消息: ${event.message}\n")
                writer.write("详情:\n")
                event.details.forEach { (key, value) ->
                    writer.write("$key: $value\n")
                }
            }

            Log.i(TAG, "错误报告已保存到: ${crashFile.absolutePath}")
        } catch (e: IOException) {
            Log.e(TAG, "保存错误报告失败", e)
        }
    }

    /**
     * 生成统计信息
     */
    private fun generateStatistics(session: DebugSession): DebugStatistics {
        val eventsByLevel = session.events.groupBy { it.level }
            .mapValues { it.value.size }

        val eventsByCategory = session.events.groupBy { it.category }
            .mapValues { it.value.size }

        val metricsMap = session.metrics.mapValues { (_, metrics) ->
            if (metrics.isNotEmpty()) {
                metrics.sumOf { it.value.toInt() } / metrics.size.toFloat()
            } else 0f
        }

        val peakMetrics = session.metrics.mapValues { (_, metrics) ->
            metrics.maxOfOrNull { it.value } ?: 0f
        }

        return DebugStatistics(
            totalEvents = session.events.size,
            eventsByLevel = eventsByLevel,
            eventsByCategory = eventsByCategory,
            averageMetrics = metricsMap,
            peakMetrics = peakMetrics,
            sessionDuration = (session.endTime ?: System.currentTimeMillis()) - session.startTime
        )
    }

    /**
     * 获取当前会话信息
     */
    fun getCurrentSessionInfo(): DebugSession? {
        return currentSession
    }

    /**
     * 导出调试数据
     */
    fun exportDebugData(context: Context, sessionId: String? = null): File? {
        val logDir = File(context.getExternalFilesDir(null), LOG_DIR)

        val targetFile = sessionId?.let {
            File(logDir, "session_$sessionId.json")
        } ?: File(logDir, "all_sessions_${System.currentTimeMillis()}.json")

        return targetFile.takeIf { it.exists() }
    }

    /**
     * 清理旧的调试文件
     */
    fun cleanupOldDebugFiles(context: Context, daysToKeep: Int = 7) {
        try {
            val logDir = File(context.getExternalFilesDir(null), LOG_DIR)
            val cutoffTime = System.currentTimeMillis() - (daysToKeep * 24 * 60 * 60 * 1000L)

            logDir.listFiles()?.forEach { file ->
                if (file.lastModified() < cutoffTime) {
                    file.delete()
                    Log.i(TAG, "删除旧文件: ${file.name}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "清理旧文件失败", e)
        }
    }
}