package com.lizongying.mytv

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.SurfaceHolder
import android.view.WindowManager
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

            // 启用硬解（4K必须硬解，软解会压垮CPU+GPU导致fence超时卡顿）
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 1L)
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-hevc", 1L)
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-avc", 1L)
            // 处理分辨率变化，避免花屏/卡顿
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-handle-resolution-change", 1L)
            // OpenGL ES2 直接渲染（绕过SurfaceFlinger合成，减少4K GPU开销）
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "overlay-format", "fcc-_es2")

            // 允许udp协议（rtp://会转为udp://@）
            setOption(
                IjkMediaPlayer.OPT_CATEGORY_FORMAT,
                "protocol_whitelist",
                "file,http,https,udp,rtp,tcp,tls,rtmp,crypto"
            )

            // ========== 4K/高码率优化配置 ==========
            // 分析码流大小（4K HEVC需要更大探测值以正确识别流参数）
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "probesize", 20000000L)
            // UDP接收缓冲区（增大减少组播丢包，4K流需要更大）
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "buffer_size", 8388608L)
            // UDP FIFO缓冲区（额外缓冲层应对网络抖动）
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "fifo_size", 8388608L)
            // MPEG-TS 流增强容错（resync=丢失同步后重新同步，discardcorrupt=丢弃损坏包）
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "mpegts_flags", "+resync")
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "fflags", "+discardcorrupt")
            // 关闭 flush_packets（避免NAL单元被提前分割，导致海思解码器收到不完整的slice）
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "flush_packets", 0L)
            // ========== 直播/4K 核心优化 ==========
            // 开启播放器缓冲（让播放器管理帧释放节奏，均匀输出到屏幕）
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "packet-buffering", 1L)
            // 限制输入缓存（防止缓冲过大导致延迟累积）
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "infbuf", 0L)
            // 轻微丢帧（只在严重延迟时丢帧，平时保持流畅）
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 1L)
            // 自动开始播放
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 1L)
            // 跳过循环过滤器（48=跳过非参考帧，最大化减轻4K解码压力）
            setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_loop_filter", 48L)
            // 最大缓存数（8MB，更大缓冲平滑网络抖动）
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max-buffer-size", 8388608L)
            // 最小帧数（16帧启动缓冲，积累更多帧后均匀输出）
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "min-frames", 16L)
            // 视频帧队列大小（增大到12，让渲染更稳定）
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "video-pictq-size", 12L)
            // 音频帧队列大小
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "audio-pictq-size", 4L)
            // 使用OpenSL ES音频输出（更低的音频延迟，更稳定的A/V同步）
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "opensles", 1L)
            // 让音频时钟主导同步（通常比视频同步更稳定，减少画面抖动）
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "sync", "audio")
            // ========== 帧率适配（类似Kodi VideoSync）==========
            // 启用帧率适配：将帧显示时长对齐到显示刷新周期的整数倍
            // 减少25fps/50fps视频在60Hz屏幕上的judder（画面抖动）
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "fps-adaptation", 1L)
            // 自动检测并设置显示刷新率
            val refreshRate = getDisplayRefreshRate()
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "refresh-rate", refreshRate.toString())
            Log.d(TAG, "自动检测到显示刷新率: ${refreshRate}Hz")
            // 最大缓存时长（毫秒）——让播放器自由管理，不限制
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max_cached_duration", 3000L)
            // 禁用精确seek
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "accurate-seek", 0L)
            // 分析码流时长（4K HEVC需要更长时间探测）
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "analyzedmaxduration", 5000000L)
            // 最大分析时长（微秒）
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "analyzeduration", 5000000L)

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

    /**
     * 自动检测设备显示刷新率（Hz）
     * 通过 DisplayManager 获取当前显示模式刷新率，适配不同设备
     */
    private fun getDisplayRefreshRate(): Float {
        val ctx = context ?: return 60.0f
        return try {
            val displayManager = ctx.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            val display = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)

            val refreshRate = display?.refreshRate ?: 60.0f
            // 过滤掉不合理的值，保底60Hz
            if (refreshRate <= 0 || refreshRate > 240) 60.0f else refreshRate
        } catch (e: Exception) {
            Log.w(TAG, "获取显示刷新率失败，使用默认值60Hz", e)
            60.0f
        }
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
