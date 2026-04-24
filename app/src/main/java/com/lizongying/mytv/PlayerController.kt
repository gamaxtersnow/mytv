package com.lizongying.mytv

import android.content.Context
import android.view.SurfaceHolder
import android.util.Log
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import com.lizongying.mytv.UnifiedVideoPlayer

/**
 * 播放器控制器
 * 负责播放器生命周期管理、播放控制、状态管理
 * 从PlayerFragment中拆分出来，符合单一职责原则
 */
class PlayerController(private val context: Context) {
    private val appContext = context.applicationContext
    private var unifiedPlayer: UnifiedVideoPlayer? = null
    private var currentVideoUrl: String? = null
    private var eventListener: PlayerEventListener? = null
    private var isSurfaceHolder: SurfaceHolder? = null
    private var playerView: PlayerView? = null
    private var playerTypeChangeListener: ((UnifiedVideoPlayer.PlayerType) -> Unit)? = null

    // 协程作用域，用于执行网络检查等异步操作
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())
    private val networkDetector = NetworkDetector

    // 播放器状态监听器
    interface PlayerEventListener {
        fun onStateChanged(state: UnifiedVideoPlayer.PlayerState)
        fun onError(error: String)
        fun onBufferingUpdate(percent: Int)
        fun onProgressUpdate(position: Long, duration: Long)
        fun onPlaybackCompleted()
        fun onPlaying()
    }

    /**
     * 初始化播放器
     */
    fun initializePlayer(url: String, listener: PlayerEventListener) {
        eventListener = listener

        // 验证URL是否被支持
        if (!PlayerFactory.isUrlSupported(url)) {
            Log.e(TAG, "不支持的URL协议: $url")
            listener.onError("不支持的播放协议")
            return
        }

        // 释放之前的播放器
        releasePlayer()

        // 对于组播流，先获取组播锁，然后立刻开始播放，网络检测后台异步做
        if (PlayerFactory.isRTPStream(url)) {
            coroutineScope.launch {
                // 获取组播锁（必须做，阻塞到拿到为止）
                NetworkDetector.acquireMulticastLock(appContext)

                // 先开始播放，不要等网络检测！
                createAndStartPlayer(url, listener)

                // 网络检测放后台做，不阻塞播放流程
                launch(Dispatchers.IO) {
                    try {
                        val isNetworkSuitable = networkDetector.isSuitableForMulticast(appContext)
                        if (!isNetworkSuitable) {
                            Log.w(TAG, "网络不适合播放组播流，可能出现卡顿")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "网络检测失败，不影响播放: ${e.message}")
                    }
                }
            }
        } else {
            // 非组播流直接初始化
            createAndStartPlayer(url, listener)
        }
    }

    /**
     * 创建并启动播放器
     */
    private fun createAndStartPlayer(url: String, listener: PlayerEventListener) {
        // 创建新的播放器，使用Application context避免内存泄漏
        val player = PlayerFactory.createPlayer(appContext, url)
        val playerType = player.getPlayerType()

        // 通知播放器类型变化
        playerTypeChangeListener?.invoke(playerType)

        player.setEventListener(object : UnifiedVideoPlayer.EventListener {
            override fun onStateChanged(state: UnifiedVideoPlayer.PlayerState) {
                listener.onStateChanged(state)
                if (state == UnifiedVideoPlayer.PlayerState.PLAYING) {
                    listener.onPlaying()
                } else if (state == UnifiedVideoPlayer.PlayerState.ERROR) {
                    listener.onError("播放错误")
                }
            }

            override fun onError(error: String) {
                listener.onError(error)
            }

            override fun onBufferingUpdate(percent: Int) {
                listener.onBufferingUpdate(percent)
            }

            override fun onProgressUpdate(position: Long, duration: Long) {
                listener.onProgressUpdate(position, duration)
            }

            override fun onPlaybackCompleted() {
                listener.onPlaybackCompleted()
            }
        })

        // 【重要】必须在prepare之前设置视频输出，特别是对于VLC播放器
        if (playerType == UnifiedVideoPlayer.PlayerType.EXO_PLAYER && playerView != null) {
            // ExoPlayer使用PlayerView
            player.setPlayerView(playerView)
            Log.d(TAG, "为ExoPlayer设置PlayerView")
        } else {
            val surfaceHolder = isSurfaceHolder
            if (surfaceHolder != null && surfaceHolder.surface != null && surfaceHolder.surface.isValid) {
                // VLC或其他播放器使用SurfaceHolder，确保Surface有效
                player.setVideoSurface(surfaceHolder)
                Log.d(TAG, "为VLC播放器设置有效SurfaceHolder，尺寸: ${surfaceHolder.surfaceFrame.width()}x${surfaceHolder.surfaceFrame.height()}")
            } else {
                Log.w(TAG, "没有可用的视频输出Surface，播放可能失败")
            }
        }

        // 准备播放
        val options = getPlayerOptions(url)
        Log.d(TAG, "使用播放选项: $options")
        player.prepare(url, options)

        // 开始播放
        player.start()

        unifiedPlayer = player
        currentVideoUrl = url

        Log.d(TAG, "使用播放器: ${player.getPlayerType()}")
    }

    /**
     * 获取播放器选项
     */
    private fun getPlayerOptions(url: String): Map<String, Any> {
        val options = mutableMapOf<String, Any>()

        // 根据协议类型添加特定选项，只使用VLC支持的选项
        when {
            PlayerFactory.isRTPStream(url) -> {
                options["network-caching"] = 3000
                options["live-caching"] = 3000
            }
            PlayerFactory.isRTSPStream(url) -> {
                options["network-caching"] = 3000
                options["live-caching"] = 3000
            }
            else -> {
                options["network-caching"] = 1000
            }
        }

        return options
    }

    /**
     * 设置视频输出Surface
     */
    fun setSurfaceHolder(surfaceHolder: SurfaceHolder) {
        this.isSurfaceHolder = surfaceHolder
        unifiedPlayer?.setVideoSurface(surfaceHolder)
    }

    /**
     * 设置视频输出PlayerView
     */
    fun setPlayerView(playerView: PlayerView) {
        this.playerView = playerView
        unifiedPlayer?.setPlayerView(playerView)
    }

    /**
     * 设置播放器类型变化监听器
     */
    fun setPlayerTypeChangeListener(listener: (UnifiedVideoPlayer.PlayerType) -> Unit) {
        this.playerTypeChangeListener = listener
    }

    /**
     * 开始播放
     */
    fun play() {
        unifiedPlayer?.start()
    }

    /**
     * 暂停播放
     */
    fun pause() {
        unifiedPlayer?.pause()
    }

    /**
     * 恢复播放
     */
    fun resume() {
        if (unifiedPlayer?.getPlayerState() == UnifiedVideoPlayer.PlayerState.PAUSED) {
            unifiedPlayer?.resume()
        } else if (currentVideoUrl != null && unifiedPlayer == null) {
            currentVideoUrl?.let { url ->
                eventListener?.let { listener ->
                    initializePlayer(url, listener)
                }
            }
        }
    }

    /**
     * 停止播放
     */
    fun stop() {
        unifiedPlayer?.stop()
    }

    /**
     * 重新播放当前视频
     */
    fun replay() {
        currentVideoUrl?.let { url ->
            eventListener?.let { listener ->
                initializePlayer(url, listener)
            }
        }
    }

    /**
     * 切换播放器类型
     */
    fun switchPlayer(forceType: UnifiedVideoPlayer.PlayerType) {
        currentVideoUrl?.let { url ->
            Log.i(TAG, "切换播放器到: $forceType")
            releasePlayer()

            val player = when (forceType) {
                UnifiedVideoPlayer.PlayerType.VLC_PLAYER -> {
                    val vlcPlayer = VLCPlayerWrapper()
                    vlcPlayer.initialize(appContext)
                    vlcPlayer
                }
                UnifiedVideoPlayer.PlayerType.EXO_PLAYER -> {
                    val exoPlayer = ExoPlayerWrapper()
                    exoPlayer.initialize(appContext)
                    exoPlayer
                }
                UnifiedVideoPlayer.PlayerType.GSY_PLAYER -> {
                    val ijkPlayer = GsyPlayerWrapper()
                    ijkPlayer.initialize(appContext)
                    ijkPlayer
                }
            }

            eventListener?.let { listener ->
                player.setEventListener(object : UnifiedVideoPlayer.EventListener {
                    override fun onStateChanged(state: UnifiedVideoPlayer.PlayerState) {
                        listener.onStateChanged(state)
                        if (state == UnifiedVideoPlayer.PlayerState.PLAYING) {
                            listener.onPlaying()
                        }
                    }

                    override fun onError(error: String) {
                        listener.onError(error)
                    }

                    override fun onBufferingUpdate(percent: Int) {
                        listener.onBufferingUpdate(percent)
                    }

                    override fun onProgressUpdate(position: Long, duration: Long) {
                        listener.onProgressUpdate(position, duration)
                    }

                    override fun onPlaybackCompleted() {
                        listener.onPlaybackCompleted()
                    }
                })
            }

            isSurfaceHolder?.let {
                player.setVideoSurface(it)
            }

            val options = getPlayerOptions(url)
            player.prepare(url, options)
            player.start()

            unifiedPlayer = player
        }
    }

    /**
     * 应用播放器设置
     */
    fun applySettings(cacheMs: Int, performanceMode: PerformanceConfig.PerformanceMode) {
        currentVideoUrl?.let { url ->
            val options = getPlayerOptions(url).toMutableMap()
            options["network-caching"] = cacheMs

            // 根据性能模式调整参数
            when (performanceMode) {
                PerformanceConfig.PerformanceMode.LOW_LATENCY -> {
                    options["network-caching"] = 500
                    options["live-caching"] = 500
                }
                PerformanceConfig.PerformanceMode.HIGH_QUALITY -> {
                    options["network-caching"] = 3000
                    options["live-caching"] = 3000
                }
                PerformanceConfig.PerformanceMode.POWER_SAVING -> {
                    options["network-caching"] = 2000
                }
                else -> {
                    // 使用默认值
                }
            }

            // 重新配置播放器
            unifiedPlayer?.reconfigure(options)
        }
    }

    /**
     * 获取播放器信息
     */
    fun getPlayerInfo(): String {
        return unifiedPlayer?.let { player ->
            "类型: ${player.getPlayerType()}, 状态: ${player.getPlayerState()}, " +
                    "位置: ${player.getCurrentPosition()}ms, 时长: ${player.getDuration()}ms"
        } ?: "无活动播放器"
    }

    /**
     * 获取当前播放器类型
     */
    fun getPlayerType(): UnifiedVideoPlayer.PlayerType? {
        return unifiedPlayer?.getPlayerType()
    }

    /**
     * 获取当前播放器状态
     */
    fun getPlayerState(): UnifiedVideoPlayer.PlayerState {
        return unifiedPlayer?.getPlayerState() ?: UnifiedVideoPlayer.PlayerState.IDLE
    }

    /**
     * 获取当前播放URL
     */
    fun getCurrentUrl(): String? {
        return currentVideoUrl
    }

    /**
     * 释放播放器资源
     */
    fun releasePlayer() {
        // 取消所有正在执行的协程
        coroutineScope.coroutineContext.cancelChildren()
        unifiedPlayer?.release()
        unifiedPlayer = null
        currentVideoUrl = null
        // 释放组播锁
        NetworkDetector.releaseMulticastLock()
    }

    companion object {
        private const val TAG = "PlayerController"
    }
}
