package com.gamaxtersnow.mytv

import android.content.Context
import android.util.Log

/**
 * 智能播放器工厂
 * 根据URL协议自动选择最适合的播放器
 */
object PlayerFactory {
    
    private const val TAG = "PlayerFactory"
    
    // 协议到播放器类型的映射
    private val PROTOCOL_PLAYER_MAP = mapOf(
        // RTP/UDP协议 - 使用ExoPlayer + UdpDataSource（利用ExoPlayer的缓冲管理）
        "rtp://" to UnifiedVideoPlayer.PlayerType.EXO_PLAYER,
        "udp://" to UnifiedVideoPlayer.PlayerType.EXO_PLAYER,
        "rtp:" to UnifiedVideoPlayer.PlayerType.EXO_PLAYER,
        "udp:" to UnifiedVideoPlayer.PlayerType.EXO_PLAYER,

        // RTSP协议 - 使用ExoPlayer
        "rtsp://" to UnifiedVideoPlayer.PlayerType.EXO_PLAYER,

        // HTTP/HTTPS协议 - 使用ExoPlayer
        "http://" to UnifiedVideoPlayer.PlayerType.EXO_PLAYER,
        "https://" to UnifiedVideoPlayer.PlayerType.EXO_PLAYER,

        // HLS协议 - 使用ExoPlayer
        "hls://" to UnifiedVideoPlayer.PlayerType.EXO_PLAYER,
        "hls+http://" to UnifiedVideoPlayer.PlayerType.EXO_PLAYER,
        "hls+https://" to UnifiedVideoPlayer.PlayerType.EXO_PLAYER,

        // DASH协议 - 使用ExoPlayer
        "dash://" to UnifiedVideoPlayer.PlayerType.EXO_PLAYER,
        "dash+http://" to UnifiedVideoPlayer.PlayerType.EXO_PLAYER,
        "dash+https://" to UnifiedVideoPlayer.PlayerType.EXO_PLAYER,

    )
    
    // 默认播放器类型（当无法识别协议时）
    private val DEFAULT_PLAYER_TYPE = UnifiedVideoPlayer.PlayerType.EXO_PLAYER
    
    /**
     * 根据URL创建合适的播放器
     * @param context 上下文
     * @param url 视频URL
     * @param forcePlayerType 强制使用指定播放器类型（可选）
     * @return 统一播放器实例
     */
    fun createPlayer(
        context: Context,
        url: String,
        forcePlayerType: UnifiedVideoPlayer.PlayerType? = null
    ): UnifiedVideoPlayer {
        val playerType = forcePlayerType ?: detectPlayerType(url)
        Log.i(TAG, "为URL创建播放器: $url, 类型: $playerType")
        
        return createExoPlayer(context)
    }
    
    /**
     * 根据URL检测最适合的播放器类型
     * @param url 视频URL
     * @return 播放器类型
     */
    fun detectPlayerType(url: String): UnifiedVideoPlayer.PlayerType {
        // 检查URL是否匹配已知协议
        for ((protocol, playerType) in PROTOCOL_PLAYER_MAP) {
            if (url.startsWith(protocol, ignoreCase = true)) {
                Log.d(TAG, "检测到协议: $protocol, 选择播放器: $playerType")
                return playerType
            }
        }
        
        // 检查URL是否包含特定关键词
        val lowerUrl = url.lowercase()
        when {
            lowerUrl.contains(".m3u8") || lowerUrl.contains("hls") -> {
                Log.d(TAG, "检测到HLS流，选择ExoPlayer")
                return UnifiedVideoPlayer.PlayerType.EXO_PLAYER
            }
            lowerUrl.contains(".mpd") || lowerUrl.contains("dash") -> {
                Log.d(TAG, "检测到DASH流，选择ExoPlayer")
                return UnifiedVideoPlayer.PlayerType.EXO_PLAYER
            }
            else -> {
                Log.d(TAG, "无法识别协议，使用默认播放器: $DEFAULT_PLAYER_TYPE")
                return DEFAULT_PLAYER_TYPE
            }
        }
    }
    
    /**
     * 创建ExoPlayer实例
     */
    private fun createExoPlayer(context: Context): ExoPlayerWrapper {
        Log.d(TAG, "创建ExoPlayer实例")
        val player = ExoPlayerWrapper()
        player.initialize(context)
        return player
    }
    
    /**
     * 检查URL是否支持RTP/UDP协议
     */
    fun isRTPStream(url: String): Boolean {
        return url.startsWith("rtp://", ignoreCase = true) ||
               url.startsWith("rtp:", ignoreCase = true)
    }

    /**
     * 检查URL是否是UDP流
     */
    fun isUDPStream(url: String): Boolean {
        return url.startsWith("udp://", ignoreCase = true) ||
               url.startsWith("udp:", ignoreCase = true)
    }
    
    /**
     * 检查URL是否支持RTSP协议
     */
    fun isRTSPStream(url: String): Boolean {
        return url.startsWith("rtsp://", ignoreCase = true)
    }
    
    /**
     * 检查URL是否支持HTTP/HLS协议（ExoPlayer擅长）
     */
    fun isHTTPStream(url: String): Boolean {
        return url.startsWith("http://", ignoreCase = true) ||
               url.startsWith("https://", ignoreCase = true) ||
               url.contains(".m3u8", ignoreCase = true) ||
               url.contains("hls", ignoreCase = true)
    }
    
    /**
     * 获取URL的协议类型描述
     */
    fun getProtocolDescription(url: String): String {
        return when {
            isRTPStream(url) -> "RTP/UDP流"
            isRTSPStream(url) -> "RTSP流"
            url.startsWith("http://", ignoreCase = true) -> "HTTP流"
            url.startsWith("https://", ignoreCase = true) -> "HTTPS流"
            url.contains(".m3u8", ignoreCase = true) -> "HLS流"
            url.contains(".mpd", ignoreCase = true) -> "DASH流"
            url.startsWith("rtmp://", ignoreCase = true) -> "RTMP流"
            else -> "未知流"
        }
    }
    
    /**
     * 获取所有支持的协议列表
     */
    fun getSupportedProtocols(): List<String> {
        return PROTOCOL_PLAYER_MAP.keys.toList()
    }
    
    /**
     * 验证URL是否被支持
     */
    fun isUrlSupported(url: String): Boolean {
        // 检查是否匹配已知协议
        for (protocol in PROTOCOL_PLAYER_MAP.keys) {
            if (url.startsWith(protocol, ignoreCase = true)) {
                return true
            }
        }
        
        // 检查是否包含支持的关键词
        val lowerUrl = url.lowercase()
        return lowerUrl.contains(".m3u8") ||
               lowerUrl.contains(".mpd") ||
               lowerUrl.contains("hls") ||
               lowerUrl.contains("dash")
    }
}