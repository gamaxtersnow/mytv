package com.lizongying.mytv

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * 错误恢复管理器
 * 负责处理播放错误、网络中断和自动恢复
 */
class ErrorRecoveryManager(private val context: Context) {
    private val TAG = "ErrorRecoveryManager"
    
    // 恢复策略配置
    data class RecoveryConfig(
        val maxRetryCount: Int = 3,
        val retryDelayMs: Long = 2000,
        val networkCheckEnabled: Boolean = true,
        val adaptiveBitrateEnabled: Boolean = true,
        val fallbackToLowerQuality: Boolean = true,
        val enableLogging: Boolean = true
    )
    
    // 错误类型
    enum class ErrorType {
        NETWORK_ERROR,          // 网络错误
        DECODER_ERROR,          // 解码器错误
        BUFFERING_TIMEOUT,      // 缓冲超时
        CONNECTION_LOST,        // 连接丢失
        PLAYER_ERROR,           // 播放器错误
        UNKNOWN_ERROR           // 未知错误
    }
    
    // 错误信息
    data class ErrorInfo(
        val type: ErrorType,
        val message: String,
        val timestamp: Long = System.currentTimeMillis(),
        val url: String? = null,
        val errorCode: Int? = null
    )
    
    // 恢复状态
    data class RecoveryStatus(
        val isRecovering: Boolean = false,
        val retryCount: Int = 0,
        val lastError: ErrorInfo? = null,
        val lastRecoveryTime: Long = 0,
        val currentStrategy: RecoveryStrategy? = null
    )
    
    // 恢复策略
    sealed class RecoveryStrategy {
        object RetrySameUrl : RecoveryStrategy()
        data class SwitchToBackupUrl(val backupUrl: String) : RecoveryStrategy()
        data class ReduceQuality(val qualityLevel: Int) : RecoveryStrategy()
        object WaitForNetwork : RecoveryStrategy()
        object RestartPlayer : RecoveryStrategy()
    }
    
    // 事件监听器
    interface RecoveryEventListener {
        fun onErrorDetected(error: ErrorInfo)
        fun onRecoveryStarted(strategy: RecoveryStrategy)
        fun onRecoveryCompleted(success: Boolean, message: String)
        fun onNetworkStatusChanged(available: Boolean, networkType: String)
    }
    
    private var config = RecoveryConfig()
    private var status = RecoveryStatus()
    private var eventListener: RecoveryEventListener? = null
    private val handler = Handler(Looper.getMainLooper())
    private var networkMonitor: NetworkMonitor? = null
    
    /**
     * 初始化错误恢复管理器
     */
    fun initialize(config: RecoveryConfig? = null) {
        this.config = config ?: RecoveryConfig()
        
        // 初始化网络监控
        if (this.config.networkCheckEnabled) {
            networkMonitor = NetworkMonitor(context).apply {
                setNetworkChangeListener { available, type ->
                    onNetworkStatusChanged(available, type)
                }
                startMonitoring()
            }
        }
        
        Log.d(TAG, "错误恢复管理器初始化完成，配置: $config")
    }
    
    /**
     * 设置事件监听器
     */
    fun setEventListener(listener: RecoveryEventListener) {
        this.eventListener = listener
    }
    
    /**
     * 处理播放错误
     */
    fun handleError(error: ErrorInfo): RecoveryStrategy? {
        if (status.isRecovering) {
            Log.w(TAG, "已经在恢复中，忽略新错误")
            return null
        }
        
        Log.e(TAG, "处理错误: ${error.type} - ${error.message}")
        
        // 更新状态
        status = status.copy(
            lastError = error,
            retryCount = status.retryCount + 1
        )
        
        // 通知监听器
        eventListener?.onErrorDetected(error)
        
        // 检查是否超过最大重试次数
        if (status.retryCount > config.maxRetryCount) {
            Log.w(TAG, "已达到最大重试次数(${config.maxRetryCount})，停止恢复")
            eventListener?.onRecoveryCompleted(false, "达到最大重试次数")
            return null
        }
        
        // 根据错误类型选择恢复策略
        val strategy = selectRecoveryStrategy(error)
        
        if (strategy != null) {
            // 开始恢复
            status = status.copy(
                isRecovering = true,
                currentStrategy = strategy,
                lastRecoveryTime = System.currentTimeMillis()
            )
            
            Log.d(TAG, "选择恢复策略: $strategy")
            eventListener?.onRecoveryStarted(strategy)
            
            // 执行恢复策略
            executeRecoveryStrategy(strategy, error)
        }
        
        return strategy
    }
    
    /**
     * 选择恢复策略
     */
    private fun selectRecoveryStrategy(error: ErrorInfo): RecoveryStrategy? {
        return when (error.type) {
            ErrorType.NETWORK_ERROR -> {
                // 检查网络状态
                if (!isNetworkAvailable()) {
                    RecoveryStrategy.WaitForNetwork
                } else if (status.retryCount < 2) {
                    RecoveryStrategy.RetrySameUrl
                } else {
                    RecoveryStrategy.RestartPlayer
                }
            }
            
            ErrorType.CONNECTION_LOST -> {
                if (hasBackupUrl(error.url)) {
                    RecoveryStrategy.SwitchToBackupUrl(getBackupUrl(error.url!!))
                } else {
                    RecoveryStrategy.RetrySameUrl
                }
            }
            
            ErrorType.BUFFERING_TIMEOUT -> {
                if (config.adaptiveBitrateEnabled && config.fallbackToLowerQuality) {
                    RecoveryStrategy.ReduceQuality(calculateReducedQualityLevel())
                } else {
                    RecoveryStrategy.RetrySameUrl
                }
            }
            
            ErrorType.DECODER_ERROR -> {
                RecoveryStrategy.RestartPlayer
            }

            ErrorType.PLAYER_ERROR -> {
                RecoveryStrategy.RestartPlayer
            }

            ErrorType.UNKNOWN_ERROR -> {
                if (status.retryCount == 1) {
                    RecoveryStrategy.RetrySameUrl
                } else {
                    RecoveryStrategy.RestartPlayer
                }
            }
        }
    }
    
    /**
     * 执行恢复策略
     */
    @Suppress("UNUSED_PARAMETER")
    private fun executeRecoveryStrategy(strategy: RecoveryStrategy, _error: ErrorInfo) {
        when (strategy) {
            is RecoveryStrategy.RetrySameUrl -> {
                // 延迟后重试
                handler.postDelayed({
                    completeRecovery(true, "重试相同URL")
                }, config.retryDelayMs)
            }
            
            is RecoveryStrategy.SwitchToBackupUrl -> {
                // 立即切换到备用URL
                handler.post {
                    completeRecovery(true, "切换到备用URL: ${strategy.backupUrl}")
                }
            }
            
            is RecoveryStrategy.ReduceQuality -> {
                // 降低质量级别
                handler.post {
                    completeRecovery(true, "降低质量到级别 ${strategy.qualityLevel}")
                }
            }
            
            RecoveryStrategy.WaitForNetwork -> {
                // 等待网络恢复
                Log.d(TAG, "等待网络恢复...")
                // 网络监控会处理恢复
            }
            
            RecoveryStrategy.RestartPlayer -> {
                // 延迟后重启播放器
                handler.postDelayed({
                    completeRecovery(true, "重启播放器")
                }, config.retryDelayMs * 2)
            }
        }
    }
    
    /**
     * 完成恢复过程
     */
    fun completeRecovery(success: Boolean, message: String) {
        Log.d(TAG, "恢复完成: $success - $message")
        
        status = status.copy(
            isRecovering = false,
            currentStrategy = null
        )
        
        if (success) {
            // 重置重试计数
            status = status.copy(retryCount = 0)
        }
        
        eventListener?.onRecoveryCompleted(success, message)
    }
    
    /**
     * 重置恢复状态
     */
    fun reset() {
        status = RecoveryStatus()
        Log.d(TAG, "恢复状态已重置")
    }
    
    /**
     * 获取当前恢复状态
     */
    fun getStatus(): RecoveryStatus {
        return status
    }
    
    /**
     * 检查网络是否可用
     */
    fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
               capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
               capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }
    
    /**
     * 网络状态变化回调
     */
    private fun onNetworkStatusChanged(available: Boolean, networkType: String) {
        Log.d(TAG, "网络状态变化: 可用=$available, 类型=$networkType")
        
        eventListener?.onNetworkStatusChanged(available, networkType)
        
        // 如果网络恢复且正在等待网络，则尝试恢复
        if (available && status.currentStrategy == RecoveryStrategy.WaitForNetwork) {
            Log.d(TAG, "网络恢复，尝试重新连接")
            completeRecovery(true, "网络已恢复")
        }
    }
    
    /**
     * 检查是否有备用URL
     */
    @Suppress("UNUSED_PARAMETER")
    private fun hasBackupUrl(_url: String?): Boolean {
        // 这里可以实现备用URL逻辑
        // 例如从配置文件中读取备用流地址
        return false
    }
    
    /**
     * 获取备用URL
     */
    private fun getBackupUrl(originalUrl: String): String {
        // 这里可以实现备用URL生成逻辑
        // 例如将主服务器地址替换为备用服务器
        return originalUrl.replace("primary", "backup")
    }
    
    /**
     * 计算降低后的质量级别
     */
    private fun calculateReducedQualityLevel(): Int {
        // 这里可以实现自适应码率逻辑
        // 根据当前错误次数和网络状况计算
        return when (status.retryCount) {
            1 -> 2  // 中等质量
            2 -> 3  // 低质量
            else -> 4  // 最低质量
        }
    }
    
    /**
     * 创建错误信息
     */
    fun createErrorInfo(type: ErrorType, message: String, url: String? = null): ErrorInfo {
        return ErrorInfo(type, message, System.currentTimeMillis(), url)
    }
    
    /**
     * 释放资源
     */
    fun release() {
        networkMonitor?.stopMonitoring()
        networkMonitor = null
        handler.removeCallbacksAndMessages(null)
        Log.d(TAG, "错误恢复管理器资源已释放")
    }
}

/**
 * 网络监控器
 */
private class NetworkMonitor(private val context: Context) {
    private val TAG = "NetworkMonitor"
    private var networkChangeListener: ((Boolean, String) -> Unit)? = null
    private var isMonitoring = false
    
    fun setNetworkChangeListener(listener: (Boolean, String) -> Unit) {
        this.networkChangeListener = listener
    }
    
    fun startMonitoring() {
        if (isMonitoring) return
        
        isMonitoring = true
        Log.d(TAG, "开始网络监控")
        
        // 这里可以注册网络状态广播接收器
        // 简化实现：定期检查网络状态
        // 在实际应用中，应该使用ConnectivityManager.NetworkCallback
    }
    
    fun stopMonitoring() {
        isMonitoring = false
        Log.d(TAG, "停止网络监控")
    }
    
    private fun checkNetworkStatus() {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        
        val isAvailable = capabilities != null
        val networkType = when {
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "WIFI"
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "CELLULAR"
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "ETHERNET"
            else -> "UNKNOWN"
        }
        
        networkChangeListener?.invoke(isAvailable, networkType)
    }
}