package com.lizongying.mytv

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.SurfaceHolder
import androidx.media3.ui.PlayerView
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

/**
 * VLC播放器包装类
 * 实现UnifiedVideoPlayer接口，封装VLC播放器功能
 */
class VLCPlayerWrapper : UnifiedVideoPlayer {
    
    companion object {
        private const val TAG = "VLCPlayerWrapper"

        // VLC选项白名单，仅允许添加这些选项，防止注入攻击
        private val ALLOWED_OPTIONS = setOf(
            "network-caching", "live-caching", "file-caching", "disc-caching",
            "rtp-max-rate", "rtp-timeout", "rtp-port", "sout-rtp-mux",
            "rtp-buffer-size", "rtp-max-delay", "rtp-session-timeout",
            "avcodec-hw", "avcodec-threads", "avcodec-skiploopfilter",
            "avcodec-skip-frame", "avcodec-fast", "no-skip-frames",
            "drop-late-frames", "skip-frames",
            "aout", "audio-time-stretch", "audio-resampler",
            "audio-channels", "audio-samplerate",
            "vout", "android-display-chroma", "android-display-width",
            "android-display-height", "no-video-title-show", "no-osd", "no-spu",
            "clock-jitter", "clock-synchro", "no-audio-time-stretch", "audio-desync",
            "rtsp-tcp", "rtsp-frame-buffer-size", "cr-average", "sout-mux-caching",
            "rtp-max-dropout", "udp-buffer-size"
        )
    }
    
    private var context: Context? = null
    private var libVLC: LibVLC? = null
    private var mediaPlayer: MediaPlayer? = null
    private var eventListener: UnifiedVideoPlayer.EventListener? = null
    private var currentState: UnifiedVideoPlayer.PlayerState = UnifiedVideoPlayer.PlayerState.IDLE
    private var currentUrl: String? = null
    private var isPrepared = false
    
    // VLC特定选项
    private var vlcOptions: List<String> = VLCPlayerHelper.DEFAULT_OPTIONS
    private var performanceMode: PerformanceConfig.PerformanceMode = PerformanceConfig.PerformanceMode.BALANCED
    private var retryCount = 0
    private val maxRetryCount = 3
    private var lastErrorTime: Long = 0
    private val retryDelayMs = 2000L

    // RTP播放尝试策略
    private val rtpRetryStrategies = listOf(
        // 策略1: 硬件解码（性能优先）
        { media: Media ->
            Log.d(TAG, "RTP策略1: 硬件解码")
            media.setHWDecoderEnabled(true, false)
        },
        // 策略2: 软件解码（兼容性优先，某些设备硬件解码卡顿）
        { media: Media ->
            Log.d(TAG, "RTP策略2: 软件解码")
            media.setHWDecoderEnabled(false, false)
        }
    )
    private var currentRtpStrategyIndex = 0
    
    override fun initialize(context: Context): Boolean {
        this.context = context
        return try {
            // 重置重试计数和RTP策略索引
            retryCount = 0
            currentRtpStrategyIndex = 0

            // 极简配置：参考 inputstream.ffmpegdirect 策略——不干预底层，让播放器自己处理
            val options = arrayListOf(
                "--network-caching=3000",
                "--live-caching=3000",
                "--verbose=0"
            )

            Log.d(TAG, "使用极简初始化选项: $options")
            libVLC = LibVLC(context, options)
            mediaPlayer = MediaPlayer(libVLC)

            // 设置VLC事件监听器
            mediaPlayer?.setEventListener(object : MediaPlayer.EventListener {
                override fun onEvent(event: MediaPlayer.Event) {
                    handleVlcEvent(event)
                }
            })

            currentState = UnifiedVideoPlayer.PlayerState.INITIALIZED
            Log.d(TAG, "VLC播放器初始化成功")
            true
        } catch (e: Exception) {
            Log.e(TAG, "VLC播放器初始化失败", e)
            currentState = UnifiedVideoPlayer.PlayerState.ERROR
            eventListener?.onError("初始化失败: ${e.message}")

            return false
        }
    }
    
    /**
     * 使用简化选项初始化（用于重试）
     */
    private fun initializeWithSimplifiedOptions(context: Context): Boolean {
        return try {
            // 注意：必须使用ArrayList（可变列表），因为LibVLC内部会向列表中添加元素
            val simplifiedOptions = arrayListOf(
                "--network-caching=2000"
            )

            libVLC = LibVLC(context, simplifiedOptions)
            mediaPlayer = MediaPlayer(libVLC)
            
            mediaPlayer?.setEventListener(object : MediaPlayer.EventListener {
                override fun onEvent(event: MediaPlayer.Event) {
                    handleVlcEvent(event)
                }
            })
            
            currentState = UnifiedVideoPlayer.PlayerState.INITIALIZED
            Log.d(TAG, "使用简化选项初始化成功")
            true
        } catch (e: Exception) {
            Log.e(TAG, "简化选项初始化也失败", e)
            false
        }
    }
    
    override fun setVideoSurface(holder: SurfaceHolder?) {
        try {
            if (mediaPlayer == null) {
                Log.w(TAG, "VLC播放器未初始化，保存SurfaceHolder待后续设置")
                // 保存holder，等播放器初始化后再设置
                return
            }

            if (holder != null && holder.surface != null && holder.surface.isValid) {
                val displayMetrics = context?.resources?.displayMetrics
                val screenWidth = displayMetrics?.widthPixels ?: holder.surfaceFrame.width()
                val screenHeight = displayMetrics?.heightPixels ?: holder.surfaceFrame.height()
                Log.d(TAG, "设置Surface: ${holder.surface}, 强制全屏尺寸: ${screenWidth}x${screenHeight}")
                val vout = mediaPlayer?.vlcVout
                if (vout != null) {
                    // 对于LibVLC，setVideoSurface直接绑定Surface即可
                    vout.setVideoSurface(holder.surface, holder)
                    vout.setWindowSize(screenWidth, screenHeight)
                    // 只有当前没有attach过才调用attachViews
                    if (!vout.areViewsAttached()) {
                        vout.attachViews()
                    }
                    mediaPlayer?.updateVideoSurfaces()
                    Log.d(TAG, "Surface设置成功")
                } else {
                    Log.e(TAG, "vlcVout为空，无法设置Surface")
                }
            } else {
                mediaPlayer?.vlcVout?.detachViews()
                Log.d(TAG, "VLC清除视频Surface")
            }
        } catch (e: Exception) {
            Log.e(TAG, "VLC设置视频Surface失败: ${e.message}", e)
        }
    }

    override fun setPlayerView(playerView: PlayerView?) {
        // VLC不使用PlayerView，空实现
        Log.w(TAG, "VLC播放器不支持PlayerView")
    }
    
    override fun setEventListener(listener: UnifiedVideoPlayer.EventListener?) {
        this.eventListener = listener
    }
    
    override fun prepare(url: String, options: Map<String, Any>?) {
        if (mediaPlayer == null || libVLC == null) {
            Log.e(TAG, "播放器未初始化")
            eventListener?.onError("播放器未初始化")
            return
        }

        currentUrl = url
        currentState = UnifiedVideoPlayer.PlayerState.PREPARING
        eventListener?.onStateChanged(currentState)

        try {
            Log.d(TAG, "准备播放RTP流: $url")

            // LibVLC Android播放组播的标准格式: udp://@239.x.x.x:port
            // @符号表示任意源组播(ASM)，这是IPTV组播的标准写法
            val mediaUrl = when {
                url.startsWith("rtp://") && !url.contains("@") -> {
                    url.replaceFirst("rtp://", "udp://@")
                }
                url.startsWith("udp://") && !url.contains("@") -> {
                    url.replaceFirst("udp://", "udp://@")
                }
                else -> url
            }
            Log.d(TAG, "LibVLC组播地址格式: $mediaUrl (原始: $url)")

            // 【关键修复】LibVLC Android中，字符串构造会被当作文件路径！
            // 网络URL必须使用Uri构造，否则VLC会尝试用file access打开
            val uri = android.net.Uri.parse(mediaUrl)
            Log.d(TAG, "解析后的Uri: $uri")
            val media = Media(libVLC, uri)
            Log.d(TAG, "使用Uri构造创建Media对象成功")

            // 硬件解码：尝试启用，失败则回退软件解码
            try {
                media.setHWDecoderEnabled(true, true)
                Log.d(TAG, "硬件解码已启用")
            } catch (e: Exception) {
                Log.w(TAG, "硬件解码不支持，改用软件解码", e)
                media.setHWDecoderEnabled(false, false)
            }

            // 极简媒体选项：参考 inputstream.ffmpegdirect —— 不强制demuxer，不干预同步
            // 只保留基础缓存，其余让VLC自动处理（和FFmpeg默认策略一致）
            media.addOption(":network-caching=3000")
            media.addOption(":live-caching=3000")

            // 应用用户选项
            options?.forEach { (key, value) ->
                val option = when (value) {
                    is String -> ":$key=$value"
                    is Boolean -> ":$key=${if (value) "1" else "0"}"
                    is Number -> ":$key=$value"
                    else -> ":$key"
                }
                media.addOption(option)
                Log.d(TAG, "应用自定义选项: $option")
            }

            // 设置媒体到播放器
            mediaPlayer?.media = media
            Log.d(TAG, "媒体已设置到播放器")
            media.release()

            isPrepared = true
            currentState = UnifiedVideoPlayer.PlayerState.READY
            eventListener?.onStateChanged(currentState)
            Log.d(TAG, "播放器准备就绪，等待播放命令")

        } catch (e: Exception) {
            Log.e(TAG, "准备播放失败", e)
            currentState = UnifiedVideoPlayer.PlayerState.ERROR
            eventListener?.onStateChanged(currentState)
            eventListener?.onError("准备失败: ${e.message}")
            lastErrorTime = System.currentTimeMillis()
        }
    }
    
    // 暂时移除复杂的优化选项，使用基础配置
    /**
     * 获取当前性能模式的缓存时间
     */
    private fun getCacheTimeForMode(): Int {
        return when (performanceMode) {
            PerformanceConfig.PerformanceMode.LOW_LATENCY -> 300
            PerformanceConfig.PerformanceMode.HIGH_QUALITY -> 3000
            PerformanceConfig.PerformanceMode.NETWORK_OPTIMIZED -> 2000
            PerformanceConfig.PerformanceMode.POWER_SAVING -> 1000
            else -> 1500
        }
    }
    
    override fun start() {
        if (!isPrepared) {
            Log.e(TAG, "播放器未准备就绪")
            eventListener?.onError("播放器未准备就绪")
            return
        }

        try {
            Log.d(TAG, "调用VLC play()方法")
            val playResult = mediaPlayer?.play()
            Log.d(TAG, "play()返回结果: $playResult")

            currentState = UnifiedVideoPlayer.PlayerState.PLAYING
            eventListener?.onStateChanged(currentState)
            Log.d(TAG, "播放命令已发送，等待解码器启动...")
        } catch (e: Exception) {
            Log.e(TAG, "开始播放失败", e)
            currentState = UnifiedVideoPlayer.PlayerState.ERROR
            eventListener?.onStateChanged(currentState)
            eventListener?.onError("开始播放失败: ${e.message}")
        }
    }
    
    override fun pause() {
        try {
            mediaPlayer?.pause()
            currentState = UnifiedVideoPlayer.PlayerState.PAUSED
            eventListener?.onStateChanged(currentState)
            Log.d(TAG, "播放暂停")
        } catch (e: Exception) {
            Log.e(TAG, "暂停播放失败", e)
            eventListener?.onError("暂停失败: ${e.message}")
        }
    }
    
    override fun stop() {
        try {
            mediaPlayer?.stop()
            currentState = UnifiedVideoPlayer.PlayerState.STOPPED
            eventListener?.onStateChanged(currentState)
            Log.d(TAG, "播放停止")
        } catch (e: Exception) {
            Log.e(TAG, "停止播放失败", e)
            eventListener?.onError("停止失败: ${e.message}")
        }
    }
    
    override fun resume() {
        if (currentState == UnifiedVideoPlayer.PlayerState.PAUSED) {
            start()
        }
    }
    
    override fun release() {
        try {
            stop()
            mediaPlayer?.release()
            libVLC?.release()
            mediaPlayer = null
            libVLC = null
            context = null
            currentState = UnifiedVideoPlayer.PlayerState.IDLE
            isPrepared = false
            // 重置所有状态
            retryCount = 0
            lastErrorTime = 0
            currentRtpStrategyIndex = 0
            Log.d(TAG, "VLC播放器资源已释放")
        } catch (e: Exception) {
            Log.e(TAG, "释放资源时出错", e)
        }
    }
    
    override fun isPlaying(): Boolean {
        return mediaPlayer?.isPlaying ?: false
    }
    
    override fun getCurrentPosition(): Long {
        return mediaPlayer?.time ?: 0
    }
    
    override fun getDuration(): Long {
        return mediaPlayer?.length ?: 0
    }
    
    override fun seekTo(position: Long) {
        try {
            mediaPlayer?.time = position
            Log.d(TAG, "跳转到位置: $position")
        } catch (e: Exception) {
            Log.e(TAG, "跳转失败", e)
        }
    }
    
    override fun getPlayerType(): UnifiedVideoPlayer.PlayerType {
        return UnifiedVideoPlayer.PlayerType.VLC_PLAYER
    }
    
    override fun getPlayerState(): UnifiedVideoPlayer.PlayerState {
        return currentState
    }
    
    override fun setPlaybackSpeed(speed: Float) {
        try {
            mediaPlayer?.rate = speed
            Log.d(TAG, "设置播放速度: $speed")
        } catch (e: Exception) {
            Log.e(TAG, "设置播放速度失败", e)
        }
    }
    
    override fun setVolume(volume: Float) {
        try {
            mediaPlayer?.volume = (volume * 100).toInt()
            Log.d(TAG, "设置音量: $volume")
        } catch (e: Exception) {
            Log.e(TAG, "设置音量失败", e)
        }
    }
    
    override fun supportsProtocol(url: String): Boolean {
        return PlayerFactory.detectPlayerType(url) == UnifiedVideoPlayer.PlayerType.VLC_PLAYER
    }

    override fun reconfigure(options: Map<String, Any>) {
        Log.d(TAG, "重新配置播放器: $options")
        if (currentUrl != null) {
            stop()
            prepare(currentUrl!!, options)
            start()
        }
    }
    
    /**
     * 设置VLC特定选项
     */
    fun setVLCOptions(options: List<String>) {
        this.vlcOptions = options
    }
    
    /**
     * 设置性能模式
     */
    fun setPerformanceMode(mode: PerformanceConfig.PerformanceMode) {
        this.performanceMode = mode
        Log.d(TAG, "设置性能模式: $mode")
        
        // 如果播放器已经初始化，可以重新应用选项
        if (libVLC != null && currentState != UnifiedVideoPlayer.PlayerState.IDLE) {
            Log.d(TAG, "播放器已初始化，性能模式将在下次播放时生效")
        }
    }
    
    /**
     * 获取当前性能模式
     */
    fun getPerformanceMode(): PerformanceConfig.PerformanceMode {
        return performanceMode
    }
    
    /**
     * 获取重试计数
     */
    fun getRetryCount(): Int {
        return retryCount
    }
    
    /**
     * 重置重试计数
     */
    fun resetRetryCount() {
        retryCount = 0
        lastErrorTime = 0
        Log.d(TAG, "重试计数已重置")
    }
    
    /**
     * 自适应调整性能模式（根据网络质量）
     * @param networkQuality 网络质量评分（0-100，越高越好）
     */
    fun adaptPerformanceMode(networkQuality: Int) {
        val newMode = PerformanceConfig.getAutoPerformanceMode(networkQuality, true)
        if (newMode != performanceMode) {
            Log.d(TAG, "根据网络质量($networkQuality)自适应调整性能模式: $performanceMode -> $newMode")
            setPerformanceMode(newMode)
            
            // 如果正在播放，可以尝试重新应用新设置
            if (currentState == UnifiedVideoPlayer.PlayerState.PLAYING && currentUrl != null) {
                Log.d(TAG, "正在播放，将应用新的性能设置")
                // 在实际应用中，这里可以触发重新连接或参数更新
            }
        }
    }
    
    /**
     * 处理VLC事件
     */
    private fun handleVlcEvent(event: MediaPlayer.Event) {
        when (event.type) {
            MediaPlayer.Event.Opening -> {
                Log.d(TAG, "媒体正在打开...")
                // 重置重试计数，因为正在尝试重新连接
                retryCount = 0
            }
            MediaPlayer.Event.Buffering -> {
                val percent = event.buffering
                eventListener?.onBufferingUpdate(percent.toInt())

                // 仅在有意义的时刻输出日志，避免高频I/O干扰解码
                if (percent == 0f && currentState == UnifiedVideoPlayer.PlayerState.PLAYING) {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastErrorTime > 5000) {
                        Log.w(TAG, "长时间缓冲为0%，可能网络中断或未收到RTP数据包")
                    }
                }
            }
            MediaPlayer.Event.Playing -> {
                Log.d(TAG, "开始播放")
                currentState = UnifiedVideoPlayer.PlayerState.PLAYING
                eventListener?.onStateChanged(currentState)
                // 播放成功，重置错误计数和RTP策略索引
                retryCount = 0
                lastErrorTime = 0
                currentRtpStrategyIndex = 0
            }
            MediaPlayer.Event.Paused -> {
                Log.d(TAG, "播放暂停")
                currentState = UnifiedVideoPlayer.PlayerState.PAUSED
                eventListener?.onStateChanged(currentState)
            }
            MediaPlayer.Event.Stopped -> {
                Log.d(TAG, "播放停止")
                currentState = UnifiedVideoPlayer.PlayerState.STOPPED
                eventListener?.onStateChanged(currentState)
            }
            MediaPlayer.Event.EndReached -> {
                Log.d(TAG, "播放结束")
                currentState = UnifiedVideoPlayer.PlayerState.ENDED
                eventListener?.onStateChanged(currentState)
                eventListener?.onPlaybackCompleted()
            }
            MediaPlayer.Event.EncounteredError -> {
                handlePlaybackError(event)
            }
            MediaPlayer.Event.TimeChanged -> {
                val time = event.timeChanged
                eventListener?.onProgressUpdate(time, getDuration())
            }
            MediaPlayer.Event.PositionChanged -> {
                // PositionChanged事件通常用于百分比更新，这里我们不处理，避免重复更新
                // 进度更新统一使用TimeChanged事件
            }
            MediaPlayer.Event.Vout -> {
                val voutCount = event.voutCount
                Log.d(TAG, "视频输出事件，视频轨道数量: $voutCount")
                if (voutCount > 0) {
                    val vout = mediaPlayer?.vlcVout
                    if (vout != null && vout.areViewsAttached()) {
                        // 强制重新设置窗口尺寸为全屏，防止卡顿后画面缩小
                        val displayMetrics = context?.resources?.displayMetrics
                        val screenWidth = displayMetrics?.widthPixels ?: 1920
                        val screenHeight = displayMetrics?.heightPixels ?: 1080
                        vout.setWindowSize(screenWidth, screenHeight)
                        mediaPlayer?.updateVideoSurfaces()
                        Log.d(TAG, "视频流已就绪，强制设置全屏尺寸: ${screenWidth}x${screenHeight}")
                    }
                } else {
                    Log.w(TAG, "没有检测到视频流，仅播放音频")
                }
            }
            // MediaPlayer.Event.ScrambledChanged -> {
            //     Log.d(TAG, "加密变化")
            // }
            else -> {
                Log.d(TAG, "其他VLC事件: ${event.type}")
            }
        }
    }

    /**
     * 处理播放错误
     */
    private fun handlePlaybackError(event: MediaPlayer.Event) {
        Log.e(TAG, "========================================")
        Log.e(TAG, "VLC播放错误发生!")
        Log.e(TAG, "错误事件类型: ${event.type} (EncounteredError=${MediaPlayer.Event.EncounteredError})")
        Log.e(TAG, "当前URL: $currentUrl")
        Log.e(TAG, "当前状态: $currentState")
        Log.e(TAG, "播放器是否正在播放: ${mediaPlayer?.isPlaying}")
        Log.e(TAG, "========================================")

        currentState = UnifiedVideoPlayer.PlayerState.ERROR
        eventListener?.onStateChanged(currentState)

        val currentTime = System.currentTimeMillis()
        val timeSinceLastError = currentTime - lastErrorTime
        Log.d(TAG, "距离上次错误时间: ${timeSinceLastError}ms")

        // 记录错误时间
        lastErrorTime = currentTime

        // 错误消息
        val errorMessage = "VLC播放错误 (事件码: ${event.type}), 请检查网络连接和流媒体地址"

        eventListener?.onError(errorMessage)

        // 检查是否需要自动重试
        if (shouldAutoRetry()) {
            Log.w(TAG, "尝试自动重试播放 (第 ${retryCount + 1} 次)")
            retryCount++

            // 如果是RTP流，切换到下一个策略
            currentUrl?.let { url ->
                if ((url.startsWith("rtp://") || url.startsWith("udp://")) &&
                    currentRtpStrategyIndex < rtpRetryStrategies.size - 1
                ) {
                    currentRtpStrategyIndex++
                    Log.i(TAG, "RTP播放失败，切换到下一个尝试策略: $currentRtpStrategyIndex")
                }
            }

            // 延迟后重试
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (currentUrl != null && retryCount <= maxRetryCount) {
                    Log.d(TAG, "执行自动重试...")
                    // 先停止当前播放
                    stop()
                    // 重新准备和播放
                    prepare(currentUrl!!, null)
                    start()
                } else {
                    // 重试次数用完，重置策略索引
                    currentRtpStrategyIndex = 0
                }
            }, retryDelayMs)
        } else {
            Log.w(TAG, "已达到最大重试次数或不需要重试")
            // 重置策略索引
            currentRtpStrategyIndex = 0
        }
    }
    
    /**
     * 检查是否应该自动重试
     */
    private fun shouldAutoRetry(): Boolean {
        // 检查重试次数
        if (retryCount >= maxRetryCount) {
            return false
        }
        
        // 检查错误间隔时间（避免频繁重试）
        val currentTime = System.currentTimeMillis()
        if (lastErrorTime > 0 && currentTime - lastErrorTime < 1000) {
            return false // 1秒内再次出错，不立即重试
        }
        
        // 检查当前状态和URL
        if (currentUrl == null || currentState == UnifiedVideoPlayer.PlayerState.IDLE) {
            return false
        }
        
        return true
    }
}