package com.lizongying.mytv

import android.util.Log

/**
 * 性能优化配置类
 * 提供VLC播放器的性能优化参数配置
 */
object PerformanceConfig {
    private const val TAG = "PerformanceConfig"

    /**
     * 性能监控配置
     */
    val DEFAULT_MONITORING_ENABLED = true // 临时开启性能监控
    val DEFAULT_SAMPLING_INTERVAL_MS = 1000L // 默认采样间隔1秒
    val DEFAULT_HISTORY_SIZE = 100 // 默认历史记录大小
    
    /**
     * 性能模式枚举
     */
    enum class PerformanceMode {
        /** 平衡模式 - 默认设置，适合大多数场景 */
        BALANCED,
        
        /** 低延迟模式 - 适合实时流媒体，减少缓冲延迟 */
        LOW_LATENCY,
        
        /** 高质量模式 - 适合高质量视频流，增加缓冲减少卡顿 */
        HIGH_QUALITY,
        
        /** 网络优化模式 - 适合不稳定网络环境 */
        NETWORK_OPTIMIZED,
        
        /** 省电模式 - 降低CPU/GPU使用率 */
        POWER_SAVING
    }
    
    /**
     * 获取VLC安全加固选项，防止恶意代码执行
     */
    fun getSecurityHardeningOptions(): List<String> {
        return listOf(
            "--lua=none",                   // 禁用Lua脚本支持，防止恶意脚本执行
            "--no-plugin-autoload",         // 禁用不必要的插件自动加载
            "--demux-filter=rtp,rtsp,udp,ts,mp4", // 仅允许需要的解复用器
            "--no-access-file",             // 禁用本地文件访问
            "--no-access-smb",              // 禁用SMB访问
            "--no-access-ftp",              // 禁用FTP访问
            "--no-sout",                    // 禁用流输出功能
            "--no-sout-all",                // 禁用所有输出模块
            "--disable-sout"                // 完全禁用输出功能
        )
    }

    /**
     * 获取指定性能模式的VLC选项
     */
    fun getVLCOptionsForMode(mode: PerformanceMode): List<String> {
        val options = mutableListOf<String>()
        options.addAll(getSecurityHardeningOptions()) // 首先添加安全加固选项

        // 添加组播和RTP全局优化选项
        options.addAll(listOf(
            "--rtp-max-rate=20000000",         // 全局RTP最大码率
            "--udp-timeout=30000",             // 全局UDP超时
            "--network-caching=2000",          // 全局网络缓存
            "--live-caching=2000"              // 全局直播缓存
        ))

        options.addAll(when (mode) {
            PerformanceMode.BALANCED -> getBalancedOptions()
            PerformanceMode.LOW_LATENCY -> getLowLatencyOptions()
            PerformanceMode.HIGH_QUALITY -> getHighQualityOptions()
            PerformanceMode.NETWORK_OPTIMIZED -> getNetworkOptimizedOptions()
            PerformanceMode.POWER_SAVING -> getPowerSavingOptions()
        })
        return options
    }
    
    /**
     * 获取RTP/RTSP特定优化选项
     * 参考VLC官方文档和组播流最佳实践
     */
    fun getRTPOptimizedOptions(): List<String> {
        return listOf(
            "--demux=ts",                  // 强制使用MPEG-TS解复用器，绝大多数RTP组播流都是TS格式
            "--rtp-max-rate=20000000",     // RTP最大比特率20Mbps，支持更高码率流
            "--rtp-timeout=60000",         // RTP超时60秒
            "--rtp-buffer-size=4194304",   // RTP缓冲区大小4MB，进一步增大缓存减少卡顿
            "--rtp-max-delay=1000",        // RTP最大延迟1000ms，提高兼容性
            "--rtp-session-timeout=120",   // RTP会话超时120秒
            "--udp-caching=2000",          // UDP缓存2000ms，增加缓存减少花屏
            "--udp-timeout=30000",         // UDP超时30秒
            "--no-rtp-auto-detect",        // 禁用RTP自动检测，避免误判
            "--ts-dump=-1",                // 禁用TS转储，提高性能
            "--ts-trust-pcr",              // 信任PCR时间戳，提高音视频同步
            "--no-ts-csa",                 // 禁用CSA解密，减少不必要的处理
            "--rtp-dont-force-ts",         // 不强制TS，但我们前面已经指定了demux=ts
            "--network-synchronisation",   // 启用网络时间同步
            "--clock-jitter=1000",         // 时钟抖动缓冲1000ms
            "--rtsp-tcp",                  // 对于RTSP使用TCP，这里RTP也可以加上增强兼容性
            "--no-audio-time-stretch",     // 禁用音频时间拉伸，减少延迟
            "--avcodec-hw=any",            // 尝试所有可用的硬件解码器
            "--avcodec-skip-frame=0",      // 不跳过视频帧，保证质量
            "--avcodec-skip-idct=0",       // 不跳过IDCT计算
            "--drop-late-frames=1",        // 丢弃延迟超过1秒的帧
            "--sout-mux-caching=0"         // 禁用复用缓存，减少延迟
        )
    }
    
    /**
     * 获取网络缓存优化选项
     * @param cacheMs 缓存时间（毫秒）
     */
    fun getNetworkCacheOptions(cacheMs: Int = 1500): List<String> {
        return listOf(
            "--network-caching=$cacheMs",
            "--live-caching=$cacheMs",
            "--file-caching=$cacheMs",
            "--disc-caching=$cacheMs"
        )
    }
    
    /**
     * 获取解码器优化选项
     */
    fun getDecoderOptimizedOptions(): List<String> {
        return listOf(
            "--avcodec-hw=any",            // 启用硬件解码
            "--avcodec-threads=0",         // 自动选择解码线程数
            "--avcodec-skiploopfilter=1",  // 跳过环路滤波（提高性能）
            "--avcodec-skip-frame=0",      // 不跳过帧
            "--avcodec-fast",              // 快速解码
            "--no-skip-frames",            // 不跳过帧（保持质量）
            "--drop-late-frames",          // 丢弃延迟帧
            "--skip-frames"                // 跳帧以保持同步
        )
    }
    
    /**
     * 获取音频优化选项
     */
    fun getAudioOptimizedOptions(): List<String> {
        return listOf(
            "--aout=opensles",             // 使用OpenSL ES音频输出
            "--audio-time-stretch",        // 音频时间拉伸
            "--audio-resampler=src",       // 高质量音频重采样
            "--audio-channels=2",          // 立体声
            "--audio-samplerate=48000"     // 48kHz采样率
        )
    }
    
    /**
     * 获取视频渲染优化选项
     */
    fun getVideoRenderOptimizedOptions(): List<String> {
        return listOf(
            "--vout=android-display",      // Android显示输出
            "--android-display-chroma=RV32", // 使用RGB32格式
            "--android-display-width=1920", // 显示宽度
            "--android-display-height=1080", // 显示高度
            "--no-video-title-show",       // 不显示视频标题
            "--no-osd",                    // 不显示OSD
            "--no-spu"                     // 不显示字幕
        )
    }
    
    /**
     * 获取同步优化选项
     */
    fun getSyncOptimizedOptions(): List<String> {
        return listOf(
            "--clock-jitter=0",            // 减少时钟抖动
            "--clock-synchro=0",           // 禁用时钟同步
            "--no-audio-time-stretch",     // 禁用音频时间拉伸（减少延迟）
            "--audio-desync=0"             // 音频去同步为0
        )
    }
    
    /**
     * 平衡模式选项
     */
    private fun getBalancedOptions(): List<String> {
        val options = mutableListOf<String>()
        options.addAll(getNetworkCacheOptions(1500))
        options.addAll(getDecoderOptimizedOptions())
        options.addAll(getAudioOptimizedOptions())
        options.addAll(getVideoRenderOptimizedOptions())
        options.addAll(getSyncOptimizedOptions())
        options.addAll(listOf(
            "--rtsp-tcp",                  // 强制使用TCP for RTSP
            "--rtsp-frame-buffer-size=500000",
            "--cr-average=10000",          // 平均码率控制
            "--sout-mux-caching=100"       // 复用缓存
        ))
        return options
    }
    
    /**
     * 低延迟模式选项
     */
    private fun getLowLatencyOptions(): List<String> {
        val options = mutableListOf<String>()
        options.addAll(getNetworkCacheOptions(300))  // 低缓存
        options.addAll(getDecoderOptimizedOptions())
        options.addAll(getAudioOptimizedOptions())
        options.addAll(getVideoRenderOptimizedOptions())
        options.addAll(listOf(
            "--rtsp-tcp",
            "--network-caching=300",
            "--live-caching=300",
            "--file-caching=300",
            "--clock-jitter=0",
            "--clock-synchro=0",
            "--no-audio-time-stretch",
            "--audio-desync=0",
            "--cr-average=5000",           // 更快的码率适应
            "--sout-mux-caching=50",       // 更低的复用缓存
            "--rtsp-low-latency"           // RTSP低延迟模式
        ))
        return options
    }
    
    /**
     * 高质量模式选项
     */
    private fun getHighQualityOptions(): List<String> {
        val options = mutableListOf<String>()
        options.addAll(getNetworkCacheOptions(3000))  // 高缓存
        options.addAll(getDecoderOptimizedOptions())
        options.addAll(getAudioOptimizedOptions())
        options.addAll(getVideoRenderOptimizedOptions())
        options.addAll(listOf(
            "--rtsp-tcp",
            "--network-caching=3000",
            "--live-caching=3000",
            "--file-caching=3000",
            "--avcodec-skiploopfilter=0",  // 不跳过环路滤波（保持质量）
            "--no-skip-frames",            // 不跳过帧
            "--no-drop-late-frames",       // 不丢弃延迟帧
            "--cr-average=20000",          // 更高的平均码率
            "--sout-mux-caching=200",      // 更高的复用缓存
            "--deinterlace=1"              // 启用去交错
        ))
        return options
    }
    
    /**
     * 网络优化模式选项
     */
    private fun getNetworkOptimizedOptions(): List<String> {
        val options = mutableListOf<String>()
        options.addAll(getNetworkCacheOptions(2000))
        options.addAll(getDecoderOptimizedOptions())
        options.addAll(getAudioOptimizedOptions())
        options.addAll(getVideoRenderOptimizedOptions())
        options.addAll(listOf(
            "--rtsp-tcp",
            "--network-caching=2000",
            "--live-caching=2000",
            "--file-caching=2000",
            "--cr-average=10000",
            "--sout-mux-caching=100",
            "--rtsp-timeout=60000000",     // 更长的RTSP超时
            "--rtsp-frame-buffer-size=1000000", // 更大的帧缓冲区
            "--http-reconnect",            // HTTP自动重连
            "--rtsp-reconnect"             // RTSP自动重连
        ))
        return options
    }
    
    /**
     * 省电模式选项
     */
    private fun getPowerSavingOptions(): List<String> {
        val options = mutableListOf<String>()
        options.addAll(getNetworkCacheOptions(1000))
        options.addAll(listOf(
            "--avcodec-hw=none",           // 禁用硬件解码（某些设备上更省电）
            "--avcodec-threads=1",         // 限制解码线程数
            "--avcodec-skiploopfilter=1",  // 跳过环路滤波
            "--avcodec-skip-frame=1",      // 跳过B帧
            "--avcodec-fast",
            "--drop-late-frames",
            "--skip-frames",
            "--network-caching=1000",
            "--live-caching=1000",
            "--file-caching=1000",
            "--rtsp-tcp",
            "--clock-jitter=0",
            "--clock-synchro=0",
            "--cr-average=5000",           // 降低码率
            "--sout-mux-caching=50"
        ))
        return options
    }
    
    /**
     * 根据网络质量自动选择性能模式
     * @param networkQuality 网络质量评分（0-100，越高越好）
     * @param isLiveStream 是否为直播流
     */
    fun getAutoPerformanceMode(networkQuality: Int, isLiveStream: Boolean = true): PerformanceMode {
        return when {
            networkQuality < 30 -> {
                Log.d(TAG, "网络质量差($networkQuality)，使用网络优化模式")
                PerformanceMode.NETWORK_OPTIMIZED
            }
            networkQuality < 60 -> {
                Log.d(TAG, "网络质量一般($networkQuality)，使用平衡模式")
                PerformanceMode.BALANCED
            }
            isLiveStream -> {
                Log.d(TAG, "直播流且网络质量好($networkQuality)，使用低延迟模式")
                PerformanceMode.LOW_LATENCY
            }
            else -> {
                Log.d(TAG, "点播流且网络质量好($networkQuality)，使用高质量模式")
                PerformanceMode.HIGH_QUALITY
            }
        }
    }
    
    /**
     * 获取性能监控配置
     */
    data class MonitoringConfig(
        val enableFrameRateMonitoring: Boolean = true,
        val enableBufferMonitoring: Boolean = true,
        val enableNetworkMonitoring: Boolean = true,
        val enableCpuMonitoring: Boolean = false,
        val samplingIntervalMs: Long = 1000,
        val logThresholdMs: Long = 5000
    )
    
    /**
     * 默认监控配置
     */
    val DEFAULT_MONITORING_CONFIG = MonitoringConfig()
}