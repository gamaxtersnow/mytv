package com.gamaxtersnow.mytv.epg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class EpgCacheManagerTest {
    @Test
    fun missingCacheReturnsNull() {
        val manager = EpgCacheManager(tempDir("missing"))

        assertNull(manager.load("source"))
    }

    @Test
    fun savedCacheLoadsBack() {
        val dir = tempDir("save-load")
        val manager = EpgCacheManager(dir) { 1_000L }

        manager.save("source", "xml".toByteArray(), "2026-04-28", "2026-04-29", savedAt = 1_000L)
        val cached = manager.load("source", maxAgeMs = 10_000L)

        assertNotNull(cached)
        assertEquals("xml", cached!!.raw.decodeToString())
        assertEquals("2026-04-28", cached.status.coveredStartDate)
        assertFalse(cached.status.isStale)
    }

    @Test
    fun loadMarksExpiredCacheAsStale() {
        val dir = tempDir("stale")
        val manager = EpgCacheManager(dir) { 100_000L }

        manager.save("source", "xml".toByteArray(), "2026-04-28", "2026-04-29", savedAt = 1_000L)
        val cached = manager.load("source", maxAgeMs = 10L)

        assertNotNull(cached)
        assertTrue(cached!!.status.isStale)
    }

    @Test
    fun cleanupRemovesOldFilesOnly() {
        val dir = tempDir("cleanup")
        val manager = EpgCacheManager(dir) { 500_000L }
        manager.save("old-source", "old".toByteArray(), "2026-04-20", "2026-04-21", savedAt = 10L)
        manager.save("new-source", "new".toByteArray(), "2026-04-28", "2026-04-29", savedAt = 500_000L)

        File(dir, "epg_cache/old-source.xmltv").setLastModified(10L)
        File(dir, "epg_cache/old-source.properties").setLastModified(10L)
        File(dir, "epg_cache/new-source.xmltv").setLastModified(500_000L)
        File(dir, "epg_cache/new-source.properties").setLastModified(500_000L)

        manager.cleanup(retentionMs = 1_000L)

        assertNull(manager.load("old-source"))
        assertEquals("new", manager.load("new-source")!!.raw.decodeToString())
    }

    private fun tempDir(name: String): File {
        return createTempDirectory("epg-cache-$name-").toFile().apply { deleteOnExit() }
    }
}
