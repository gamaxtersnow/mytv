package com.gamaxtersnow.mytv

import android.util.Log
import com.gamaxtersnow.mytv.models.ProgramType
import java.io.File

/**
 * M3U8工具类 - 简化版M3U8解析
 * 用于解析包含RTP/UDP地址的IPTV M3U8文件
 */
object M3UUtils {
    private const val TAG = "M3UUtils"
    
    /**
     * 解析M3U8文件内容，返回TV列表
     */
    fun parseM3U8Content(content: String): List<TV> {
        val channels = mutableListOf<TV>()
        var currentTitle = ""
        var currentGroup = "自定义"
        var currentLogo = ""
        
        val lines = content.lines()
        for (line in lines) {
            val trimmedLine = line.trim()
            
            when {
                trimmedLine.startsWith("#EXTM3U") -> continue
                trimmedLine.startsWith("#EXTINF") -> {
                    // 解析EXTINF行，提取频道信息
                    parseExtInfLine(trimmedLine)?.let { info ->
                        currentTitle = info.title
                        currentGroup = info.groupTitle.ifEmpty { "本地IPTV" }
                        currentLogo = info.logo
                    }
                }
                trimmedLine.isNotEmpty() && !trimmedLine.startsWith("#") -> {
                    // 流地址行
                    if (trimmedLine.startsWith("rtp://") || trimmedLine.startsWith("udp://") || 
                        trimmedLine.startsWith("http://") || trimmedLine.startsWith("https://")) {
                        
                        val channelName = if (currentTitle.isNotEmpty()) currentTitle else {
                            // 从URL生成默认名称
                            generateChannelNameFromUrl(trimmedLine)
                        }
                        
                        val tv = TV(
                            id = 0, // 将在TVList中分配
                            title = channelName,
                            alias = "",
                            videoUrl = listOf(trimmedLine),
                            channel = currentGroup,
                            logo = currentLogo,
                            pid = "",
                            sid = "",
                            programType = ProgramType.RTP,
                            needToken = false,
                            mustToken = false
                        )
                        
                        channels.add(tv)
                        Log.d(TAG, "解析到频道: $channelName - $trimmedLine")
                        
                        // 重置当前信息
                        currentTitle = ""
                        currentGroup = "本地IPTV"
                        currentLogo = ""
                    }
                }
            }
        }
        
        Log.d(TAG, "M3U8解析完成，共找到 ${channels.size} 个频道")
        return channels
    }
    
    /**
     * 从文件解析M3U8
     */
    fun parseM3U8File(file: File): List<TV> {
        return try {
            val content = file.readText()
            parseM3U8Content(content)
        } catch (e: Exception) {
            Log.e(TAG, "读取M3U8文件失败: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * 解析EXTINF行
     * 格式: #EXTINF:-1 tvg-id="CCTV1" tvg-name="CCTV1" tvg-logo="logo.png" group-title="央视",CCTV1 综合
     */
    private fun parseExtInfLine(line: String): ExtInfInfo? {
        return try {
            val info = ExtInfInfo()
            
            // 移除#EXTINF:前缀
            var content = line.substringAfter("#EXTINF:").trim()
            
            // 分离参数和频道名称
            val lastCommaIndex = content.lastIndexOf(',')
            if (lastCommaIndex != -1) {
                info.title = content.substring(lastCommaIndex + 1).trim()
                content = content.substring(0, lastCommaIndex)
            }
            
            // 简单解析参数（不处理复杂情况）
            val params = content.split(" ")
            for (param in params) {
                if (param.contains("=")) {
                    val keyValue = param.split("=")
                    if (keyValue.size == 2) {
                        val key = keyValue[0]
                        var value = keyValue[1].removeSurrounding("\"")
                        
                        when (key) {
                            "tvg-name" -> info.tvgName = value
                            "tvg-logo" -> info.logo = value
                            "group-title" -> info.groupTitle = value
                            "tvg-id" -> info.tvgId = value
                        }
                    }
                }
            }
            
            // 如果tvg-name不为空，使用它作为标题
            if (info.tvgName.isNotEmpty() && info.title.isEmpty()) {
                info.title = info.tvgName
            }
            
            info
        } catch (e: Exception) {
            Log.e(TAG, "解析EXTINF行失败: $line", e)
            null
        }
    }
    
    /**
     * 从URL生成频道名称
     */
    private fun generateChannelNameFromUrl(url: String): String {
        return when {
            url.startsWith("rtp://") -> {
                val address = url.substringAfter("rtp://").substringBefore("/")
                "RTP频道 $address"
            }
            url.startsWith("udp://") -> {
                val address = url.substringAfter("udp://").substringBefore("/")
                "UDP频道 $address"
            }
            else -> {
                val fileName = url.substringAfterLast("/").substringBefore("?")
                if (fileName.isNotEmpty()) fileName else "未知频道"
            }
        }
    }
    
    /**
     * EXTINF信息数据类
     */
    private data class ExtInfInfo(
        var title: String = "",
        var tvgId: String = "",
        var tvgName: String = "",
        var logo: String = "",
        var groupTitle: String = ""
    )
}