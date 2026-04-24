package com.lizongying.mytv

import android.content.Context
import android.util.Log
import android.view.SurfaceHolder
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.ui.PlayerView

/**
 * ExoPlayer包装类
 * 实现UnifiedVideoPlayer接口，封装ExoPlayer功能
 */
class ExoPlayerWrapper : UnifiedVideoPlayer {
    
    companion object {
        private const val TAG = "ExoPlayerWrapper"
    }
    
    private var context: Context? = null
    private var player: ExoPlayer? = null
    private var eventListener: UnifiedVideoPlayer.EventListener? = null
    private var currentState: UnifiedVideoPlayer.PlayerState = UnifiedVideoPlayer.PlayerState.IDLE
    private var currentUrl: String? = null
    private var isPrepared = false

    // ExoPlayer配置
    private var exoPlayerConfig: ExoPlayer.Builder? = null
    
    override fun initialize(context: Context): Boolean {
        this.context = context
        return try {
            // 使用配置的builder或默认创建ExoPlayer
            player = exoPlayerConfig?.build() ?: ExoPlayer.Builder(context).build()

            // 设置播放器事件监听器
            player?.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    handlePlayerState(playbackState)
                }

                override fun onPlayerError(error: PlaybackException) {
                    Log.e(TAG, "ExoPlayer播放错误", error)
                    currentState = UnifiedVideoPlayer.PlayerState.ERROR
                    eventListener?.onStateChanged(currentState)
                    eventListener?.onError("ExoPlayer错误: ${error.message}")
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) {
                        currentState = UnifiedVideoPlayer.PlayerState.PLAYING
                        eventListener?.onStateChanged(currentState)
                    }
                }
            })

            currentState = UnifiedVideoPlayer.PlayerState.INITIALIZED
            Log.d(TAG, "ExoPlayer初始化成功")
            true
        } catch (e: Exception) {
            Log.e(TAG, "ExoPlayer初始化失败", e)
            currentState = UnifiedVideoPlayer.PlayerState.ERROR
            eventListener?.onError("初始化失败: ${e.message}")
            false
        }
    }
    
    override fun setVideoSurface(holder: SurfaceHolder?) {
        player?.setVideoSurfaceHolder(holder)
        Log.d(TAG, "设置视频Surface")
    }

    override fun setPlayerView(playerView: PlayerView?) {
        playerView?.player = player
        Log.d(TAG, "设置ExoPlayer到PlayerView")
    }
    
    override fun setEventListener(listener: UnifiedVideoPlayer.EventListener?) {
        this.eventListener = listener
    }
    
    override fun prepare(url: String, options: Map<String, Any>?) {
        if (player == null || context == null) {
            Log.e(TAG, "播放器未初始化")
            eventListener?.onError("播放器未初始化")
            return
        }

        currentUrl = url
        currentState = UnifiedVideoPlayer.PlayerState.PREPARING
        eventListener?.onStateChanged(currentState)

        try {
            Log.d(TAG, "准备播放: $url")
            val mediaItem = MediaItem.fromUri(url)
            player?.setMediaItem(mediaItem)
            player?.prepare()

            // 如果是RTP流，应用优化
            if (PlayerFactory.isRTPStream(url)) {
                RTPPlayerHelper.applyRTPOptimizations(player!!)
                Log.d(TAG, "应用RTP流优化配置")
            }

            isPrepared = true
            currentState = UnifiedVideoPlayer.PlayerState.READY
            eventListener?.onStateChanged(currentState)
            Log.d(TAG, "播放器准备就绪")

        } catch (e: Exception) {
            Log.e(TAG, "准备播放失败", e)
            currentState = UnifiedVideoPlayer.PlayerState.ERROR
            eventListener?.onStateChanged(currentState)
            eventListener?.onError("准备失败: ${e.message}")
        }
    }
    
    override fun start() {
        if (!isPrepared) {
            Log.e(TAG, "播放器未准备就绪")
            eventListener?.onError("播放器未准备就绪")
            return
        }

        try {
            player?.play()
            currentState = UnifiedVideoPlayer.PlayerState.PLAYING
            eventListener?.onStateChanged(currentState)
            Log.d(TAG, "开始播放")
        } catch (e: Exception) {
            Log.e(TAG, "开始播放失败", e)
            currentState = UnifiedVideoPlayer.PlayerState.ERROR
            eventListener?.onStateChanged(currentState)
            eventListener?.onError("开始播放失败: ${e.message}")
        }
    }
    
    override fun pause() {
        try {
            player?.pause()
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
            player?.stop()
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
            player?.release()
            player = null
            context = null
            currentState = UnifiedVideoPlayer.PlayerState.IDLE
            isPrepared = false
            Log.d(TAG, "ExoPlayer资源已释放")
        } catch (e: Exception) {
            Log.e(TAG, "释放资源时出错", e)
        }
    }
    
    override fun isPlaying(): Boolean {
        return player?.isPlaying ?: false
    }
    
    override fun getCurrentPosition(): Long {
        return player?.currentPosition ?: 0
    }
    
    override fun getDuration(): Long {
        return player?.duration ?: 0
    }
    
    override fun seekTo(position: Long) {
        try {
            player?.seekTo(position)
            Log.d(TAG, "跳转到位置: $position")
        } catch (e: Exception) {
            Log.e(TAG, "跳转失败", e)
        }
    }
    
    override fun getPlayerType(): UnifiedVideoPlayer.PlayerType {
        return UnifiedVideoPlayer.PlayerType.EXO_PLAYER
    }
    
    override fun getPlayerState(): UnifiedVideoPlayer.PlayerState {
        return currentState
    }
    
    override fun setPlaybackSpeed(speed: Float) {
        try {
            player?.setPlaybackSpeed(speed)
            Log.d(TAG, "设置播放速度: $speed")
        } catch (e: Exception) {
            Log.e(TAG, "设置播放速度失败", e)
        }
    }
    
    override fun setVolume(volume: Float) {
        try {
            player?.volume = volume
            Log.d(TAG, "设置音量: $volume")
        } catch (e: Exception) {
            Log.e(TAG, "设置音量失败", e)
        }
    }
    
    override fun supportsProtocol(url: String): Boolean {
        return PlayerFactory.detectPlayerType(url) == UnifiedVideoPlayer.PlayerType.EXO_PLAYER
    }

    override fun reconfigure(options: Map<String, Any>) {
        Log.d(TAG, "重新配置播放器: $options")
        val url = currentUrl
        if (url != null && player != null) {
            stop()
            prepare(url, options)
            start()
        }
    }
    
    /**
     * 设置ExoPlayer配置
     */
    fun setExoPlayerConfig(config: ExoPlayer.Builder) {
        this.exoPlayerConfig = config
    }
    
    /**
     * 处理播放器状态变化
     */
    private fun handlePlayerState(playbackState: Int) {
        when (playbackState) {
            Player.STATE_IDLE -> {
                currentState = UnifiedVideoPlayer.PlayerState.IDLE
                eventListener?.onStateChanged(currentState)
            }
            Player.STATE_BUFFERING -> {
                // 获取缓冲百分比
                val bufferedPosition = player?.bufferedPosition ?: 0
                val duration = player?.duration ?: 1
                val percent = if (duration > 0) {
                    (bufferedPosition * 100 / duration).toInt()
                } else {
                    0
                }
                eventListener?.onBufferingUpdate(percent)
            }
            Player.STATE_READY -> {
                currentState = UnifiedVideoPlayer.PlayerState.READY
                eventListener?.onStateChanged(currentState)
            }
            Player.STATE_ENDED -> {
                currentState = UnifiedVideoPlayer.PlayerState.ENDED
                eventListener?.onStateChanged(currentState)
                eventListener?.onPlaybackCompleted()
            }
        }

        // 发送进度更新
        val currentPos = player?.currentPosition ?: 0
        val duration = player?.duration ?: 0
        eventListener?.onProgressUpdate(currentPos, duration)
    }
}