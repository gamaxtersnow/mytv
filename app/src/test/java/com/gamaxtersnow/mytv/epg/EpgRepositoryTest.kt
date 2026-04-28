package com.gamaxtersnow.mytv.epg

import com.gamaxtersnow.mytv.TV
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class EpgRepositoryTest {
    @Test
    fun loadCacheMakesProgrammesAvailableBeforeRefresh() {
        val dir = tempDir("cache-first")
        val cacheManager = EpgCacheManager(dir) { 1_000L }
        cacheManager.save("51zmt_domestic", sampleXml.toByteArray(), "2026-04-28", "2026-04-29", 1_000L)

        val repository = EpgRepository(
            cacheManager = cacheManager,
            downloader = { error("network should not be used") }
        )

        assertTrue(repository.loadCache())
        assertEquals("朝闻天下", repository.programmesFor(cctv1()).first().title)
        assertTrue(repository.status.isAvailable)
    }

    @Test
    fun refreshReplacesIndexAfterSuccessfulDownload() {
        val repository = EpgRepository(
            cacheManager = EpgCacheManager(tempDir("refresh")) { 2_000L },
            downloader = { updatedXml.toByteArray() }
        )

        assertTrue(repository.refresh())
        assertEquals("新闻直播间", repository.programmesFor(cctv1()).first().title)
        assertEquals("51zmt_domestic", repository.status.sourceKey)
    }

    @Test
    fun refreshFailureKeepsPreviousIndexAndMarksStatusStale() {
        val dir = tempDir("failure")
        val cacheManager = EpgCacheManager(dir) { 1_000L }
        cacheManager.save("51zmt_domestic", sampleXml.toByteArray(), "2026-04-28", "2026-04-29", 1_000L)

        val repository = EpgRepository(
            cacheManager = cacheManager,
            downloader = { error("boom") }
        )

        assertTrue(repository.loadCache())
        assertFalse(repository.refresh())
        assertEquals("朝闻天下", repository.programmesFor(cctv1()).first().title)
        assertTrue(repository.status.isStale)
    }

    @Test
    fun normalizesSimpleChannelNameVariants() {
        val repository = EpgRepository(
            cacheManager = EpgCacheManager(tempDir("normalize")) { 1_000L },
            downloader = { sampleXml.toByteArray() }
        )

        repository.refresh()

        val tv = TV(
            id = 0,
            title = "CCTV1 综合",
            alias = "CCTV-1",
            videoUrl = listOf("http://example.com/live.m3u8")
        )

        assertEquals("朝闻天下", repository.programmesFor(tv).first().title)
    }

    private fun cctv1(): TV {
        return TV(
            id = 0,
            title = "CCTV-1 综合",
            alias = "CCTV1",
            videoUrl = listOf("http://example.com/live.m3u8")
        )
    }

    private fun tempDir(name: String): File {
        return createTempDirectory("epg-repo-$name-").toFile().apply { deleteOnExit() }
    }

    private val sampleXml = """
        <tv>
          <channel id="1">
            <display-name>CCTV-1</display-name>
            <display-name>CCTV1</display-name>
          </channel>
          <programme start="20260428080000 +0800" stop="20260428090000 +0800" channel="1">
            <title>朝闻天下</title>
          </programme>
          <programme start="20260428090000 +0800" stop="20260428100000 +0800" channel="1">
            <title>生活早参考</title>
          </programme>
        </tv>
    """.trimIndent()

    private val updatedXml = """
        <tv>
          <channel id="1">
            <display-name>CCTV-1</display-name>
          </channel>
          <programme start="20260428100000 +0800" stop="20260428110000 +0800" channel="1">
            <title>新闻直播间</title>
          </programme>
        </tv>
    """.trimIndent()
}
