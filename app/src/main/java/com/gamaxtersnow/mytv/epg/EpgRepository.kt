package com.gamaxtersnow.mytv.epg

import android.content.Context
import com.gamaxtersnow.mytv.TV
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EpgRepository(
    private val cacheManager: EpgCacheManager,
    private val parser: XmlTvParser = XmlTvParser(),
    private val downloader: () -> ByteArray
) {
    private var data: XmlTvData = XmlTvData(emptyList(), emptyList())
    private var programmesByChannelId: Map<String, List<XmlTvProgramme>> = emptyMap()
    private var channelIdByNormalizedName: Map<String, String> = emptyMap()

    var status: EpgStatus = EpgStatus(message = "暂无节目单数据")
        private set

    fun loadCache(sourceKey: String = DEFAULT_SOURCE_KEY): Boolean {
        val cached = cacheManager.load(sourceKey) ?: run {
            status = EpgStatus(sourceKey = sourceKey, message = "暂无节目单缓存")
            return false
        }

        return loadCachedData(cached)
    }

    fun refresh(sourceKey: String = DEFAULT_SOURCE_KEY): Boolean {
        return runCatching {
            val raw = downloader()
            val parsed = parser.parse(raw)
            val range = dateRange(parsed.programmes)
            val cached = cacheManager.save(
                sourceKey = sourceKey,
                raw = raw,
                coveredStartDate = range.first,
                coveredEndDate = range.second
            )
            rebuildIndex(parsed)
            status = cached.status.copy(message = "节目单已更新")
            true
        }.getOrElse { error ->
            val previous = status
            status = previous.copy(
                isAvailable = previous.isAvailable,
                isStale = true,
                message = "节目单更新失败: ${error.message ?: "未知错误"}"
            )
            false
        }
    }

    fun programmesFor(tv: TV): List<XmlTvProgramme> {
        val directId = tv.pid.takeIf { it.isNotBlank() }
        val matchedId = directId
            ?: channelIdByNormalizedName[normalizeChannelName(tv.title)]
            ?: channelIdByNormalizedName[normalizeChannelName(tv.alias)]
        return matchedId?.let { programmesByChannelId[it] }.orEmpty()
    }

    private fun loadCachedData(cached: CachedEpg): Boolean {
        return runCatching {
            val parsed = parser.parse(cached.raw)
            rebuildIndex(parsed)
            status = cached.status
            true
        }.getOrElse { error ->
            status = cached.status.copy(
                isAvailable = false,
                isStale = true,
                message = "节目单缓存解析失败: ${error.message ?: "未知错误"}"
            )
            false
        }
    }

    private fun rebuildIndex(parsed: XmlTvData) {
        data = parsed
        programmesByChannelId = parsed.programmes.groupBy { it.channelId }
        channelIdByNormalizedName = buildMap {
            parsed.channels.forEach { channel ->
                put(normalizeChannelName(channel.id), channel.id)
                channel.displayNames.forEach { name ->
                    put(normalizeChannelName(name), channel.id)
                }
            }
        }
    }

    private fun dateRange(programmes: List<XmlTvProgramme>): Pair<String, String> {
        if (programmes.isEmpty()) {
            val today = DATE_FORMAT.format(Date())
            return today to today
        }
        val start = programmes.minOf { it.startTime } * 1000
        val end = programmes.maxOf { it.stopTime } * 1000
        return DATE_FORMAT.format(Date(start)) to DATE_FORMAT.format(Date(end))
    }

    companion object {
        const val DEFAULT_SOURCE_KEY = "51zmt_domestic"
        const val DEFAULT_SOURCE_URL = "http://epg.51zmt.top:8000/e.xml.gz"

        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        fun create(context: Context): EpgRepository {
            return EpgRepository(
                cacheManager = EpgCacheManager(context.filesDir),
                downloader = { download(DEFAULT_SOURCE_URL) }
            )
        }

        fun normalizeChannelName(name: String): String {
            return name
                .lowercase(Locale.ROOT)
                .replace("综合", "")
                .replace("频道", "")
                .replace(Regex("[\\s_\\-·:：]+"), "")
                .trim()
        }

        private fun download(url: String): ByteArray {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 20_000
            return connection.inputStream.use { it.readBytes() }.also {
                connection.disconnect()
            }
        }
    }
}
