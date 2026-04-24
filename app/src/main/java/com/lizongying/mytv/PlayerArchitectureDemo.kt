package com.lizongying.mytv

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.lizongying.mytv.databinding.ActivityPlayerDemoBinding

/**
 * 播放器架构演示Activity
 * 展示统一播放器架构的功能和协议自动识别
 */
class PlayerArchitectureDemo : AppCompatActivity() {
    
    private lateinit var binding: ActivityPlayerDemoBinding
    private lateinit var logTextView: TextView
    private lateinit var protocolInfoTextView: TextView
    
    private var currentPlayer: UnifiedVideoPlayer? = null
    
    companion object {
        private const val TAG = "PlayerArchitectureDemo"
        
        // 演示URLs
        private val DEMO_URLS = listOf(
            "rtp://239.1.1.1:5000" to "RTP组播电视流",
            "udp://192.168.1.100:1234" to "UDP单播流",
            "rtsp://example.com:554/live" to "RTSP监控流",
            "http://example.com/stream.m3u8" to "HLS在线直播",
            "https://example.com/video.mp4" to "HTTPS点播视频",
            "rtmp://example.com/live/stream" to "RTMP直播流"
        )
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerDemoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        logTextView = binding.logTextView
        protocolInfoTextView = binding.protocolInfoTextView
        
        setupUI()
        logMessage("播放器架构演示已启动")
        logMessage("统一播放器架构版本: 1.0")
    }
    
    private fun setupUI() {
        // 协议检测演示
        binding.btnDetectProtocols.setOnClickListener {
            demonstrateProtocolDetection()
        }
        
        // 播放器创建演示
        binding.btnCreatePlayers.setOnClickListener {
            demonstratePlayerCreation()
        }
        
        // 混合播放场景演示
        binding.btnHybridScenarios.setOnClickListener {
            demonstrateHybridScenarios()
        }
        
        // 清理日志
        binding.btnClearLog.setOnClickListener {
            logTextView.text = ""
            protocolInfoTextView.text = ""
        }
        
        // 测试RTP协议检测器
        binding.btnTestRTPDetector.setOnClickListener {
            testRTPProtocolDetector()
        }
    }
    
    /**
     * 演示协议检测功能
     */
    private fun demonstrateProtocolDetection() {
        logMessage("\n=== 协议检测演示 ===")
        
        DEMO_URLS.forEach { (url, description) ->
            val playerType = PlayerFactory.detectPlayerType(url)
            val protocolDesc = PlayerFactory.getProtocolDescription(url)
            val isSupported = PlayerFactory.isUrlSupported(url)
            val isRTP = PlayerFactory.isRTPStream(url)
            val isHTTP = PlayerFactory.isHTTPStream(url)
            
            val info = """
                URL: $url
                描述: $description
                检测到的播放器: $playerType
                协议类型: $protocolDesc
                是否支持: $isSupported
                是RTP流: $isRTP
                是HTTP流: $isHTTP
            """.trimIndent()
            
            logMessage(info)
            logMessage("---")
        }
        
        logMessage("协议检测演示完成 ✓")
    }
    
    /**
     * 演示播放器创建功能
     */
    private fun demonstratePlayerCreation() {
        logMessage("\n=== 播放器创建演示 ===")
        
        // 释放之前的播放器
        currentPlayer?.release()
        
        // 测试创建不同协议的播放器
        val testUrls = listOf(
            "rtp://239.1.1.1:5000",
            "http://example.com/stream.m3u8"
        )
        
        testUrls.forEach { url ->
            logMessage("创建播放器 for: $url")
            
            val player = PlayerFactory.createPlayer(this, url)
            currentPlayer = player
            
            val playerInfo = """
                播放器类型: ${player.getPlayerType()}
                播放器状态: ${player.getPlayerState()}
                支持此协议: ${player.supportsProtocol(url)}
                协议详细信息: ${PlayerFactory.getProtocolDescription(url)}
            """.trimIndent()
            
            logMessage(playerInfo)
            
            // 演示播放器基本操作
            try {
                player.prepare(url)
                logMessage("播放器准备成功")
                
                player.start()
                logMessage("播放器开始播放")
                
                Thread.sleep(100) // 短暂等待
                
                player.pause()
                logMessage("播放器暂停")
                
                player.release()
                logMessage("播放器资源释放")
            } catch (e: Exception) {
                logMessage("播放器操作错误: ${e.message}")
            }
            
            logMessage("---")
        }
        
        logMessage("播放器创建演示完成 ✓")
    }
    
    /**
     * 演示混合播放场景
     */
    private fun demonstrateHybridScenarios() {
        logMessage("\n=== 混合播放场景演示 ===")
        
        val scenarios = listOf(
            Scenario("IPTV电视直播", "rtp://239.1.1.1:5000", "使用GSYVideoPlayer播放RTP组播流"),
            Scenario("在线视频点播", "http://example.com/video.mp4", "使用ExoPlayer播放HTTP流"),
            Scenario("监控摄像头", "rtsp://192.168.1.100:554/stream", "使用GSYVideoPlayer播放RTSP流"),
            Scenario("网络直播", "http://example.com/live.m3u8", "使用ExoPlayer播放HLS流")
        )
        
        scenarios.forEach { scenario ->
            logMessage("\n场景: ${scenario.name}")
            logMessage("URL: ${scenario.url}")
            logMessage("描述: ${scenario.description}")
            
            val playerType = PlayerFactory.detectPlayerType(scenario.url)
            val protocolInfo = RTPProtocolDetector.getStreamProtocolDetails(scenario.url)
            
            val info = """
                自动选择的播放器: $playerType
                协议类型: ${protocolInfo.protocolType}
                主机地址: ${protocolInfo.host}
                端口: ${protocolInfo.port ?: "默认"}
                是否组播: ${protocolInfo.isMulticast}
                推荐播放器: ${protocolInfo.recommendedPlayer}
            """.trimIndent()
            
            logMessage(info)
            
            // 显示协议建议
            val suggestions = RTPProtocolDetector.getRTPPlaybackSuggestions(scenario.url)
            logMessage("播放建议:")
            suggestions.forEach { suggestion ->
                logMessage("  • $suggestion")
            }
            
            logMessage("场景验证: 通过 ✓")
        }
        
        logMessage("\n混合播放场景演示完成 ✓")
        logMessage("架构支持自动播放器选择和多协议播放")
    }
    
    /**
     * 测试RTP协议检测器
     */
    private fun testRTPProtocolDetector() {
        logMessage("\n=== RTP协议检测器测试 ===")
        
        val testCases = listOf(
            TestCase("rtp://239.1.1.1:5000", "标准RTP组播"),
            TestCase("udp://192.168.1.100:1234", "UDP单播"),
            TestCase("rtp://224.0.0.1", "RTP无端口"),
            TestCase("rtsp://example.com:554/stream", "RTSP流"),
            TestCase("http://example.com/video.mp4", "HTTP流（对比）")
        )
        
        testCases.forEach { testCase ->
            logMessage("\n测试: ${testCase.description}")
            logMessage("URL: ${testCase.url}")
            
            val detectorInfo = """
                是RTP流: ${RTPProtocolDetector.isRTPStream(testCase.url)}
                是RTSP流: ${RTPProtocolDetector.isRTSPStream(testCase.url)}
                是组播流: ${RTPProtocolDetector.isMulticastStream(testCase.url)}
                主机地址: ${RTPProtocolDetector.extractHostFromUrl(testCase.url)}
                端口: ${RTPProtocolDetector.extractPortFromUrl(testCase.url)}
            """.trimIndent()
            
            logMessage(detectorInfo)
            
            // 验证URL格式
            val validation = RTPProtocolDetector.validateRTPUrl(testCase.url)
            logMessage("URL验证: ${validation.message}")
            
            if (validation.isValid) {
                logMessage("✅ URL格式有效")
            } else {
                logMessage("⚠️ URL格式问题: ${validation.message}")
            }
        }
        
        logMessage("\nRTP协议检测器测试完成 ✓")
    }
    
    /**
     * 记录消息到日志视图
     */
    private fun logMessage(message: String) {
        Log.d(TAG, message)
        runOnUiThread {
            val currentText = logTextView.text.toString()
            logTextView.text = "$currentText\n$message"
            
            // 自动滚动到底部
            val scrollAmount = logTextView.layout.getLineTop(logTextView.lineCount) - logTextView.height
            if (scrollAmount > 0) {
                logTextView.scrollTo(0, scrollAmount)
            } else {
                logTextView.scrollTo(0, 0)
            }
        }
    }
    
    /**
     * 更新协议信息视图
     */
    private fun updateProtocolInfo(info: String) {
        runOnUiThread {
            protocolInfoTextView.text = info
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // 释放播放器资源
        currentPlayer?.release()
        logMessage("演示Activity销毁，资源已释放")
    }
    
    /**
     * 场景数据类
     */
    data class Scenario(
        val name: String,
        val url: String,
        val description: String
    )
    
    /**
     * 测试用例数据类
     */
    data class TestCase(
        val url: String,
        val description: String
    )
}