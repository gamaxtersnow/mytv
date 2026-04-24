package com.lizongying.mytv

import android.content.Context
import android.util.Log
import android.view.SurfaceHolder
import androidx.media3.ui.PlayerView
import tv.danmaku.ijk.media.player.IMediaPlayer
import tv.danmaku.ijk.media.player.IjkMediaPlayer

/**
 * GSYVideoPlayer包装类
 * 基于ijkplayer持续维护版本，优化UDP/RTP组播播放
 * 实现UnifiedVideoPlayer接口，与现有架构兼容
 *
 * 参考GSYVideoPlayer文档优化配置：
 * https://github.com/CarGuo/GSYVideoPlayer
 */
class GsyPlayerWrapper : UnifiedVideoPlayer {

    companion object {
        private const val TAG = "GsyPlayerWrapper"
    }

    private var context: Context? = null
    private var player: IjkMediaPlayer? = null
    private var eventListener: UnifiedVideoPlayer.EventListener? = null
    private var currentState: UnifiedVideoPlayer.PlayerState = UnifiedVideoPlayer.PlayerState.IDLE
    private var currentUrl: String? = null
    private var isPrepared = false
    private var pendingStart = false

    override fun initialize(context: Context): Boolean {
        this.context = context
        return try {
            player = createPlayer()
            currentState = UnifiedVideoPlayer.PlayerState.INITIALIZED
            Log.d(TAG, "GSY player 初始化成功")
            true
        } catch (e: Exception) {
            Log.e(TAG, "GSY player 初始化失败", e)
            currentState = UnifiedVideoPlayer.PlayerState.ERROR
            eventListener?.onError("初始化失败: ${e.message}")
            false
        }
    }

    /**
     * 创建ijkplayer实例 —— 应用GSYVideoPlayer官方直播/RTSP推荐配置
     * 参考: GSYVideoPlayer/doc/QUESTION.md 第17点
     */
    private fun createPlayer(): IjkMediaPlayer {
        return IjkMediaPlayer().apply {
            // 开启详细日志以便诊断
            IjkMediaPlayer.native_setLogLevel(IjkMediaPlayer.IJK_LOG_VERBOSE)

            // 禁用硬解（硬解在某些设备上会导致音频问题）
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 0L)

            // 允许udp协议（rtp://会转为udp://@）
            setOption(
                IjkMediaPlayer.OPT_CATEGORY_FORMAT,
                "protocol_whitelist",
                "file,http,https,udp,rtp,tcp,tls,rtmp,crypto"
            )

            // ========== GSYVideoPlayer官方直播/RTSP推荐配置 ==========
            // 不额外优化
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "fast", 1L)
            // 分析码流大小
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "probesize", 10240L)
            // 刷新包
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "flush_packets", 1L)
            // 关闭播放器缓冲（必须关闭，否则播放一段时间后会卡住）
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "packet-buffering", 0L)
            // 丢帧，太卡时尝试丢帧保持同步
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 1L)
            // 自动开始播放
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 1L)
            // 跳过循环过滤器（默认值48）
            setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_loop_filter", 48L)
            // 最大缓存数（0=不限制）
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max-buffer-size", 0L)
            // 默认最小帧数
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "min-frames", 2L)
            // 最大缓存时长
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max_cached_duration", 30L)
            // 是否限制输入缓存数（1=不限制，适合实时流）
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "infbuf", 1L)
            // 不缓冲
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "fflags", "nobuffer")
            // TCP传输数据（UDP组播不需要，但保留以防万一）
            // setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "rtsp_transport", "tcp")
            // 分析码流时长（默认1024*1000）
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "analyzedmaxduration", 100L)

            // 监听器
            setupListeners(this)
        }
    }

    private fun setupListeners(player: IjkMediaPlayer) {
        player.setOnPreparedListener {
            Log.d(TAG, "GSY player 准备完成")
            isPrepared = true
            currentState = UnifiedVideoPlayer.PlayerState.READY
            eventListener?.onStateChanged(currentState)
            if (pendingStart) {
                pendingStart = false
                doStart()
            }
        }

        player.setOnInfoListener { _, what, extra ->
            when (what) {
                IMediaPlayer.MEDIA_INFO_BUFFERING_START -> {
                    Log.d(TAG, "开始缓冲")
                    currentState = UnifiedVideoPlayer.PlayerState.BUFFERING
                    eventListener?.onStateChanged(currentState)
                }
                IMediaPlayer.MEDIA_INFO_BUFFERING_END -> {
                    Log.d(TAG, "缓冲结束")
                    currentState = UnifiedVideoPlayer.PlayerState.PLAYING
                    eventListener?.onStateChanged(currentState)
                }
                IMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START -> {
                    Log.d(TAG, "开始渲染视频")
                    currentState = UnifiedVideoPlayer.PlayerState.PLAYING
                    eventListener?.onStateChanged(currentState)
                }
                IMediaPlayer.MEDIA_INFO_AUDIO_RENDERING_START -> {
                    Log.d(TAG, "【音频】开始渲染音频！")
                }
                else -> {
                    Log.d(TAG, "Info事件: what=$what, extra=$extra")
                }
            }
            true
        }

        player.setOnErrorListener { _, what, extra ->
            Log.e(TAG, "GSY player 错误: what=$what, extra=$extra")
            isPrepared = false
            pendingStart = false
            currentState = UnifiedVideoPlayer.PlayerState.ERROR
            eventListener?.onStateChanged(currentState)
            eventListener?.onError("GSY错误: $what/$extra")
            true
        }

        player.setOnCompletionListener {
            Log.d(TAG, "播放完成")
            currentState = UnifiedVideoPlayer.PlayerState.ENDED
            eventListener?.onStateChanged(currentState)
            eventListener?.onPlaybackCompleted()
        }

        player.setOnBufferingUpdateListener { _, percent ->
            eventListener?.onBufferingUpdate(percent)
        }
    }

    override fun setVideoSurface(holder: SurfaceHolder?) {
        try {
            player?.setDisplay(holder)
            Log.d(TAG, "设置视频Surface")
        } catch (e: Exception) {
            Log.e(TAG, "设置Surface失败", e)
        }
    }

    override fun setPlayerView(playerView: PlayerView?) {
        Log.w(TAG, "GSY player 不支持PlayerView，请使用SurfaceView")
    }

    override fun setEventListener(listener: UnifiedVideoPlayer.EventListener?) {
        this.eventListener = listener
    }

    override fun prepare(url: String, options: Map<String, Any>?) {
        if (player == null) {
            Log.e(TAG, "播放器未初始化")
            eventListener?.onError("播放器未初始化")
            return
        }

        val actualUrl = convertRtpToUdp(url)
        currentUrl = actualUrl
        isPrepared = false
        pendingStart = false
        currentState = UnifiedVideoPlayer.PlayerState.PREPARING
        eventListener?.onStateChanged(currentState)

        try {
            Log.d(TAG, "准备播放: 原始=$url, 实际=$actualUrl")
            player?.setDataSource(actualUrl)
            player?.prepareAsync()
            Log.d(TAG, "开始异步准备")
        } catch (e: Exception) {
            Log.e(TAG, "准备播放失败", e)
            currentState = UnifiedVideoPlayer.PlayerState.ERROR
            eventListener?.onStateChanged(currentState)
            eventListener?.onError("准备失败: ${e.message}")
        }
    }

    /**
     * 将rtp:// URL转换为udp://@格式
     */
    private fun convertRtpToUdp(url: String): String {
        return when {
            url.startsWith("rtp://", ignoreCase = true) -> {
                val address = url.removePrefix("rtp://")
                "udp://@$address"
            }
            else -> url
        }
    }

    override fun start() {
        if (!isPrepared) {
            Log.d(TAG, "播放器尚未准备就绪，标记pending start")
            pendingStart = true
            return
        }
        doStart()
    }

    private fun doStart() {
        if (!isPrepared || player == null) {
            Log.w(TAG, "doStart: 状态不对，isPrepared=$isPrepared, player=$player")
            return
        }
        try {
            player?.start()
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
            Log.e(TAG, "暂停失败", e)
        }
    }

    override fun stop() {
        try {
            player?.stop()
            currentState = UnifiedVideoPlayer.PlayerState.STOPPED
            eventListener?.onStateChanged(currentState)
            isPrepared = false
            pendingStart = false
            Log.d(TAG, "播放停止")
        } catch (e: Exception) {
            Log.e(TAG, "停止失败", e)
        }
    }

    override fun resume() {
        if (currentState == UnifiedVideoPlayer.PlayerState.PAUSED) {
            start()
        }
    }

    override fun release() {
        try {
            player?.stop()
            player?.release()
            player = null
            context = null
            currentState = UnifiedVideoPlayer.PlayerState.IDLE
            isPrepared = false
            pendingStart = false
            Log.d(TAG, "GSY player 资源已释放")
        } catch (e: Exception) {
            Log.e(TAG, "释放资源失败", e)
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
        return UnifiedVideoPlayer.PlayerType.GSY_PLAYER
    }

    override fun getPlayerState(): UnifiedVideoPlayer.PlayerState {
        return currentState
    }

    override fun setPlaybackSpeed(speed: Float) {
        Log.w(TAG, "GSY player 播放速度调整可能不受支持")
    }

    override fun setVolume(volume: Float) {
        try {
            player?.setVolume(volume, volume)
            Log.d(TAG, "设置音量: $volume")
        } catch (e: Exception) {
            Log.e(TAG, "设置音量失败", e)
        }
    }

    override fun supportsProtocol(url: String): Boolean {
        return PlayerFactory.isRTPStream(url) ||
               PlayerFactory.isRTSPStream(url) ||
               url.startsWith("http://", ignoreCase = true) ||
               url.startsWith("https://", ignoreCase = true)
    }

    override fun reconfigure(options: Map<String, Any>) {
        Log.d(TAG, "重新配置: $options")
        val url = currentUrl
        if (url != null) {
            stop()
            prepare(url, options)
            start()
        }
    }
}
