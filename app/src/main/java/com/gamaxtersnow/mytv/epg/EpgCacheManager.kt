package com.gamaxtersnow.mytv.epg

import java.io.File
import java.util.Properties

data class CachedEpg(
    val raw: ByteArray,
    val status: EpgStatus
)

class EpgCacheManager(
    rootDir: File,
    private val now: () -> Long = { System.currentTimeMillis() }
) {
    private val cacheDir = File(rootDir, CACHE_DIR_NAME)

    fun save(
        sourceKey: String,
        raw: ByteArray,
        coveredStartDate: String,
        coveredEndDate: String,
        savedAt: Long = now()
    ): CachedEpg {
        cacheDir.mkdirs()
        val safeKey = safeSourceKey(sourceKey)
        val dataFile = File(cacheDir, "$safeKey.xmltv")
        val metaFile = File(cacheDir, "$safeKey.properties")

        dataFile.writeBytes(raw)
        Properties().apply {
            setProperty(KEY_SOURCE, sourceKey)
            setProperty(KEY_SAVED_AT, savedAt.toString())
            setProperty(KEY_START_DATE, coveredStartDate)
            setProperty(KEY_END_DATE, coveredEndDate)
        }.store(metaFile.outputStream(), "EPG cache metadata")

        return CachedEpg(
            raw = raw,
            status = EpgStatus(
                sourceKey = sourceKey,
                lastSuccessTime = savedAt,
                coveredStartDate = coveredStartDate,
                coveredEndDate = coveredEndDate,
                isAvailable = true,
                isStale = false,
                message = "缓存可用"
            )
        )
    }

    fun load(sourceKey: String, maxAgeMs: Long = DEFAULT_MAX_AGE_MS): CachedEpg? {
        val safeKey = safeSourceKey(sourceKey)
        val dataFile = File(cacheDir, "$safeKey.xmltv")
        val metaFile = File(cacheDir, "$safeKey.properties")
        if (!dataFile.exists() || !metaFile.exists()) {
            return null
        }

        val props = Properties().apply {
            metaFile.inputStream().use { load(it) }
        }
        val savedAt = props.getProperty(KEY_SAVED_AT)?.toLongOrNull() ?: 0L
        val isStale = now() - savedAt > maxAgeMs

        return CachedEpg(
            raw = dataFile.readBytes(),
            status = EpgStatus(
                sourceKey = props.getProperty(KEY_SOURCE, sourceKey),
                lastSuccessTime = savedAt,
                coveredStartDate = props.getProperty(KEY_START_DATE, ""),
                coveredEndDate = props.getProperty(KEY_END_DATE, ""),
                isAvailable = true,
                isStale = isStale,
                message = if (isStale) "缓存已过期" else "缓存可用"
            )
        )
    }

    fun cleanup(retentionMs: Long = DEFAULT_RETENTION_MS) {
        if (!cacheDir.exists()) {
            return
        }
        val cutoff = now() - retentionMs
        cacheDir.listFiles().orEmpty().forEach { file ->
            if (file.lastModified() < cutoff) {
                file.delete()
            }
        }
    }

    companion object {
        private const val CACHE_DIR_NAME = "epg_cache"
        private const val KEY_SOURCE = "source"
        private const val KEY_SAVED_AT = "savedAt"
        private const val KEY_START_DATE = "coveredStartDate"
        private const val KEY_END_DATE = "coveredEndDate"
        private const val DEFAULT_MAX_AGE_MS = 72 * 60 * 60 * 1000L
        private const val DEFAULT_RETENTION_MS = 4 * 24 * 60 * 60 * 1000L

        private fun safeSourceKey(sourceKey: String): String {
            return sourceKey.replace(Regex("[^A-Za-z0-9._-]"), "_")
        }
    }
}
