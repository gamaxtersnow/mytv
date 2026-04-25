package com.lizongying.mytv

import android.content.Context
import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 远程M3U8播放列表管理器
 * 负责拉取、解析、缓存远程M3U8频道列表
 */
object RemotePlaylistManager {

    private const val TAG = "RemotePlaylistManager"
    private const val CACHE_FILE_NAME = "remote_playlist.json"
    private const val DEFAULT_REMOTE_URL = "http://nas.bookinger.top:8000/iptv.m3u"
    private const val CONNECT_TIMEOUT_MS = 15000
    private const val READ_TIMEOUT_MS = 30000

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * 从远程URL拉取并解析M3U8播放列表
     * @return 解析后的频道列表，失败返回null
     */
    fun fetchRemotePlaylist(url: String): List<TV>? {
        return try {
            Log.i(TAG, "开始拉取远程M3U8: $url")
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("Accept", "*/*")
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val content = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()
                val channels = M3UUtils.parseM3U8Content(content)
                Log.i(TAG, "拉取成功，解析到 ${channels.size} 个频道")
                channels
            } else {
                Log.w(TAG, "拉取失败，HTTP状态码: $responseCode")
                connection.disconnect()
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "拉取远程M3U8异常: ${e.message}", e)
            null
        }
    }

    /**
     * 将频道列表序列化后保存到应用私有文件
     */
    fun saveCachedPlaylist(context: Context, channels: List<TV>) {
        try {
            val file = File(context.filesDir, CACHE_FILE_NAME)
            val serialized = json.encodeToString(channels)
            file.writeText(serialized)
            Log.i(TAG, "缓存已保存: ${channels.size} 个频道")
        } catch (e: Exception) {
            Log.e(TAG, "保存缓存失败: ${e.message}", e)
        }
    }

    /**
     * 从应用私有文件加载缓存的频道列表
     * @return 缓存的频道列表，文件不存在或解析失败返回空列表
     */
    fun loadCachedPlaylist(context: Context): List<TV> {
        return try {
            val file = File(context.filesDir, CACHE_FILE_NAME)
            if (!file.exists()) {
                Log.d(TAG, "缓存文件不存在")
                return emptyList()
            }
            val content = file.readText()
            val channels = json.decodeFromString<List<TV>>(content)
            Log.i(TAG, "缓存已加载: ${channels.size} 个频道")
            channels
        } catch (e: Exception) {
            Log.e(TAG, "加载缓存失败: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * 判断是否应该自动更新
     * 基于上次成功更新时间和用户设置的更新间隔
     */
    fun shouldAutoUpdate(): Boolean {
        val interval = SP.updateInterval
        if (interval <= 0) {
            Log.d(TAG, "自动更新已禁用")
            return false
        }
        val lastUpdate = SP.lastUpdateTime
        val now = System.currentTimeMillis()
        val shouldUpdate = now - lastUpdate >= interval
        Log.d(TAG, "shouldAutoUpdate: last=$lastUpdate, interval=$interval, now=$now, result=$shouldUpdate")
        return shouldUpdate
    }

    /**
     * 检查并执行更新
     * @param context Context
     * @param force 是否强制更新（忽略时间间隔）
     * @return 更新是否成功
     */
    fun checkAndUpdate(context: Context, force: Boolean = false): Boolean {
        val url = SP.remoteUrl
        if (url.isEmpty()) {
            Log.d(TAG, "远程URL为空，跳过更新")
            return false
        }

        if (!force && !shouldAutoUpdate()) {
            Log.d(TAG, "未达到更新间隔，跳过自动更新")
            return true // 不是失败，只是不需要更新
        }

        val channels = fetchRemotePlaylist(url)
        return if (channels != null) {
            saveCachedPlaylist(context, channels)
            if (loadCachedPlaylist(context).isNotEmpty()) {
                SP.lastUpdateTime = System.currentTimeMillis()
                Log.i(TAG, "更新成功: ${channels.size} 个频道")
                true
            } else {
                Log.w(TAG, "缓存保存后验证失败")
                false
            }
        } else {
            Log.w(TAG, "更新失败，保留上次缓存")
            false
        }
    }

    /**
     * 获取默认远程M3U8 URL
     */
    fun getDefaultUrl(): String = DEFAULT_REMOTE_URL

    /**
     * 获取缓存文件中的频道数量（用于UI显示）
     */
    fun getCachedChannelCount(context: Context): Int {
        return loadCachedPlaylist(context).size
    }
}