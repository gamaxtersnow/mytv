package com.lizongying.mytv

import android.content.Context
import android.view.SurfaceHolder
import android.util.Log
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

/**
 * VLC播放器助手类
 * 封装VLC播放RTP/RTSP流的核心功能
 */
class VLCPlayerHelper(private val context: Context) {
    private var libVLC: LibVLC? = null
    private var mediaPlayer: MediaPlayer? = null
    private var eventListener: EventListener? = null
    
    companion object {
        private const val TAG = "VLCPlayerHelper"

        // 默认VLC选项，针对RTP/RTSP流优化（使用平衡模式）
        val DEFAULT_OPTIONS = PerformanceConfig.getVLCOptionsForMode(
            PerformanceConfig.PerformanceMode.BALANCED
        )

        // RTP特定选项
        val RTP_OPTIONS = PerformanceConfig.getRTPOptimizedOptions()

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
        
        /**
         * 获取指定性能模式的VLC选项
         */
        fun getOptionsForMode(mode: PerformanceConfig.PerformanceMode): List<String> {
            return PerformanceConfig.getVLCOptionsForMode(mode)
        }
        
        /**
         * 根据网络质量自动获取VLC选项
         */
        fun getAutoOptions(networkQuality: Int, isLiveStream: Boolean = true): List<String> {
            val mode = PerformanceConfig.getAutoPerformanceMode(networkQuality, isLiveStream)
            return getOptionsForMode(mode)
        }
    }
    
    interface EventListener {
        fun onPlayerEvent(event: PlayerEvent)
        fun onError(error: String)
    }
    
    sealed class PlayerEvent {
        object Opening : PlayerEvent()
        data class Buffering(val percent: Float) : PlayerEvent()
        object Playing : PlayerEvent()
        object Paused : PlayerEvent()
        object Stopped : PlayerEvent()
        object EndReached : PlayerEvent()
        data class TimeChanged(val time: Long) : PlayerEvent()
        data class PositionChanged(val position: Float) : PlayerEvent()
    }
    
    /**
     * 初始化VLC播放器
     * @param options 自定义VLC选项，如果为null则使用默认选项
     */
    fun initialize(options: List<String>? = null): Boolean {
        return try {
            val vlcOptions = options ?: DEFAULT_OPTIONS
            libVLC = LibVLC(context, vlcOptions)
            mediaPlayer = MediaPlayer(libVLC)
            
            // 设置事件监听器
            mediaPlayer?.setEventListener(object : MediaPlayer.EventListener {
                override fun onEvent(event: MediaPlayer.Event) {
                    handleVlcEvent(event)
                }
            })
            
            Log.d(TAG, "VLC播放器初始化成功，选项: ${vlcOptions.size}个")
            true
        } catch (e: Exception) {
            Log.e(TAG, "VLC播放器初始化失败", e)
            eventListener?.onError("初始化失败: ${e.message}")
            false
        }
    }
    
    /**
     * 设置事件监听器
     */
    fun setEventListener(listener: EventListener) {
        this.eventListener = listener
    }
    
    /**
     * 设置视频输出Surface
     */
    fun setVideoSurface(holder: SurfaceHolder?) {
        // VLC Android SDK可能需要不同的Surface设置
        // 注意：新版本VLC可能期望VLCVideoLayout而不是SurfaceHolder
        try {
            // 使用@Suppress来抑制类型不匹配警告
            @Suppress("TYPE_MISMATCH")
            val result = mediaPlayer?.attachViews(holder, null, false, false)
            Log.d(TAG, "设置视频Surface成功: $result")
        } catch (e: Exception) {
            Log.e(TAG, "设置视频Surface失败: ${e.message}")
        }
    }
    
    /**
     * 播放RTP流
     * @param url RTP/RTSP流地址，例如: rtp://239.1.1.1:5000
     * @param options 媒体特定选项
     */
    fun playRtpStream(url: String, options: List<String>? = null): Boolean {
        if (mediaPlayer == null) {
            Log.e(TAG, "播放器未初始化")
            return false
        }
        
        return try {
            Log.d(TAG, "开始播放RTP流: $url")
            
            // 创建媒体对象
            val media = Media(libVLC, url)
            
            // 启用硬件解码
            media.setHWDecoderEnabled(true, false)
            
            // 添加默认选项
            media.addOption(":network-caching=1500")
            media.addOption(":live-caching=1500")
            media.addOption(":rtsp-tcp")
            media.addOption(":no-audio-time-stretch")
            
            // 添加RTP特定选项
            media.addOption(":rtp-max-rate=10000000")
            media.addOption(":rtp-timeout=60000000")
            
            // 添加用户自定义选项（白名单验证）
            options?.forEach { option ->
                // 拆分键值对，提取选项名
                val key = option.substringBefore('=').trim()
                if (key in ALLOWED_OPTIONS) { // 仅允许白名单内的选项
                    media.addOption(":$option")
                } else {
                    Log.w(TAG, "忽略不安全的VLC选项: $option")
                }
            }
            
            // 设置媒体并播放
            mediaPlayer?.media = media
            media.release()
            mediaPlayer?.play()
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "播放RTP流失败", e)
            eventListener?.onError("播放失败: ${e.message}")
            false
        }
    }
    
    /**
     * 播放RTSP流
     */
    fun playRtspStream(url: String): Boolean {
        return playRtpStream(url, listOf("rtsp-tcp"))
    }
    
    /**
     * 停止播放
     */
    fun stop() {
        try {
            mediaPlayer?.stop()
            Log.d(TAG, "播放已停止")
        } catch (e: Exception) {
            Log.e(TAG, "停止播放时出错", e)
        }
    }
    
    /**
     * 暂停播放
     */
    fun pause() {
        try {
            mediaPlayer?.pause()
            Log.d(TAG, "播放已暂停")
        } catch (e: Exception) {
            Log.e(TAG, "暂停播放时出错", e)
        }
    }
    
    /**
     * 恢复播放
     */
    fun resume() {
        try {
            mediaPlayer?.play()
            Log.d(TAG, "播放已恢复")
        } catch (e: Exception) {
            Log.e(TAG, "恢复播放时出错", e)
        }
    }
    
    /**
     * 是否正在播放
     */
    fun isPlaying(): Boolean {
        return mediaPlayer?.isPlaying ?: false
    }
    
    /**
     * 获取当前播放时间（毫秒）
     */
    fun getCurrentTime(): Long {
        return mediaPlayer?.time ?: 0
    }
    
    /**
     * 获取播放位置（0.0 - 1.0）
     */
    fun getPosition(): Float {
        return mediaPlayer?.position ?: 0f
    }
    
    /**
     * 设置播放位置
     */
    fun setPosition(position: Float) {
        try {
            mediaPlayer?.position = position
        } catch (e: Exception) {
            Log.e(TAG, "设置播放位置时出错", e)
        }
    }
    
    /**
     * 释放资源
     */
    fun release() {
        try {
            stop()
            mediaPlayer?.release()
            libVLC?.release()
            mediaPlayer = null
            libVLC = null
            Log.d(TAG, "VLC播放器资源已释放")
        } catch (e: Exception) {
            Log.e(TAG, "释放资源时出错", e)
        }
    }
    
    /**
     * 处理VLC事件
     */
    private fun handleVlcEvent(event: MediaPlayer.Event) {
        when (event.type) {
            MediaPlayer.Event.Opening -> {
                Log.d(TAG, "媒体正在打开...")
                eventListener?.onPlayerEvent(PlayerEvent.Opening)
            }
            MediaPlayer.Event.Buffering -> {
                val percent = event.buffering
                Log.d(TAG, "缓冲中: $percent%")
                eventListener?.onPlayerEvent(PlayerEvent.Buffering(percent))
            }
            MediaPlayer.Event.Playing -> {
                Log.d(TAG, "开始播放")
                eventListener?.onPlayerEvent(PlayerEvent.Playing)
            }
            MediaPlayer.Event.Paused -> {
                Log.d(TAG, "播放暂停")
                eventListener?.onPlayerEvent(PlayerEvent.Paused)
            }
            MediaPlayer.Event.Stopped -> {
                Log.d(TAG, "播放停止")
                eventListener?.onPlayerEvent(PlayerEvent.Stopped)
            }
            MediaPlayer.Event.EndReached -> {
                Log.d(TAG, "播放结束")
                eventListener?.onPlayerEvent(PlayerEvent.EndReached)
            }
            MediaPlayer.Event.EncounteredError -> {
                Log.e(TAG, "播放错误: ${event.type}")
                eventListener?.onError("播放错误: ${event.type}")
            }
            MediaPlayer.Event.TimeChanged -> {
                val time = event.timeChanged
                eventListener?.onPlayerEvent(PlayerEvent.TimeChanged(time))
            }
            MediaPlayer.Event.PositionChanged -> {
                val position = event.positionChanged
                eventListener?.onPlayerEvent(PlayerEvent.PositionChanged(position))
            }
            else -> {
                Log.d(TAG, "其他VLC事件: ${event.type}")
            }
        }
    }
}