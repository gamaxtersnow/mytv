package com.lizongying.mytv

import android.util.Log

/**
 * 性能优化配置类
 * 提供通用的性能优化参数配置
 */
object PerformanceConfig {
    private const val TAG = "PerformanceConfig"

    /**
     * 性能监控配置
     */
    val DEFAULT_MONITORING_ENABLED = true
    val DEFAULT_SAMPLING_INTERVAL_MS = 1000L
    val DEFAULT_HISTORY_SIZE = 100

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
