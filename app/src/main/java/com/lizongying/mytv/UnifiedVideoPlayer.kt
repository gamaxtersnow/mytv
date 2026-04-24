package com.lizongying.mytv

import android.view.SurfaceHolder
import androidx.media3.ui.PlayerView

/**
 * 统一视频播放器接口
 * 抽象化不同播放器（ExoPlayer、ijkplayer）的核心功能，提供一致的API
 */
interface UnifiedVideoPlayer {
    
    /**
     * 播放器类型枚举
     */
    enum class PlayerType {
        EXO_PLAYER,  // ExoPlayer播放器
        GSY_PLAYER   // GSYVideoPlayer（基于ijkplayer持续维护，优化UDP/RTP组播）
    }
    
    /**
     * 播放器状态枚举
     */
    enum class PlayerState {
        IDLE,        // 空闲状态
        INITIALIZED, // 已初始化
        PREPARING,   // 准备中
        READY,       // 准备就绪
        BUFFERING,   // 缓冲中
        PLAYING,     // 播放中
        PAUSED,      // 已暂停
        STOPPED,     // 已停止
        COMPLETED,   // 播放完成
        ENDED,       // 播放结束
        ERROR        // 错误状态
    }
    
    /**
     * 播放器事件监听器
     */
    interface EventListener {
        /**
         * 播放器状态变化
         */
        fun onStateChanged(state: PlayerState)
        
        /**
         * 播放错误
         */
        fun onError(error: String)
        
        /**
         * 缓冲进度更新
         */
        fun onBufferingUpdate(percent: Int)
        
        /**
         * 播放进度更新
         */
        fun onProgressUpdate(position: Long, duration: Long)
        
        /**
         * 播放完成
         */
        fun onPlaybackCompleted()
    }
    
    /**
     * 初始化播放器
     * @param context 上下文
     * @return 是否初始化成功
     */
    fun initialize(context: android.content.Context): Boolean
    
    /**
     * 设置视频输出Surface
     * @param holder SurfaceHolder
     */
    fun setVideoSurface(holder: SurfaceHolder?)

    /**
     * 设置视频输出PlayerView（用于ExoPlayer）
     * @param playerView PlayerView
     */
    fun setPlayerView(playerView: PlayerView?)

    /**
     * 设置事件监听器
     */
    fun setEventListener(listener: EventListener?)
    
    /**
     * 准备播放
     * @param url 视频URL
     * @param options 播放选项（可选）
     */
    fun prepare(url: String, options: Map<String, Any>? = null)
    
    /**
     * 开始播放
     */
    fun start()
    
    /**
     * 暂停播放
     */
    fun pause()
    
    /**
     * 停止播放
     */
    fun stop()
    
    /**
     * 恢复播放
     */
    fun resume()
    
    /**
     * 释放播放器资源
     */
    fun release()
    
    /**
     * 是否正在播放
     */
    fun isPlaying(): Boolean
    
    /**
     * 获取当前播放位置（毫秒）
     */
    fun getCurrentPosition(): Long
    
    /**
     * 获取视频总时长（毫秒）
     */
    fun getDuration(): Long
    
    /**
     * 跳转到指定位置
     * @param position 位置（毫秒）
     */
    fun seekTo(position: Long)
    
    /**
     * 获取播放器类型
     */
    fun getPlayerType(): PlayerType
    
    /**
     * 获取当前播放器状态
     */
    fun getPlayerState(): PlayerState
    
    /**
     * 设置播放速度
     * @param speed 播放速度（1.0为正常速度）
     */
    fun setPlaybackSpeed(speed: Float)
    
    /**
     * 设置音量
     * @param volume 音量（0.0-1.0）
     */
    fun setVolume(volume: Float)
    
    /**
     * 是否支持特定协议
     * @param url 视频URL
     * @return 是否支持
     */
    fun supportsProtocol(url: String): Boolean

    /**
     * 重新配置播放器参数
     * @param options 新的播放选项
     */
    fun reconfigure(options: Map<String, Any>)
}