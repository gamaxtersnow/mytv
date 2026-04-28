package com.gamaxtersnow.mytv.epg

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.zip.GZIPInputStream
import javax.xml.parsers.DocumentBuilderFactory

data class XmlTvChannel(
    val id: String,
    val displayNames: List<String>
)

data class XmlTvProgramme(
    val channelId: String,
    val title: String,
    val startTime: Long,
    val stopTime: Long
)

data class XmlTvData(
    val channels: List<XmlTvChannel>,
    val programmes: List<XmlTvProgramme>
)

class XmlTvParser {
    fun parse(raw: ByteArray): XmlTvData {
        val input = ByteArrayInputStream(decompressIfNeeded(raw))
        val document = DocumentBuilderFactory.newInstance().apply {
            isIgnoringComments = true
            isCoalescing = true
            isNamespaceAware = false
        }.newDocumentBuilder().parse(input)

        val channelNodes = document.getElementsByTagName("channel")
        val channels = buildList {
            for (i in 0 until channelNodes.length) {
                val element = channelNodes.item(i) as? Element ?: continue
                val id = element.getAttribute("id").trim()
                if (id.isBlank()) {
                    continue
                }
                val displayNameNodes = element.getElementsByTagName("display-name")
                val displayNames = buildList {
                    for (j in 0 until displayNameNodes.length) {
                        val name = displayNameNodes.item(j)?.textContent?.trim().orEmpty()
                        if (name.isNotBlank()) {
                            add(name)
                        }
                    }
                }
                add(XmlTvChannel(id = id, displayNames = displayNames))
            }
        }

        val programmeNodes = document.getElementsByTagName("programme")
        val programmes = buildList {
            for (i in 0 until programmeNodes.length) {
                val element = programmeNodes.item(i) as? Element ?: continue
                val channelId = element.getAttribute("channel").trim()
                val start = parseXmlTvTime(element.getAttribute("start"))
                val stop = parseXmlTvTime(element.getAttribute("stop"))
                val title = element.getElementsByTagName("title")
                    .item(0)
                    ?.textContent
                    ?.trim()
                    .orEmpty()

                if (channelId.isBlank() || title.isBlank() || start == null || stop == null || stop <= start) {
                    continue
                }

                add(
                    XmlTvProgramme(
                        channelId = channelId,
                        title = title,
                        startTime = start,
                        stopTime = stop
                    )
                )
            }
        }

        return XmlTvData(
            channels = channels,
            programmes = programmes.sortedWith(compareBy({ it.channelId }, { it.startTime }))
        )
    }

    private fun decompressIfNeeded(raw: ByteArray): ByteArray {
        val isGzip = raw.size >= 2 &&
            raw[0].toInt() == GZIP_MAGIC_1 &&
            raw[1].toInt() == GZIP_MAGIC_2
        if (!isGzip) {
            return raw
        }
        return GZIPInputStream(ByteArrayInputStream(raw)).use { it.readBytes() }
    }

    private fun parseXmlTvTime(value: String): Long? {
        val trimmed = value.trim()
        if (trimmed.length < 14) {
            return null
        }
        return runCatching {
            XMLTV_TIME_FORMAT.parse(trimmed)?.time?.div(1000)
        }.getOrNull()
    }

    companion object {
        private const val GZIP_MAGIC_1 = 0x1f
        private const val GZIP_MAGIC_2 = -0x75

        private val XMLTV_TIME_FORMAT = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
}
