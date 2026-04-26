package com.gamaxtersnow.mytv

import android.content.Context
import android.util.Log
import com.gamaxtersnow.mytv.models.ProgramType

object TVList {

    private const val TAG = "TVList"

    var list: Map<String, List<TV>> = emptyMap()
        private set

    // 本地凤凰台固定基底（港澳台）
    private val localBase: List<TV> by lazy {
        listOf(
            TV(
                0,
                "凤凰卫视资讯台",
                "",
                listOf(),
                "港澳台",
                "http://c1.fengshows-cdn.com/a/2021_22/79dcc3a9da358a3.png",
                "7c96b084-60e1-40a9-89c5-682b994fb680",
                "",
                ProgramType.F,
                false,
                mustToken = false
            ),
            TV(
                0,
                "凤凰卫视中文台",
                "",
                listOf(),
                "港澳台",
                "http://c1.fengshows-cdn.com/a/2021_22/ede3d9e09be28e5.png",
                "f7f48462-9b13-485b-8101-7b54716411ec",
                "",
                ProgramType.F,
                false,
                mustToken = false
            ),
            TV(
                0,
                "凤凰卫视香港台",
                "",
                listOf(),
                "港澳台",
                "http://c1.fengshows-cdn.com/a/2021_23/325d941090bee17.png",
                "15e02d92-1698-416c-af2f-3e9a872b4d78",
                "",
                ProgramType.F,
                false,
                mustToken = false
            ),
        )
    }

    init {
        setup()
    }

    /**
     * 初始设置：仅加载本地凤凰台
     */
    private fun setup() {
        list = buildList(mapOf("港澳台" to localBase))
    }

    /**
     * 刷新频道列表：本地凤凰台 + 远程缓存
     * 远程条目与本地凤凰台同title时，丢弃远程条目
     */
    fun refresh(context: Context) {
        val remoteChannels = RemotePlaylistManager.loadCachedPlaylist(context)
        val localTitles = localBase.map { it.title }.toSet()

        // 过滤掉与本地凤凰台同名的远程频道
        val filteredRemote = remoteChannels.filter { it.title !in localTitles }

        // 按channel（group-title）分组
        val remoteGroups = filteredRemote.groupBy { it.channel.ifEmpty { "自定义" } }

        // 合并：港澳台优先，然后是远程分组
        val merged = mutableMapOf<String, List<TV>>()
        merged["港澳台"] = localBase
        merged.putAll(remoteGroups)

        list = buildList(merged)
        Log.i(TAG, "频道列表已刷新，共 ${list.size} 个分组，${list.values.sumOf { it.size }} 个频道")
    }

    /**
     * 为所有频道分配连续id，过滤mustToken=true的频道
     */
    private fun buildList(source: Map<String, List<TV>>): Map<String, List<TV>> {
        val result = mutableMapOf<String, List<TV>>()
        var id = 0
        source.forEach { (groupName, channels) ->
            val filtered = channels.filter { !it.mustToken }
            if (filtered.isNotEmpty()) {
                val withIds = filtered.map { tv ->
                    tv.id = id
                    id++
                    tv
                }
                result[groupName] = withIds
            }
        }
        return result
    }
}
