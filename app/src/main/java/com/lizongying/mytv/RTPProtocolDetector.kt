package com.lizongying.mytv

import android.util.Log

/**
 * RTP协议检测器
 * 增强的RTP/UDP/RTSP协议识别和解析工具
 */
object RTPProtocolDetector {
    
    private const val TAG = "RTPProtocolDetector"
    
    // RTP/UDP协议前缀
    private val RTP_PROTOCOLS = listOf("rtp://", "udp://", "rtp:", "udp:")
    
    // RTSP协议前缀
    private val RTSP_PROTOCOLS = listOf("rtsp://")
    
    // 组播地址范围
    private val MULTICAST_RANGES = listOf(
        "224.0.0.0" to "239.255.255.255",  // IPv4组播地址范围
        "ff00::" to "ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff"  // IPv6组播地址范围
    )
    
    // 常见RTP端口
    private val COMMON_RTP_PORTS = setOf(5000, 5002, 5004, 5006, 1234, 1235, 1236)
    
    /**
     * 检测URL是否为RTP/UDP流
     */
    fun isRTPStream(url: String): Boolean {
        return RTP_PROTOCOLS.any { url.startsWith(it, ignoreCase = true) }
    }
    
    /**
     * 检测URL是否为RTSP流
     */
    fun isRTSPStream(url: String): Boolean {
        return RTSP_PROTOCOLS.any { url.startsWith(it, ignoreCase = true) }
    }
    
    /**
     * 检测URL是否为组播流
     */
    fun isMulticastStream(url: String): Boolean {
        if (!isRTPStream(url) && !isRTSPStream(url)) {
            return false
        }
        
        return try {
            // 提取主机地址
            val host = extractHostFromUrl(url)
            isMulticastAddress(host)
        } catch (e: Exception) {
            Log.w(TAG, "解析组播地址失败: ${e.message}")
            false
        }
    }
    
    /**
     * 检测地址是否为组播地址
     */
    fun isMulticastAddress(address: String): Boolean {
        // 检查IPv4组播地址 (224.0.0.0 - 239.255.255.255)
        if (address.matches(Regex("^\\d+\\.\\d+\\.\\d+\\.\\d+$"))) {
            val parts = address.split(".").map { it.toInt() }
            if (parts.size == 4) {
                val firstOctet = parts[0]
                return firstOctet in 224..239
            }
        }
        
        // 检查IPv6组播地址 (ff00::/8)
        if (address.contains(":")) {
            return address.lowercase().startsWith("ff")
        }
        
        return false
    }
    
    /**
     * 从URL中提取主机地址
     */
    fun extractHostFromUrl(url: String): String {
        // 移除协议前缀
        var cleanUrl = url
        val protocols = RTP_PROTOCOLS + RTSP_PROTOCOLS + listOf("http://", "https://")
        for (protocol in protocols) {
            if (url.startsWith(protocol, ignoreCase = true)) {
                cleanUrl = url.substring(protocol.length)
                break
            }
        }
        
        // 提取主机部分（直到第一个:或/）
        val hostEnd = cleanUrl.indexOfAny(charArrayOf(':', '/'))
        return if (hostEnd > 0) {
            cleanUrl.substring(0, hostEnd)
        } else {
            cleanUrl
        }
    }
    
    /**
     * 从URL中提取端口号
     */
    fun extractPortFromUrl(url: String): Int? {
        return try {
            // 查找端口号（在主机后的:之后）
            val host = extractHostFromUrl(url)
            val afterHost = url.substring(url.indexOf(host) + host.length)
            
            if (afterHost.startsWith(":")) {
                val portEnd = afterHost.indexOfAny(charArrayOf('/', '?'))
                val portStr = if (portEnd > 0) {
                    afterHost.substring(1, portEnd)
                } else {
                    afterHost.substring(1)
                }
                portStr.toInt()
            } else {
                // 默认端口
                when {
                    isRTPStream(url) -> 5004  // RTP默认端口
                    isRTSPStream(url) -> 554   // RTSP默认端口
                    else -> null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "提取端口失败: ${e.message}")
            null
        }
    }
    
    /**
     * 获取流协议详细信息
     */
    fun getStreamProtocolDetails(url: String): StreamProtocolInfo {
        val isRTP = isRTPStream(url)
        val isRTSP = isRTSPStream(url)
        val isMulticast = isMulticastStream(url)
        val host = extractHostFromUrl(url)
        val port = extractPortFromUrl(url)
        val isCommonPort = port in COMMON_RTP_PORTS
        
        return StreamProtocolInfo(
            url = url,
            protocolType = when {
                isRTP -> ProtocolType.RTP
                isRTSP -> ProtocolType.RTSP
                else -> ProtocolType.UNKNOWN
            },
            host = host,
            port = port,
            isMulticast = isMulticast,
            isCommonPort = isCommonPort,
            recommendedPlayer = when {
                isRTP || isRTSP -> RecommendedPlayer.GSY_PLAYER
                else -> RecommendedPlayer.EXO_PLAYER
            }
        )
    }
    
    /**
     * 验证RTP/UDP流URL格式
     */
    fun validateRTPUrl(url: String): ValidationResult {
        if (!isRTPStream(url) && !isRTSPStream(url)) {
            return ValidationResult(false, "不是RTP/RTSP协议URL")
        }
        
        val host = extractHostFromUrl(url)
        if (host.isEmpty()) {
            return ValidationResult(false, "URL中缺少主机地址")
        }
        
        val port = extractPortFromUrl(url)
        if (port == null || port <= 0 || port > 65535) {
            return ValidationResult(false, "端口号无效")
        }
        
        return ValidationResult(true, "URL格式有效")
    }
    
    /**
     * 获取RTP流播放建议
     */
    fun getRTPPlaybackSuggestions(url: String): List<String> {
        val suggestions = mutableListOf<String>()
        val info = getStreamProtocolDetails(url)
        
        suggestions.add("协议类型: ${info.protocolType}")
        suggestions.add("主机地址: ${info.host}")
        info.port?.let { suggestions.add("端口: $it") }
        
        if (info.isMulticast) {
            suggestions.add("⚠️ 这是组播流，需要网络支持IGMP")
            suggestions.add("建议: 确保路由器支持组播转发")
        }
        
        when (info.recommendedPlayer) {
            RecommendedPlayer.GSY_PLAYER -> {
                suggestions.add("推荐播放器: GSYVideoPlayer (对RTP/RTSP支持更好)")
                suggestions.add("GSY优化: 启用硬件解码，增加网络缓存")
            }
            RecommendedPlayer.EXO_PLAYER -> {
                suggestions.add("推荐播放器: ExoPlayer")
                suggestions.add("ExoPlayer优化: 增加缓冲区大小，使用TCP传输")
            }
            else -> {
                suggestions.add("推荐播放器: 使用默认播放器")
            }
        }
        
        if (info.isCommonPort) {
            suggestions.add("✅ 使用常见RTP端口，兼容性较好")
        }
        
        return suggestions
    }
    
    /**
     * 协议类型枚举
     */
    enum class ProtocolType {
        RTP, RTSP, UDP, HTTP, HLS, DASH, UNKNOWN
    }
    
    /**
     * 推荐播放器枚举
     */
    enum class RecommendedPlayer {
        GSY_PLAYER, EXO_PLAYER, AUTO
    }
    
    /**
     * 流协议信息数据类
     */
    data class StreamProtocolInfo(
        val url: String,
        val protocolType: ProtocolType,
        val host: String,
        val port: Int?,
        val isMulticast: Boolean,
        val isCommonPort: Boolean,
        val recommendedPlayer: RecommendedPlayer
    )
    
    /**
     * 验证结果数据类
     */
    data class ValidationResult(
        val isValid: Boolean,
        val message: String
    )
}