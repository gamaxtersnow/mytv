package com.lizongying.mytv

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.lizongying.mytv.PerformanceConfig.PerformanceMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * RTP测试Activity
 * 用于在Meta20实体机上验证RTP组播播放功能
 */
class RTPTestActivity : AppCompatActivity() {
    private val TAG = "RTPTestActivity"
    
    // UI组件
    private lateinit var surfaceView: SurfaceView
    private lateinit var statusTextView: TextView
    private lateinit var performanceTextView: TextView
    private lateinit var logTextView: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var changeModeButton: Button
    private lateinit var generateReportButton: Button
    
    // 播放器组件
    private lateinit var playerWrapper: VLCPlayerWrapper
    private lateinit var performanceMonitor: PerformanceMonitor
    private lateinit var errorRecoveryManager: ErrorRecoveryManager
    
    // 测试配置
    private val testStreams = listOf(
        // RTP组播测试流（示例地址，实际使用时需要替换）
        "rtp://239.255.1.1:5000",      // 标准RTP组播
        "rtp://239.255.1.2:5001",      // 高清RTP流
        "rtp://239.255.1.3:5002",      // 低延迟RTP流
        "udp://@239.255.1.4:5003",     // UDP组播
        "rtsp://example.com/live/stream1",  // RTSP流（备用）
        "rtsp://example.com/live/stream2"   // RTSP流（备用）
    )
    
    private var currentStreamIndex = 0
    private var isPlaying = false
    private var currentPerformanceMode = PerformanceMode.BALANCED
    private val handler = Handler(Looper.getMainLooper())
    private val logEntries = mutableListOf<String>()
    private val maxLogEntries = 50
    
    // 性能数据
    private var startTime: Long = 0
    private var totalFrames = 0
    private var totalDroppedFrames = 0
    private var totalBytesReceived = 0L
    
    companion object {
        /**
         * 启动RTP测试Activity
         */
        fun start(context: Context) {
            val intent = Intent(context, RTPTestActivity::class.java)
            context.startActivity(intent)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rtp_test)
        
        // 初始化UI组件
        initViews()
        
        // 初始化播放器
        initPlayer()
        
        // 初始化性能监控
        initPerformanceMonitor()
        
        // 初始化错误恢复管理器
        initErrorRecoveryManager()
        
        // 更新UI状态
        updateUI()
        
        logMessage("RTP测试Activity已创建")
        logMessage("设备型号: ${android.os.Build.MODEL}")
        logMessage("Android版本: ${android.os.Build.VERSION.RELEASE}")
    }
    
    private fun initViews() {
        surfaceView = findViewById(R.id.surface_view)
        statusTextView = findViewById(R.id.status_text)
        performanceTextView = findViewById(R.id.performance_text)
        logTextView = findViewById(R.id.log_text)
        startButton = findViewById(R.id.start_button)
        stopButton = findViewById(R.id.stop_button)
        changeModeButton = findViewById(R.id.change_mode_button)
        generateReportButton = findViewById(R.id.generate_report_button)
        
        // 设置按钮点击监听器
        startButton.setOnClickListener { startPlayback() }
        stopButton.setOnClickListener { stopPlayback() }
        changeModeButton.setOnClickListener { changePerformanceMode() }
        generateReportButton.setOnClickListener { generatePerformanceReport() }
        
        // 设置Surface回调
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                logMessage("Surface已创建")
                playerWrapper.setVideoSurface(holder)
            }
            
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                logMessage("Surface尺寸变化: ${width}x$height")
            }
            
            override fun surfaceDestroyed(holder: SurfaceHolder) {
                logMessage("Surface已销毁")
                playerWrapper.setVideoSurface(null)
            }
        })
    }
    
    private fun initPlayer() {
        playerWrapper = VLCPlayerWrapper()
        
        // 设置性能模式
        playerWrapper.setPerformanceMode(currentPerformanceMode)
        
        // 初始化播放器
        val initialized = playerWrapper.initialize(this)
        if (initialized) {
            logMessage("VLC播放器初始化成功")
            
            // 设置事件监听器
            playerWrapper.setEventListener(object : UnifiedVideoPlayer.EventListener {
                override fun onStateChanged(state: UnifiedVideoPlayer.PlayerState) {
                    runOnUiThread {
                        updateStatus("播放器状态: $state")
                        logMessage("状态变化: $state")
                    }
                }
                
                override fun onBufferingUpdate(percent: Int) {
                    runOnUiThread {
                        updatePerformanceInfo("缓冲: $percent%")
                    }
                }
                
                override fun onProgressUpdate(position: Long, duration: Long) {
                    // 更新进度信息
                }
                
                override fun onPlaybackCompleted() {
                    runOnUiThread {
                        logMessage("播放完成")
                        stopPlayback()
                    }
                }
                
                override fun onError(error: String) {
                    runOnUiThread {
                        updateStatus("错误: $error")
                        logMessage("播放错误: $error")
                        
                        // 处理错误
                        val errorInfo = errorRecoveryManager.createErrorInfo(
                            ErrorRecoveryManager.ErrorType.NETWORK_ERROR,
                            error,
                            getCurrentStreamUrl()
                        )
                        errorRecoveryManager.handleError(errorInfo)
                    }
                }
            })
        } else {
            logMessage("VLC播放器初始化失败")
            updateStatus("播放器初始化失败")
        }
    }
    
    private fun initPerformanceMonitor() {
        performanceMonitor = PerformanceMonitor(this)
        
        // 配置性能监控
        val config = PerformanceMonitor.MonitoringConfig(
            samplingIntervalMs = 2000, // 2秒采样一次
            logToFile = false,
            maxHistorySize = 100
        )
        
        performanceMonitor.startMonitoring(config)
        
        // 设置性能事件监听器
        performanceMonitor.setEventListener(object : PerformanceMonitor.PerformanceEventListener {
            override fun onMetricUpdated(metric: PerformanceMonitor.MetricData) {
                runOnUiThread {
                    when (metric.type) {
                        PerformanceMonitor.MetricType.FRAME_RATE -> {
                            updatePerformanceInfo("帧率: ${metric.value.toInt()}fps")
                            totalFrames = (metric.value * 10).toInt() // 估算
                        }
                        PerformanceMonitor.MetricType.NETWORK_BITRATE -> {
                            val bitrateMbps = metric.value / 1000000
                            updatePerformanceInfo("比特率: ${"%.2f".format(bitrateMbps)} Mbps")
                        }
                        PerformanceMonitor.MetricType.NETWORK_QUALITY -> {
                            updatePerformanceInfo("网络质量: ${metric.value.toInt()}/100")
                        }
                        else -> {}
                    }
                }
            }
            
            override fun onIssueDetected(issue: PerformanceMonitor.PerformanceIssue) {
                runOnUiThread {
                    logMessage("性能问题: ${issue.description} (${issue.severity})")
                }
            }
            
            override fun onPerformanceAlert(alert: PerformanceMonitor.PerformanceAlert) {
                runOnUiThread {
                    logMessage("性能警报: ${alert.message}")
                }
            }
        })
    }
    
    private fun initErrorRecoveryManager() {
        errorRecoveryManager = ErrorRecoveryManager(this)
        
        // 配置错误恢复
        val config = ErrorRecoveryManager.RecoveryConfig(
            maxRetryCount = 3,
            retryDelayMs = 3000,
            networkCheckEnabled = true,
            adaptiveBitrateEnabled = true,
            fallbackToLowerQuality = true
        )
        
        errorRecoveryManager.initialize(config)
        
        // 设置错误恢复事件监听器
        errorRecoveryManager.setEventListener(object : ErrorRecoveryManager.RecoveryEventListener {
            override fun onErrorDetected(error: ErrorRecoveryManager.ErrorInfo) {
                runOnUiThread {
                    logMessage("错误检测: ${error.type} - ${error.message}")
                }
            }
            
            override fun onRecoveryStarted(strategy: ErrorRecoveryManager.RecoveryStrategy) {
                runOnUiThread {
                    logMessage("开始恢复: $strategy")
                }
            }
            
            override fun onRecoveryCompleted(success: Boolean, message: String) {
                runOnUiThread {
                    logMessage("恢复完成: $success - $message")
                    
                    if (success && isPlaying) {
                        // 恢复成功后尝试重新播放
                        handler.postDelayed({
                            startPlayback()
                        }, 1000)
                    }
                }
            }
            
            override fun onNetworkStatusChanged(available: Boolean, networkType: String) {
                runOnUiThread {
                    logMessage("网络状态变化: 可用=$available, 类型=$networkType")
                }
            }
        })
    }
    
    private fun startPlayback() {
        if (isPlaying) {
            logMessage("已经在播放中")
            return
        }
        
        val streamUrl = getCurrentStreamUrl()
        logMessage("开始播放: $streamUrl")
        updateStatus("正在连接...")
        
        // 记录开始时间
        startTime = System.currentTimeMillis()
        
        // 准备播放器
        playerWrapper.prepare(streamUrl, null)
        
        // 开始播放
        playerWrapper.start()
        
        isPlaying = true
        updateUI()
        
        // 开始性能监控更新
        startPerformanceUpdates()
    }
    
    private fun stopPlayback() {
        if (!isPlaying) return
        
        logMessage("停止播放")
        updateStatus("停止播放")
        
        playerWrapper.stop()
        isPlaying = false
        
        // 停止性能监控更新
        stopPerformanceUpdates()
        
        // 生成播放统计
        generatePlaybackStats()
        
        updateUI()
    }
    
    private fun changePerformanceMode() {
        val modes = PerformanceMode.values()
        val currentIndex = modes.indexOf(currentPerformanceMode)
        val nextIndex = (currentIndex + 1) % modes.size
        
        currentPerformanceMode = modes[nextIndex]
        playerWrapper.setPerformanceMode(currentPerformanceMode)
        
        logMessage("切换到性能模式: $currentPerformanceMode")
        updateStatus("性能模式: $currentPerformanceMode")
        
        // 如果正在播放，重新应用设置
        if (isPlaying) {
            logMessage("重新应用性能设置...")
            stopPlayback()
            handler.postDelayed({
                startPlayback()
            }, 500)
        }
    }
    
    private fun generatePerformanceReport() {
        logMessage("生成性能报告...")
        
        val report = performanceMonitor.generateReport("rtp_test_${System.currentTimeMillis()}")
        
        // 显示报告摘要
        val summary = report.summary
        val reportText = """
            === 性能报告 ===
            会话ID: ${report.sessionId}
            测试时长: ${(report.endTime - report.startTime) / 1000}秒
            平均帧率: ${"%.1f".format(summary.averageFrameRate)} fps
            平均缓冲: ${"%.1f".format(summary.averageBufferLevel)}%
            平均比特率: ${"%.2f".format(summary.averageBitrate / 1000000)} Mbps
            网络质量: ${summary.networkQualityScore}/100
            播放稳定性: ${"%.1f".format(summary.playbackStability)}%
            丢帧总数: ${summary.totalDroppedFrames}
            检测到的问题: ${report.issues.size}个
            
            建议:
            ${summary.recommendations.joinToString("\n")}
        """.trimIndent()
        
        logMessage(reportText)
        updateStatus("性能报告已生成")
    }
    
    private fun getCurrentStreamUrl(): String {
        return testStreams[currentStreamIndex]
    }
    
    private fun updateUI() {
        runOnUiThread {
            startButton.isEnabled = !isPlaying
            stopButton.isEnabled = isPlaying
            changeModeButton.isEnabled = true
            generateReportButton.isEnabled = true
            
            // 更新状态文本
            val streamInfo = "当前流: ${getCurrentStreamUrl()}\n性能模式: $currentPerformanceMode"
            statusTextView.text = "状态: ${if (isPlaying) "播放中" else "已停止"}\n$streamInfo"
        }
    }
    
    private fun updateStatus(message: String) {
        runOnUiThread {
            statusTextView.text = message
        }
    }
    
    private fun updatePerformanceInfo(info: String) {
        runOnUiThread {
            performanceTextView.text = info
        }
    }
    
    private fun logMessage(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val logEntry = "[$timestamp] $message"
        
        logEntries.add(0, logEntry) // 添加到开头，最新的在最上面
        if (logEntries.size > maxLogEntries) {
            logEntries.removeAt(logEntries.size - 1)
        }
        
        runOnUiThread {
            logTextView.text = logEntries.joinToString("\n")
        }
        
        Log.d(TAG, message)
    }
    
    private fun startPerformanceUpdates() {
        // 每2秒更新一次性能信息
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (isPlaying) {
                    // 触发性能监控收集
                    performanceMonitor.collectFrameRate()
                    performanceMonitor.collectBufferLevel()
                    
                    // 继续下一次更新
                    handler.postDelayed(this, 2000)
                }
            }
        }, 2000)
    }
    
    private fun stopPerformanceUpdates() {
        handler.removeCallbacksAndMessages(null)
    }
    
    private fun generatePlaybackStats() {
        val duration = (System.currentTimeMillis() - startTime) / 1000f // 秒
        if (duration > 0) {
            val avgFrameRate = totalFrames / duration
            val stats = """
                播放统计:
                时长: ${"%.1f".format(duration)}秒
                平均帧率: ${"%.1f".format(avgFrameRate)} fps
                丢帧数: $totalDroppedFrames
                接收数据: ${totalBytesReceived / 1024} KB
            """.trimIndent()
            
            logMessage(stats)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // 停止播放
        stopPlayback()
        
        // 释放资源
        playerWrapper.release()
        performanceMonitor.stopMonitoring()
        errorRecoveryManager.release()
        
        handler.removeCallbacksAndMessages(null)
        
        logMessage("RTP测试Activity已销毁")
    }
}