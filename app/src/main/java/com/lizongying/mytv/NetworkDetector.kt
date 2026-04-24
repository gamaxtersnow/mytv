package com.lizongying.mytv

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.NetworkInterface

/**
 * 网络检测器
 * 检测组播流播放所需的网络条件，包括IGMP支持检测和网络质量评估
 */
object NetworkDetector {
    private const val TAG = "NetworkDetector"
    private const val NETWORK_CHECK_TIMEOUT_MS = 5000L
    private var multicastLock: WifiManager.MulticastLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null

    /**
     * 检测网络是否支持组播（IGMP）
     */
    fun isMulticastSupported(): Boolean {
        return try {
            // 检查网络接口的IGMP支持
            val interfaces = NetworkInterface.getNetworkInterfaces()
            var hasMulticastSupport = false

            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isUp && networkInterface.supportsMulticast() && !networkInterface.isLoopback) {
                    hasMulticastSupport = true
                    Log.d(TAG, "网络接口 ${networkInterface.name} 支持组播, 地址: ${networkInterface.interfaceAddresses}")
                    break
                }
            }

            if (!hasMulticastSupport) {
                Log.w(TAG, "未找到支持组播的网络接口")
            }

            hasMulticastSupport
        } catch (e: Exception) {
            Log.e(TAG, "检测组播支持失败: ${e.message}")
            false
        }
    }

    /**
     * 获取当前活动的网络接口名称，用于VLC绑定
     */
    fun getActiveNetworkInterfaceName(context: Context): String? {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val activeNetwork = connectivityManager?.activeNetwork ?: return null
            val linkProperties = connectivityManager.getLinkProperties(activeNetwork) ?: return null

            val interfaceName = linkProperties.interfaceName
            Log.d(TAG, "当前活动网络接口: $interfaceName")
            return interfaceName
        } catch (e: Exception) {
            Log.e(TAG, "获取活动网络接口失败: ${e.message}")
            null
        }
    }

    /**
     * 检测是否连接到正确的网络（组播流需要本地网络）
     */
    fun isLocalNetwork(context: Context): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            connectivityManager?.let { manager ->
                val activeNetwork = manager.activeNetwork
                val networkCapabilities = manager.getNetworkCapabilities(activeNetwork)

                if (networkCapabilities == null) {
                    Log.w(TAG, "没有活动的网络连接")
                    return false
                }

                // 检查是否为本地网络（Wi-Fi或以太网）
                val isLocal = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)

                if (!isLocal) {
                    Log.w(TAG, "不是本地网络连接，可能无法接收组播流")
                }

                isLocal
            } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "检测本地网络失败: ${e.message}")
            false
        }
    }

    /**
     * 检测网络是否适合播放组播流
     * 简化版本：只做本地检查，不做外部网络请求，避免后台IO干扰播放
     */
    suspend fun isSuitableForMulticast(context: Context): Boolean = withContext(Dispatchers.IO) {
        val isLocal = isLocalNetwork(context)
        if (!isLocal) {
            Log.w(TAG, "不是本地网络，可能无法接收组播流")
        }
        Log.d(TAG, "网络检测完成: isLocal=$isLocal")
        return@withContext true // 始终尝试播放，让播放器自己处理
    }

    /**
     * 获取组播锁，允许接收组播数据包
     * Android默认会过滤组播包，需要获取此锁才能正常接收
     * 同时获取WiFi锁和WakeLock，防止省电模式丢弃组播包
     */
    fun acquireMulticastLock(context: Context) {
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val activeNetwork = connectivityManager?.activeNetwork
            val networkCapabilities = connectivityManager?.getNetworkCapabilities(activeNetwork)

            // 以太网连接不需要组播锁
            if (networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true) {
                Log.d(TAG, "以太网连接，不需要组播锁")
                return
            }

            // Wi-Fi连接需要获取组播锁 + WiFi锁 + WakeLock
            if (networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

                // 1. 获取组播锁
                if (multicastLock == null) {
                    multicastLock = wifiManager?.createMulticastLock("MyTVMulticastLock")
                    multicastLock?.setReferenceCounted(true)
                }
                if (multicastLock?.isHeld != true) {
                    multicastLock?.acquire()
                    Log.d(TAG, "已获取Wi-Fi组播锁")
                }

                // 2. 获取WiFi锁 - 防止WiFi进入省电模式丢弃组播包
                if (wifiLock == null) {
                    @Suppress("DEPRECATION")
                    wifiLock = wifiManager?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "MyTVWifiLock")
                }
                if (wifiLock?.isHeld != true) {
                    wifiLock?.acquire()
                    Log.d(TAG, "已获取WiFi高性能锁，防止WiFi省电模式")
                }

                // 3. 获取WakeLock - 保持CPU活跃
                val powerManager = context.applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
                if (wakeLock == null) {
                    wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyTV:WakeLock")
                }
                if (wakeLock?.isHeld != true) {
                    wakeLock?.acquire(4 * 60 * 60 * 1000L) // 4小时超时，看电视场景避免中途释放
                    Log.d(TAG, "已获取WakeLock，保持CPU活跃")
                }

                Log.d(TAG, "网络优化完成: 组播锁+WiFi锁+WakeLock全部获取")
            } else {
                Log.w(TAG, "未知网络类型，无法获取组播锁")
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取组播锁失败: ${e.message}", e)
        }
    }

    /**
     * 释放所有锁
     */
    fun releaseMulticastLock() {
        try {
            // 释放组播锁
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
                Log.d(TAG, "已释放组播锁")
            }
            multicastLock = null

            // 释放WiFi锁
            if (wifiLock?.isHeld == true) {
                wifiLock?.release()
                Log.d(TAG, "已释放WiFi锁")
            }
            wifiLock = null

            // 释放WakeLock
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d(TAG, "已释放WakeLock")
            }
            wakeLock = null
        } catch (e: Exception) {
            Log.e(TAG, "释放锁失败: ${e.message}", e)
        }
    }
}