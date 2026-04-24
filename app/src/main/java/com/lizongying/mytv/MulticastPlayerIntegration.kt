package com.lizongying.mytv

import android.content.Context
import android.util.Log
import kotlinx.coroutines.runBlocking
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * 组播播放器集成器
 * 协调组播播放相关的所有组件，提供统一的组播播放接口
 */
class MulticastPlayerIntegration(context: Context) {
    private val TAG = "MulticastPlayerIntegration"

    private val appContext = context.applicationContext

    // 网络检测器
    private val networkDetector = NetworkDetector

    // 性能监控器
    private var performanceMonitor: PerformanceMonitor? = null

    // 当前播放的组播URL
    private var currentMulticastUrl: String? = null

    // 当前使用的播放器
    private var currentPlayer: UnifiedVideoPlayer? = null

    // 播放状态监听器
    private var playbackStateListener: PlaybackStateListener? = null

    // 错误重试计数器
    private var retryCount = 0
    private val maxRetryCount = 3
    private val retryDelayMs = 2000L

    // 自动重试定时器
    private var retryTimer: java.util.Timer? = null

    /**
     * 播放状态监听器
     */
    interface PlaybackStateListener {
        fun onMulticastStarted(url: String)
        fun onMulticastStopped()
        fun onMulticastError(error: String)
        fun onNetworkQualityChanged(quality: Int)
        fun onPerformanceIssue(issue: PerformanceMonitor.PerformanceIssue)
    }

    /**
     * 初始化并开始播放组播流
     */
    fun startMulticastStream(url: String, listener: PlaybackStateListener): Boolean {
        Log.d(TAG, "开始播放组播流: $url")

        // 检查URL是否为组播流
        if (!RTPProtocolDetector.isMulticastStream(url)) {
            Log.w(TAG, "URL不是组播流: $url")
            listener.onMulticastError("不是有效的组播流地址")
            return false
        }

        // 检查网络是否支持组播
        val isMulticastSupported = networkDetector.isMulticastSupported()
        if (!isMulticastSupported) {
            Log.w(TAG, "网络不支持组播")
            listener.onMulticastError("网络不支持组播，请检查IGMP设置")
            return false
        }

        // 固定网络质量评估（避免后台ping国外地址），根据连接类型给分
        val networkQuality = if (networkDetector.isLocalNetwork(appContext)) 70 else 30
        listener.onNetworkQualityChanged(networkQuality)

        // 设置监听器
        playbackStateListener = listener
        currentMulticastUrl = url

        // 选择合适的播放器：组播流使用GSYVideoPlayer
        val playerType = when {
            url.startsWith("rtsp://") || url.startsWith("rtp://") || url.startsWith("udp://") -> {
                Log.d(TAG, "组播流使用GSYVideoPlayer")
                UnifiedVideoPlayer.PlayerType.GSY_PLAYER
            }
            else -> {
                Log.d(TAG, "使用ExoPlayer播放器")
                UnifiedVideoPlayer.PlayerType.EXO_PLAYER
            }
        }

        // 创建播放器
        currentPlayer = createPlayer(playerType)

        // 初始化播放器
        if (!currentPlayer?.initialize(appContext)!!) {
            Log.e(TAG, "播放器初始化失败")
            cleanup()
            listener.onMulticastError("播放器初始化失败")
            return false
        }

        // 设置事件监听器
        currentPlayer?.setEventListener(object : UnifiedVideoPlayer.EventListener {
            override fun onStateChanged(state: UnifiedVideoPlayer.PlayerState) {
                Log.d(TAG, "播放器状态变化: $state")

                when (state) {
                    UnifiedVideoPlayer.PlayerState.PLAYING -> {
                        Log.d(TAG, "组播流开始播放")
                        startPerformanceMonitoring()
                        retryCount = 0 // 播放成功，重置重试计数
                        listener.onMulticastStarted(url)
                    }
                    UnifiedVideoPlayer.PlayerState.ERROR -> {
                        Log.e(TAG, "组播播放错误")
                        handlePlaybackError("组播播放错误")
                    }
                    else -> {
                        // 其他状态不需要特殊处理
                    }
                }
            }

            override fun onError(error: String) {
                Log.e(TAG, "播放器错误: $error")
                handlePlaybackError(error)
            }

            override fun onBufferingUpdate(percent: Int) {
                Log.d(TAG, "缓冲进度: $percent%")
                // 可以根据缓冲进度调整性能模式
                adjustPerformanceModeBasedOnBuffer(percent)
            }

            override fun onProgressUpdate(position: Long, duration: Long) {
                // 更新进度信息
            }

            override fun onPlaybackCompleted() {
                Log.d(TAG, "组播播放完成（通常是点播流）")
                // 对于组播流，这不应该发生
                stopMulticastStream()
            }
        })

        // 准备并播放
        val options = getOptimizedOptions(url, networkQuality)
        currentPlayer?.prepare(url, options)
        currentPlayer?.start()

        return true
    }

    /**
     * 停止组播流
     */
    fun stopMulticastStream() {
        Log.d(TAG, "停止组播流")

        // 停止性能监控
        performanceMonitor?.stopMonitoring()
        performanceMonitor = null

        // 取消重试定时器
        retryTimer?.cancel()
        retryTimer = null

        // 释放播放器
        currentPlayer?.release()
        currentPlayer = null

        currentMulticastUrl = null
        retryCount = 0

        playbackStateListener?.onMulticastStopped()
    }

    /**
     * 创建播放器实例
     */
    private fun createPlayer(type: UnifiedVideoPlayer.PlayerType): UnifiedVideoPlayer {
        return when (type) {
            UnifiedVideoPlayer.PlayerType.EXO_PLAYER -> {
                ExoPlayerWrapper()
            }
            UnifiedVideoPlayer.PlayerType.GSY_PLAYER -> {
                GsyPlayerWrapper()
            }
        }
    }

    /**
     * 获取优化选项
     */
    private fun getOptimizedOptions(url: String, networkQuality: Int): Map<String, Any> {
        val options = mutableMapOf<String, Any>()

        // 如果是RTP流，添加特殊优化
        if (url.startsWith("rtp://") || url.startsWith("udp://")) {
            // 增加网络缓存
            options["networkCache"] = if (networkQuality > 60) 1000 else 3000

            // 启用硬件解码
            options["hardwareDecode"] = true

            // 设置性能模式
            options["performanceMode"] = when {
                networkQuality > 70 -> PerformanceConfig.PerformanceMode.HIGH_QUALITY
                networkQuality > 40 -> PerformanceConfig.PerformanceMode.BALANCED
                else -> PerformanceConfig.PerformanceMode.NETWORK_OPTIMIZED
            }

            Log.d(TAG, "应用RTP流优化选项，网络质量: $networkQuality")
        }

        return options
    }

    /**
     * 处理播放错误
     */
    private fun handlePlaybackError(error: String) {
        Log.e(TAG, "处理播放错误: $error，重试次数: $retryCount")

        // 通知监听器
        playbackStateListener?.onMulticastError(error)

        // 检查是否需要重试
        if (shouldRetry()) {
            Log.w(TAG, "准备重试播放（第 ${retryCount + 1} 次）")
            scheduleRetry()
        } else {
            Log.w(TAG, "已达到最大重试次数，停止播放")
            stopMulticastStream()
        }
    }

    /**
     * 判断是否应该重试
     */
    private fun shouldRetry(): Boolean {
        if (retryCount >= maxRetryCount) {
            return false
        }

        // 简化网络检查：只看是否是本地网络
        val isLocal = networkDetector.isLocalNetwork(appContext)
        if (!isLocal) {
            Log.w(TAG, "非本地网络，不重试")
            return false
        }

        return true
    }

    /**
     * 安排重试
     */
    private fun scheduleRetry() {
        retryTimer = Timer()
        retryTimer?.schedule(object : TimerTask() {
            override fun run() {
                retryCount++
                Log.d(TAG, "执行重试（第 $retryCount 次）")

                // 重新开始播放
                currentMulticastUrl?.let { url ->
                    val isLocal = networkDetector.isLocalNetwork(appContext)
                    val networkQuality = if (isLocal) 70 else 30
                    val options = getOptimizedOptions(url, networkQuality)
                    currentPlayer?.prepare(url, options)
                    currentPlayer?.start()
                }
            }
        }, retryDelayMs)
    }

    /**
     * 根据缓冲进度调整性能模式
     */
    private fun adjustPerformanceModeBasedOnBuffer(bufferPercent: Int) {
        Log.d(TAG, "缓冲进度: $bufferPercent%，调整性能模式")
        // 性能模式调整逻辑（当前通过日志记录，如需可通过播放器接口扩展）
    }

    /**
     * 开始性能监控
     */
    private fun startPerformanceMonitoring() {
        performanceMonitor = PerformanceMonitor(appContext)
        performanceMonitor?.setEventListener(object : PerformanceMonitor.PerformanceEventListener {
            override fun onMetricUpdated(metric: PerformanceMonitor.MetricData) {
                // 可以在这里更新UI或其他响应
            }

            override fun onIssueDetected(issue: PerformanceMonitor.PerformanceIssue) {
                Log.w(TAG, "检测到性能问题: ${issue.description}")
                playbackStateListener?.onPerformanceIssue(issue)
            }

            override fun onPerformanceAlert(alert: PerformanceMonitor.PerformanceAlert) {
                Log.e(TAG, "性能警报: ${alert.message}")
            }
        })

        performanceMonitor?.startMonitoring()
    }

    /**
     * 清理资源
     */
    private fun cleanup() {
        Log.d(TAG, "清理资源")

        stopMulticastStream()
        currentMulticastUrl = null
        retryCount = 0

        // 重置网络检测
        // 可以在这里添加清理网络检测器的代码
    }

    /**
     * 获取当前组播流的详细信息
     */
    fun getMulticastInfo(): MulticastInfo? {
        if (currentMulticastUrl == null) {
            return null
        }

        val url = currentMulticastUrl!!
        val isLocal = networkDetector.isLocalNetwork(appContext)
        val protocolInfo = RTPProtocolDetector.getStreamProtocolDetails(url)

        return MulticastInfo(
            url = url,
            isMulticast = protocolInfo.isMulticast,
            networkQuality = if (isLocal) 70 else 30,
            isLocalNetwork = isLocal,
            protocolType = protocolInfo.protocolType,
            recommendedPlayer = protocolInfo.recommendedPlayer,
            currentPlayerType = currentPlayer?.getPlayerType(),
            isPlaying = currentPlayer?.isPlaying() ?: false,
            retryCount = retryCount
        )
    }

    /**
     * 组播信息数据类
     */
    data class MulticastInfo(
        val url: String,
        val isMulticast: Boolean,
        val networkQuality: Int,
        val isLocalNetwork: Boolean,
        val protocolType: RTPProtocolDetector.ProtocolType,
        val recommendedPlayer: RTPProtocolDetector.RecommendedPlayer,
        val currentPlayerType: UnifiedVideoPlayer.PlayerType?,
        val isPlaying: Boolean,
        val retryCount: Int
    )
}