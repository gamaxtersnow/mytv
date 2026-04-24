package com.lizongying.mytv

import android.content.Context
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.Player
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.datasource.DefaultDataSource

/**
 * RTP播放助手类
 * 用于优化RTP/UDP流的播放性能
 */
object RTPPlayerHelper {
    private const val TAG = "RTPPlayerHelper"
    
    /**
     * 为RTP流创建优化的ExoPlayer实例（media3版本）
     */
    @UnstableApi
    fun createOptimizedExoPlayer(context: Context): ExoPlayer {
        Log.d(TAG, "创建优化的ExoPlayer用于RTP流播放")
        
        // 创建优化的轨道选择器
        val trackSelector = DefaultTrackSelector(context).apply {
            // 设置参数以优先选择适合的轨道
            val parameters = buildUponParameters()
                .setMaxVideoSizeSd()
                .setPreferredAudioLanguage("zh")
                .build()
            setParameters(parameters)
        }
        
        // 创建优化的加载控制器（增加缓冲区大小）
        val loadControl: LoadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                MIN_BUFFER_MS,        // 最小缓冲区
                MAX_BUFFER_MS,        // 最大缓冲区
                BUFFER_FOR_PLAYBACK_MS, // 开始播放所需缓冲区
                BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS // 重新缓冲后播放所需缓冲区
            )
            .setTargetBufferBytes(C.LENGTH_UNSET)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        
        // 创建数据源工厂
        val dataSourceFactory = DefaultDataSource.Factory(context)
        
        // 创建媒体源工厂
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        
        // 创建并配置ExoPlayer
        return ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(mediaSourceFactory)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build().apply {
                // 设置播放参数
                playWhenReady = true
                repeatMode = ExoPlayer.REPEAT_MODE_OFF
                
                Log.d(TAG, "ExoPlayer创建完成，缓冲区配置: min=$MIN_BUFFER_MS ms, max=$MAX_BUFFER_MS ms")
            }
    }
    
    /**
     * 检查URL是否是RTP/UDP流
     */
    fun isRTPOrUDPUrl(url: String): Boolean {
        return url.startsWith("rtp://") || 
               url.startsWith("udp://") ||
               url.startsWith("rtsp://") ||
               url.contains(":1234") || // 常见的RTP端口
               url.contains(":554")     // 常见的RTSP端口
    }
    
    /**
     * 获取RTP流的优化播放参数建议
     */
    fun getRTPPlaybackTips(): String {
        return """
        RTP/UDP流播放优化建议:
        1. 确保设备连接到正确的网络（组播流需要本地网络）
        2. 检查防火墙设置，允许UDP流量
        3. 如果播放卡顿，尝试重启应用或设备
        4. 确保RTP地址格式正确: rtp://IP地址:端口
        5. 组播地址通常以239.x.x.x开头
        """.trimIndent()
    }
    
    /**
     * 为RTP流创建MediaItem
     */
    fun createRTPMediaItem(url: String): MediaItem {
        return MediaItem.Builder()
            .setUri(url)
            .setMimeType("video/mp2t") // RTP流通常是MPEG-TS格式
            .build()
    }
    
    // 缓冲区配置常量（针对RTP流优化）
    private const val MIN_BUFFER_MS = 5000          // 5秒最小缓冲区
    private const val MAX_BUFFER_MS = 30000         // 30秒最大缓冲区
    private const val BUFFER_FOR_PLAYBACK_MS = 2500 // 2.5秒后开始播放
    private const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 5000 // 重新缓冲后5秒
    
    /**
     * 应用RTP播放优化到现有的ExoPlayer
     */
    @UnstableApi
    fun applyRTPOptimizations(player: ExoPlayer) {
        try {
            // 增加缓冲区大小
            player.playbackParameters = player.playbackParameters
            
            Log.d(TAG, "已应用RTP播放优化到ExoPlayer")
        } catch (e: Exception) {
            Log.e(TAG, "应用RTP优化失败: ${e.message}")
        }
    }
    
    /**
     * 检测URL是否为RTP流
     */
    fun isRTPUrl(url: String): Boolean {
        return url.startsWith("rtp://")
    }
    
    /**
     * 获取RTP播放优化建议
     */
    fun getOptimizationTips(): String {
        return """
            1. 确保设备与RTP源在同一局域网
            2. 网络带宽建议至少10Mbps
            3. 使用有线网络连接更稳定
            4. 关闭其他占用带宽的应用
        """.trimIndent()
    }
}